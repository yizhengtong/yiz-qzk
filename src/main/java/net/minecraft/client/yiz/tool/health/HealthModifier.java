package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
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
     * @param context 效果上下文
     * @return 修改量（正数 = 治疗，负数 = 伤害）
     */
    double getModificationAmount(LivingEntity entity, EffectContext context);

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
