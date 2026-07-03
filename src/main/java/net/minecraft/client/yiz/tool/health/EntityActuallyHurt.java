package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.syncher.EntityDataAccessor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/**
 * 自定义伤害管道
 * 实现分析文档 §四 描述的完整自定义伤害系统。
 *
 * <p>提供两种伤害模式：
 * <ul>
 *   <li>{@link #actuallyHurt} — 直接扣血，受 {@code specialGetHealth} 截断</li>
 *   <li>{@link #actuallyHurtForDelta} — 扣 delta（压缩血量上限）</li>
 * </ul>
 */
public final class EntityActuallyHurt {

    // ==================== 反射句柄 ====================

    /** LivingEntity.health 字段的 VarHandle */
    private static final VarHandle HEALTH_FIELD;
    /** LivingEntity.DATA_HEALTH_ID 字段的 VarHandle */
    private static final VarHandle DATA_HEALTH_ID_FIELD;
    /** 是否可用的标记 */
    private static final boolean REFLECTION_AVAILABLE;

    static {
        boolean ok = false;
        VarHandle healthField = null;
        VarHandle dataHealthIdField = null;

        try {
            Field f = LivingEntity.class.getDeclaredField("health");
            f.setAccessible(true);
            healthField = MethodHandles.lookup().unreflectVarHandle(f);

            Field d = LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
            d.setAccessible(true);
            dataHealthIdField = MethodHandles.lookup().unreflectVarHandle(d);

            ok = true;
        } catch (Exception e) {
            // 反射初始化失败（环境限制），回退到 setHealth()
            System.err.println("[yizmodqzk] EntityActuallyHurt reflection init failed: " + e.getMessage());
        }

        HEALTH_FIELD = healthField;
        DATA_HEALTH_ID_FIELD = dataHealthIdField;
        REFLECTION_AVAILABLE = ok;
    }

    private EntityActuallyHurt() {}

    // ==================== 1. 标准伤害 ====================

    /**
     * 直接扣血伤害。
     * 最终血量被 {@code Math.min(health - amount, maxHealth)} 截断。
     * 受 ASM/Mixin 层的 {@code specialGetHealth} 截断影响。
     */
    public static void actuallyHurt(LivingEntity entity, DamageSource source, float amount) {
        actuallyHurt0(entity, source, amount, false);
    }

    /**
     * 标准伤害 + 可选底层写入。
     *
     * @param special true 时在 setHealth 后额外用 catchSetTrueHealth 写底层数据
     */
    private static void actuallyHurt0(LivingEntity entity, DamageSource source, float amount, boolean special) {
        float currentHealth = entity.getHealth();
        if (Float.isNaN(currentHealth)) currentHealth = 20F;
        if (Float.isNaN(amount)) return;
        float finalHealth = Math.min(currentHealth - amount, entity.getMaxHealth());
        entity.setHealth(finalHealth);
        if (special) {
            catchSetTrueHealth(entity, finalHealth);
        }
    }

    // ==================== 2. Delta 伤害 ====================

    /**
     * Delta 伤害：不是直接扣血，而是降低实体的血量上限偏移量（delta）。
     * {@code specialGetHealth} 在返回时会自动截断血量至 {@code maxHealth + delta}。
     */
    public static void actuallyHurtForDelta(LivingEntity entity, DamageSource source, float amount) {
        actuallyHurt0ForDelta(entity, source, amount, false);
    }

    private static void actuallyHurt0ForDelta(LivingEntity entity, DamageSource source, float amount, boolean special) {
        float rawHealth = entity.getHealth();
        if (Float.isNaN(rawHealth)) rawHealth = 20F;
        if (Float.isNaN(amount)) return;
        float currentHealth = Math.min(rawHealth - amount, entity.getMaxHealth());
        EntityASMUtil.addDelta(entity, -(rawHealth - currentHealth));
        if (special) {
            catchSetTrueHealth(entity, currentHealth);
        }
    }

    // ==================== 3. 强制写底层数据 ====================

    /**
     * 强制设置实体的健康值，完全绕过 {@code LivingEntity.setHealth()} 的所有逻辑
     * （禁疗检查、边界裁剪等）。
     *
     * <p>通过反射直接写入 {@code LivingEntity.health} 字段，
     * 并通过 {@code SynchedEntityData} 同步到客户端。
     *
     * <p>用于需要确保血量精确写入的场景（如回溯、锁血解除等）。
     */
    @SuppressWarnings("unchecked")
    public static void catchSetTrueHealth(LivingEntity living, float value) {
        if (!REFLECTION_AVAILABLE || HEALTH_FIELD == null) {
            // 回退：使用普通 setHealth
            living.setHealth(value);
            return;
        }

        try {
            // 1. 直接写 health 字段（绕过 setHealth 的所有 Mixin 拦截）
            HEALTH_FIELD.set(living, value);

            // 2. 更新 SynchedEntityData（同步到客户端）
            if (DATA_HEALTH_ID_FIELD != null) {
                EntityDataAccessor<Float> accessor =
                    (EntityDataAccessor<Float>) DATA_HEALTH_ID_FIELD.get(null);
                living.getEntityData().set(accessor, value);
            }
        } catch (Exception e) {
            // 出错时回退
            living.setHealth(value);
        }
    }

    // ==================== 4. 血量 DataParameter 自动发现 ====================

    /**
     * 获取实体的血量 DataParameter。
     * 优先使用标准 {@code LivingEntity.DATA_HEALTH_ID}。
     */
    @SuppressWarnings("unchecked")
    public static EntityDataAccessor<Float> findHealthAccessor(LivingEntity entity) {
        if (DATA_HEALTH_ID_FIELD != null) {
            try {
                return (EntityDataAccessor<Float>) DATA_HEALTH_ID_FIELD.get(null);
            } catch (Exception ignored) {}
        }

        // 反射回退：扫描所有静态 EntityDataAccessor 字段
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != EntityDataAccessor.class) continue;

                String name = field.getName().toUpperCase().replace("_", "");
                if (name.contains("HEALTH") && !name.contains("MAXHEALTH")) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(null);
                        if (val instanceof EntityDataAccessor<?> accessor) {
                            return (EntityDataAccessor<Float>) accessor;
                        }
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
