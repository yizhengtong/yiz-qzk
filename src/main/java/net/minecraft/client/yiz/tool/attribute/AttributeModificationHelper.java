package net.minecraft.client.yiz.tool.attribute;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.List;

/**
 * 属性修改辅助工具
 * 提供标准化的实体属性修改方法。
 */
public final class AttributeModificationHelper {

    private static final String MODIFIER_PREFIX = "yizmodqzk_";

    private AttributeModificationHelper() {}

    /**
     * 修改实体属性（临时）。
     *
     * @param entity    目标实体
     * @param attribute 要修改的属性
     * @param value     修改值
     * @param operation 操作类型
     * @param duration  持续时间（刻），<= 0 表示永久
     */
    public static void modifyAttribute(
        LivingEntity entity,
        Holder<Attribute> attribute,
        double value,
        AttributeModifier.Operation operation,
        int duration
    ) {
        ResourceLocation modifierId = ResourceLocation.withDefaultNamespace(MODIFIER_PREFIX + System.nanoTime());
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            value,
            operation
        );

        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifierId)) {
            instance.addTransientModifier(modifier);
        }

        // 设置定时器移除修正器
        if (duration > 0) {
            scheduleModifierRemoval(entity, attribute, modifierId, duration);
        }
    }

    /**
     * 修改实体属性（永久）。
     */
    public static void modifyAttributePermanent(
        LivingEntity entity,
        Holder<Attribute> attribute,
        double value,
        AttributeModifier.Operation operation
    ) {
        modifyAttribute(entity, attribute, value, operation, -1);
    }

    /**
     * 批量修改属性。
     */
    public static void modifyMultipleAttributes(
        LivingEntity entity,
        List<AttributeModifierData> modifiers
    ) {
        for (AttributeModifierData data : modifiers) {
            modifyAttribute(entity, data.attribute(), data.value(), data.operation(), data.duration());
        }
    }

    /**
     * 移除特定修正器。
     */
    public static void removeModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation modifierId) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
        }
    }

    /**
     * 移除给定属性上的所有 yizmodqzk 修正器。
     */
    public static void removeAllModifiers(LivingEntity entity, Holder<Attribute> attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifiers();
        }
    }

    /**
     * 设置定时器移除修正器。
     */
    private static void scheduleModifierRemoval(
        LivingEntity entity,
        Holder<Attribute> attribute,
        ResourceLocation modifierId,
        int duration
    ) {
        if (entity.level() != null && entity.level().getServer() != null) {
            entity.level().getServer().execute(() -> {
                if (entity.isAlive()) {
                    removeModifier(entity, attribute, modifierId);
                }
            });
        }
    }

    /**
     * 属性修正器数据记录。
     */
    public record AttributeModifierData(
        Holder<Attribute> attribute,
        double value,
        AttributeModifier.Operation operation,
        int duration
    ) {}
}
