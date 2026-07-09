package net.minecraft.client.yiz.tool.health;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 健康值修正器接口
 * 开发者实现此接口来提供健康值修改。
 */
public interface HealthModifier {

    /**
     * 获取修正器唯一标识，用于去重和调试。
     */
    ResourceLocation getId();

    /**
     * 获取健康值修改量。
     *
     * @param entity  目标实体
     * @return 修改量（正数 = 治疗，负数 = 伤害）
     */
    double getModificationAmount(LivingEntity entity);

    /**
     * 获取修正器优先级，优先级高的先计算。
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 获取修正器类型。
     */
    default ModifierType getType() {
        return ModifierType.CUSTOM;
    }

    /**
     * 获取修正模式，决定修改量如何应用到实体。
     */
    default ModificationMode getMode() {
        return ModificationMode.ADDITIVE;
    }

    /**
     * 获取人类可读的描述（用于调试和 UI）。
     */
    default String getDescription() {
        return getId().toString();
    }

    // ==================== 内部枚举 ====================

    /**
     * 修正模式枚举。
     * 决定修改量如何应用到实体的健康值。
     */
    enum ModificationMode {
        /**
         * 加法模式（默认）：最终结果 = 当前值 + Σ(修改量)
         * 标准治疗/伤害使用此模式。
         */
        ADDITIVE,

        /**
         * 乘法模式：最终结果 = 当前值 × Π(1 + 修改量)
         * 用于百分比增益/减益。
         */
        MULTIPLICATIVE,

        /**
         * 覆盖模式：直接设置健康值 = 修改量
         * 用于锁血、重置等特殊操作。
         */
        OVERRIDE,

        /**
         * Delta 模式：修改的是健康值上限偏移量，而非直接血量
         * 对应 ASM 层的 FE_GET_HEALTH_DATA delta 机制。
         * 效果：entity.getHealth() 被 Math.min(health, maxHealth + delta) 截断。
         */
        DELTA
    }

    /**
     * 修正器类型枚举。
     */
    enum ModifierType {
        HEALING("healing", "治疗"),
        DAMAGE("damage", "伤害"),
        REGEN("regen", "再生"),
        POISON("poison", "中毒"),
        LIFESTEAL("lifesteal", "生命偷取"),
        CUSTOM("custom", "自定义");

        private final String id;
        private final String displayName;

        ModifierType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
