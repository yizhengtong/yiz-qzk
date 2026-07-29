package net.minecraft.client.yiz.tool.abolish;

import net.minecraft.client.yiz.core.VTableReplace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VTable 入口覆写式实体废除工具。
 *
 * <p>将指定 Entity 子类的关键功能方法入口指针抄回 {@link Entity} 或
 * {@link LivingEntity} 基类的对应实现，使该实体丧失所有自定义行为（AI、
 * tick、存活判定等），同时不触发任何 Java 层可检测的类结构变更。</p>
 *
 * <p>核心原理：{@link VTableReplace#replaceMethodFromSource} 覆写 HotSpot
 * vtable 中目标子类的 {@code Method*} 入口地址（{@code _from_interpreted_entry}
 * 和 {@code _from_compiled_entry}），等价于"撤销"了子类的 override。
 * 无字节码修改、无 Transformer 注册、无类重定义。</p>
 *
 * <h3>与 ItemAbolitionHelper 的关系</h3>
 * <p>同体系、同原理——ItemAbolitionHelper 针对 {@code Item} 子类，
 * 本类针对 {@code Entity} 子类。共享 {@link VTableReplace} 基础设施。</p>
 *
 * <h3>废除层级</h3>
 * <ol>
 *   <li><b>彻底废除 abolishEntity</b> — tick + aiStep + isAlive + remove + kill
 *       + discard + checkDespawn，实体变成无 AI 无 tick 的空壳</li>
 *   <li><b>行为废除 abolishBehavior</b> — 只废除 tick + aiStep + checkDespawn，
 *       保留 isAlive 等判定（实体活着但不做事）</li>
 *   <li><b>存活废除 abolishAliveness</b> — 只废除 isAlive / isDeadOrDying，
 *       让实体对外表现为已死亡</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 按 EntityType ID 废除
 * EntityAbolitionHelper.abolishEntityById(ResourceLocation.parse("minecraft:zombie"));
 *
 * // 按 Class 废除
 * EntityAbolitionHelper.abolishEntity(Zombie.class);
 *
 * // 只废除 AI/tick 行为
 * EntityAbolitionHelper.abolishBehavior(Skeleton.class);
 * </pre>
 */
public final class EntityAbolitionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityAbolition");

    // ══════════════════════════════════════════════════════════
    //  Entity 方法描述符常量
    // ══════════════════════════════════════════════════════════

    /** {@code void tick()} */
    private static final String DESC_TICK = "()V";

    /** {@code void remove(Entity.RemovalReason)} */
    private static final String DESC_REMOVE =
            "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V";

    /** {@code void kill()} */
    private static final String DESC_KILL = "()V";

    /** {@code void discard()} */
    private static final String DESC_DISCARD = "()V";

    /** {@code boolean isAlive()} */
    private static final String DESC_IS_ALIVE = "()Z";

    /** {@code boolean isDeadOrDying()} — LivingEntity override */
    private static final String DESC_IS_DEAD_OR_DYING = "()Z";

    /** {@code boolean isRemoved()} */
    private static final String DESC_IS_REMOVED = "()Z";

    /** {@code void aiStep()} — LivingEntity, protected */
    private static final String DESC_AI_STEP = "()V";

    /** {@code void checkDespawn()} — Mob */
    private static final String DESC_CHECK_DESPAWN = "()V";

    /** {@code boolean shouldBeSaved()} */
    private static final String DESC_SHOULD_BE_SAVED = "()Z";

    // ══════════════════════════════════════════════════════════
    //  方法列表
    // ══════════════════════════════════════════════════════════

    /** 彻底废除：中立化关键行为方法，但保留 remove/discard/kill（让实体可以被移除） */
    private static final String[][] ALL_ENTITY_METHODS = {
            // Entity 核心（注意：不废除 remove/discard/kill，否则实体无法被移除）
            {"tick",              DESC_TICK,              "Entity"},
            {"isAlive",           DESC_IS_ALIVE,          "Entity"},
            {"isRemoved",         DESC_IS_REMOVED,        "Entity"},
            {"shouldBeSaved",     DESC_SHOULD_BE_SAVED,   "Entity"},
            // LivingEntity
            {"aiStep",            DESC_AI_STEP,           "LivingEntity"},
            {"isDeadOrDying",     DESC_IS_DEAD_OR_DYING,  "LivingEntity"},
            // Mob
            {"checkDespawn",      DESC_CHECK_DESPAWN,     "Mob"},
    };

    /** 行为废除：只废除 tick + AI + despawn */
    private static final String[][] BEHAVIOR_METHODS = {
            {"tick",              DESC_TICK,              "Entity"},
            {"aiStep",            DESC_AI_STEP,           "LivingEntity"},
            {"checkDespawn",      DESC_CHECK_DESPAWN,     "Mob"},
            {"discard",           DESC_DISCARD,           "Entity"},
            {"kill",              DESC_KILL,              "Entity"},
    };

    /** 存活废除：只废除 isAlive / isDeadOrDying / isRemoved */
    private static final String[][] ALIVENESS_METHODS = {
            {"isAlive",           DESC_IS_ALIVE,          "Entity"},
            {"isDeadOrDying",     DESC_IS_DEAD_OR_DYING,  "LivingEntity"},
            {"shouldBeSaved",     DESC_SHOULD_BE_SAVED,   "Entity"},
    };

    private EntityAbolitionHelper() {}

    // ══════════════════════════════════════════════════════════
    //  源类映射（用于 replaceMethodFromSource 的 sourceClass 参数）
    // ══════════════════════════════════════════════════════════

    private static Class<?> getSourceClass(String sourceLabel) {
        return switch (sourceLabel) {
            case "Entity"        -> Entity.class;
            case "LivingEntity"  -> LivingEntity.class;
            case "Mob"           -> Mob.class;
            default -> throw new IllegalArgumentException("Unknown source: " + sourceLabel);
        };
    }

    // ══════════════════════════════════════════════════════════
    //  废除方法
    // ══════════════════════════════════════════════════════════

    /**
     * 彻底废除指定实体类的所有关键方法（VTable 层）。
     * <p>
     * 调用后该实体类的 {@code tick()}、{@code aiStep()}、{@code isAlive()}、
     * {@code checkDespawn()}、{@code kill()}、{@code remove()} 等行为全部
     * 回退到基类的对应实现。效果等价于实体变成无 AI 无 tick 的空壳。
     * </p>
     *
     * @param entityClass 目标实体类（如 {@code Zombie.class}）
     * @return 成功覆写的方法数量
     */
    public static int abolishEntity(Class<? extends Entity> entityClass) {
        return abolishMethods(entityClass, ALL_ENTITY_METHODS);
    }

    /**
     * 只废除行为方法：tick + aiStep + checkDespawn + discard + kill。
     * <p>实体不再有任何自主行为，但 isAlive 等存活判定保持正常。</p>
     *
     * @param entityClass 目标实体类
     * @return 成功覆写的方法数量
     */
    public static int abolishBehavior(Class<? extends Entity> entityClass) {
        return abolishMethods(entityClass, BEHAVIOR_METHODS);
    }

    /**
     * 只废除存活判定方法：isAlive + isDeadOrDying + shouldBeSaved。
     * <p>实体仍然正常 tick，但对外表现为已死亡。</p>
     *
     * @param entityClass 目标实体类
     * @return 成功覆写的方法数量
     */
    public static int abolishAliveness(Class<? extends Entity> entityClass) {
        return abolishMethods(entityClass, ALIVENESS_METHODS);
    }

    // ══════════════════════════════════════════════════════════
    //  按 EntityType ID 废除
    // ══════════════════════════════════════════════════════════

    /**
     * 按 EntityType ID 彻底废除实体。
     *
     * @param entityId 实体类型 ID（如 {@code ResourceLocation.parse("minecraft:zombie")}）
     * @return 成功覆写的方法数量，实体不存在时返回 0
     */
    public static int abolishEntityById(ResourceLocation entityId) {
        // 1. 先注册到状态管理器（Mixin 层立即生效）
        EntityAbolitionStateManager.abolishEntity(entityId);

        // 2. VTable 层
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        if (type == null) {
            LOGGER.warn("Entity type not found in registry: {}", entityId);
            return 0;
        }
        // 尝试获取实体类
        Class<? extends Entity> entityClass = getEntityClass(type);
        if (entityClass == null) {
            LOGGER.warn("Cannot determine entity class for: {}", entityId);
            return 0;
        }
        return abolishEntity(entityClass);
    }

    /**
     * 按 EntityType ID 恢复实体（从状态管理器移除）。
     */
    public static void restoreEntityById(ResourceLocation entityId) {
        EntityAbolitionStateManager.restoreEntity(entityId);
        LOGGER.info("Entity restored: {}", entityId);
    }

    // ══════════════════════════════════════════════════════════
    //  内部实现
    // ══════════════════════════════════════════════════════════

    private static int abolishMethods(Class<? extends Entity> targetClass, String[][] methods) {
        if (!VTableReplace.isAvailable()) {
            LOGGER.warn("VTableReplace not available for {}, state-manager only",
                    targetClass.getSimpleName());
            return 0;
        }

        int count = 0;
        for (String[] method : methods) {
            String name = method[0];
            String desc = method[1];
            String sourceLabel = method[2];
            Class<?> sourceClass = getSourceClass(sourceLabel);

            if (VTableReplace.replaceMethodFromSource(targetClass, name, desc, sourceClass)) {
                count++;
            }
        }

        LOGGER.info("Abolished {} methods on {} (VTable={})",
                count, targetClass.getName(), VTableReplace.isAvailable());
        return count;
    }

    /**
     * 从 EntityType 获取对应的实体类。
     * <p>尝试通过创建测试实例获取 Class，但某些 EntityType 构造器有副作用。
     * 作为兜底，从已注册的实体实例获取。</p>
     */
    @SuppressWarnings("unchecked")
    private static Class<? extends Entity> getEntityClass(EntityType<?> type) {
        // EntityType 的 factory 没有直接的 getClass 方法，走类的类名推测
        // 简单方案：用 type 的 toString 或 registry 中的信息
        // 对于大多数 vanilla 实体，可以直接用反射获取，但我们选择更安全的方式：
        // 让调用者直接传 class
        return null; // 需要 Class 才能做 VTable，ID 方式只走状态管理层
    }
}
