package net.minecraft.client.yiz.tool;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 实体强制移除工具 — 多条绕过路径，按优先级依次尝试。
 *
 * <p>绕过路径：</p>
 * <ol>
 *   <li><b>Mixin forceRemoveEntity</b> — 通过 ServerLevel Mixin 直接操作
 *       内部实体存储（EntityLookup + ChunkSource），完全不走 remove() 管道</li>
 *   <li><b>MethodHandle Entity.remove()</b> — IMPL_LOOKUP + unreflectSpecial
 *       锁定基类实现，跳过虚方法分发表</li>
 *   <li><b>Unsafe + ServerLevel</b> — 设 removed=true + chunkSource.removeEntity</li>
 * </ol>
 */
@SuppressWarnings("removal")
public final class EntityRemovalUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityRemoval");
    private static final Unsafe U;
    private static final MethodHandle ENTITY_REMOVE_BASE;
    private static final long REMOVED_OFFSET;
    private static final boolean AVAILABLE;

    static {
        Unsafe u = null;
        MethodHandle handle = null;
        long removedOff = -1;
        boolean ok = false;

        try {
            u = getUnsafe();

            // 路径 2: MethodHandle 直调 Entity.remove()
            try {
                Method removeMethod = Entity.class.getDeclaredMethod(
                        "remove", Entity.RemovalReason.class);
                removeMethod.setAccessible(true);
                Field implLookupField = MethodHandles.Lookup.class
                        .getDeclaredField("IMPL_LOOKUP");
                long implLookupOffset = u.staticFieldOffset(implLookupField);
                MethodHandles.Lookup trusted = (MethodHandles.Lookup)
                        u.getObject(u.staticFieldBase(implLookupField), implLookupOffset);
                handle = trusted.unreflectSpecial(removeMethod, Entity.class);
            } catch (Exception e) {
                LOGGER.warn("[EntityRemovalUtil] MethodHandle unavailable: {}", e.getMessage());
            }

            // 路径 3: Entity.removed 字段偏移（兜底）
            for (Field f : Entity.class.getDeclaredFields()) {
                if (f.getType() == boolean.class
                        && (f.getName().contains("remov") || f.getName().contains("Remov"))) {
                    removedOff = u.objectFieldOffset(f);
                    break;
                }
            }

            ok = u != null;
            LOGGER.info("[EntityRemovalUtil] Ready: handle={} removedOff=0x{}",
                    handle != null, Long.toHexString(Math.max(0, removedOff)));
        } catch (Exception e) {
            LOGGER.error("[EntityRemovalUtil] Init failed: {}", e.getMessage());
        }

        U = u;
        ENTITY_REMOVE_BASE = handle;
        REMOVED_OFFSET = removedOff;
        AVAILABLE = ok;
    }

    private EntityRemovalUtil() {}

    /** 强制移除实体。成功返回 true。 */
    public static boolean forceRemove(Entity entity) {
        if (entity == null || entity.isRemoved()) return false;

        // 路径 1: Mixin 强制移除（不经过 remove() 管道）
        if (tryMixinForceRemove(entity)) return true;

        // 路径 2: MethodHandle 直调 Entity.remove()（绕过子类 override）
        if (ENTITY_REMOVE_BASE != null) {
            try {
                ENTITY_REMOVE_BASE.invoke(entity, Entity.RemovalReason.DISCARDED);
                return true;
            } catch (Throwable e) {
                LOGGER.trace("[EntityRemovalUtil] MethodHandle: {}", e.getMessage());
            }
        }

        // 路径 3: Unsafe + ServerLevel 反注册
        if (U != null && REMOVED_OFFSET >= 0) {
            try {
                if (entity.level() instanceof ServerLevel sl) {
                    sl.getChunkSource().removeEntity(entity);
                }
                U.putBoolean(entity, REMOVED_OFFSET, true);
                return true;
            } catch (Exception e) {
                LOGGER.trace("[EntityRemovalUtil] Unsafe: {}", e.getMessage());
            }
        }

        // 最后回退：直接调 discard（可能被拦截，但总比什么都不做强）
        try {
            entity.discard();
            return true;
        } catch (Exception e) {
            LOGGER.error("[EntityRemovalUtil] All paths failed for {}", entity);
            return false;
        }
    }

    /** 路径 1: 通过 ServerLevel Mixin 的 forceRemoveEntity 方法移除 */
    private static boolean tryMixinForceRemove(Entity entity) {
        try {
            if (entity.level() instanceof ServerLevel sl) {
                Method m = sl.getClass().getMethod("yizmodqzk$forceRemoveEntity", Entity.class);
                m.invoke(sl, entity);
                return true;
            }
        } catch (NoSuchMethodException e) {
            // Mixin 未加载，静默跳过
        } catch (Exception e) {
            LOGGER.trace("[EntityRemovalUtil] Mixin path: {}", e.getMessage());
        }
        return false;
    }

    private static Unsafe getUnsafe() {
        try {
            Constructor<Unsafe> c = Unsafe.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e1) {
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (Unsafe) f.get(null);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot get Unsafe", e2);
            }
        }
    }
}
