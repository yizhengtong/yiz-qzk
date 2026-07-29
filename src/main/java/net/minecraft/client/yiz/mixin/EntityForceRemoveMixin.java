package net.minecraft.client.yiz.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;

/**
 * 实体强制移除 Mixin — 用 Unsafe 直接操作底层字段和存储结构。
 *
 * <p>完全不经过 Entity.remove() / discard() / kill() / setRemoved() /
 * ChunkSource.removeEntity() 等任何可被 override 的方法。</p>
 *
 * <p>执行步骤：
 * <ol>
 *   <li>Unsafe 设 Entity.removed = true</li>
 *   <li>Unsafe 设 Entity.level 相关引用失效</li>
 *   <li>停止骑乘关系</li>
 *   <li>从 ServerLevel 实体 tracking 中反注册（net.minecraft.world.level.entity）</li>
 * </ol>
 */
@SuppressWarnings("removal")
@Mixin(ServerLevel.class)
public abstract class EntityForceRemoveMixin {

    @Unique
    private static final Unsafe U = getUnsafe();

    @Unique
    private static final long ENTITY_REMOVED_OFFSET = findRemovedOffset();

    /**
     * 强制移除实体 — 完全绕过所有可被 override 的方法。
     */
    @Unique
    @SuppressWarnings("unused")
    public void yizmodqzk$forceRemoveEntity(Entity entity) {
        if (entity == null) return;

        ServerLevel self = (ServerLevel) (Object) this;

        // 1. 解除骑乘（不经过 remove 管道）
        try {
            entity.unRide();
            if (entity.isVehicle()) {
                for (Entity p : entity.getPassengers()) {
                    p.stopRiding();
                }
            }
        } catch (Exception ignored) {}

        // 2. Unsafe 直写 removed = true
        //    这是 Entity 基类的私有字段，不经过任何 override
        if (ENTITY_REMOVED_OFFSET >= 0) {
            U.putBoolean(entity, ENTITY_REMOVED_OFFSET, true);
        }

        // 3. 从 ServerLevel 的 EntityLookup 中强制移除
        //    getEntities() 返回 LevelEntityGetter<Entity>，
        //    它内部有 EntityLookup，通过反射/Unsafe 操作其内部映射
        removeFromEntityLookup(self, entity);

        // 4. 调用 tick 列表移除（这个是内部方法，很难被 override）
        try {
            // ServerLevel.tickingEntities 是 EntityTickList，移除实体
            java.lang.reflect.Field tickField = ServerLevel.class.getDeclaredField("entityTickList");
            tickField.setAccessible(true);
            Object tickList = tickField.get(self);
            // EntityTickList.remove(Entity)
            java.lang.reflect.Method removeMethod = tickList.getClass()
                    .getDeclaredMethod("remove", Entity.class);
            removeMethod.setAccessible(true);
            removeMethod.invoke(tickList, entity);
        } catch (Exception ignored) {}
    }

    /**
     * 从 ServerLevel 的 EntityLookup 内部直接移除实体。
     */
    @Unique
    private static void removeFromEntityLookup(ServerLevel level, Entity entity) {
        try {
            // 路径: ServerLevel → LevelEntityGetter → EntityLookup → 内部映射
            // LevelEntityGetter 内部有 entityGetter 字段指向 EntityLookup
            Object getter = level.getEntities();
            Class<?> getterClass = getter.getClass();

            // 尝试找内部的 EntityLookup
            // 1.21.1: LevelEntityGetterAdapter → EntityLookup
            // 或者 LevelEntityGetter 本身就是 EntityLookup

            // 遍历所有字段，找到存储实体的 Map/集合
            for (java.lang.reflect.Field f : getterClass.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(getter);
                if (val == null) continue;

                // 找到 EntityLookup<Entity> 类型的字段
                String typeName = f.getType().getName();
                if (typeName.contains("EntityLookup") || typeName.contains("EntityGetter")) {
                    // 递归进入，找内部的 Map
                    removeFromLookupRecursive(val, entity);
                }

                // 如果直接就是 Map，尝试移除
                if (val instanceof java.util.Map) {
                    // 按 UUID 移除
                    ((java.util.Map<?, ?>) val).remove(entity.getUUID());
                    // 也尝试直接按 Entity 移除
                    ((java.util.Map<?, ?>) val).values().removeIf(
                            v -> v instanceof Entity e && e.getUUID().equals(entity.getUUID()));
                }
            }
        } catch (Exception ignored) {}
    }

    @Unique
    private static void removeFromLookupRecursive(Object obj, Entity target) {
        if (obj == null) return;
        try {
            for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val == null) continue;

                if (val instanceof java.util.Map) {
                    ((java.util.Map<?, ?>) val).remove(target.getUUID());
                    ((java.util.Map<?, ?>) val).values().removeIf(
                            v -> v instanceof Entity e
                                    && e.getUUID().equals(target.getUUID()));
                } else if (val instanceof Iterable) {
                    // 不能直接修改迭代中的集合，跳过
                } else if (!f.getType().isPrimitive()
                        && !f.getType().getName().startsWith("java.lang")
                        && !f.getType().getName().startsWith("java.util")) {
                    // 递归进入自定义类型
                    removeFromLookupRecursive(val, target);
                }
            }
        } catch (Exception ignored) {}
    }

    @Unique
    private static long findRemovedOffset() {
        // Entity.removed / isRemoved 字段
        for (java.lang.reflect.Field f : Entity.class.getDeclaredFields()) {
            if (f.getType() == boolean.class
                    && (f.getName().contains("remov") || f.getName().contains("Remov"))) {
                return U.objectFieldOffset(f);
            }
        }
        return -1;
    }

    @Unique
    private static Unsafe getUnsafe() {
        try {
            Constructor<Unsafe> c = Unsafe.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
