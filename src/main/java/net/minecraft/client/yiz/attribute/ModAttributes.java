package net.minecraft.client.yiz.attribute;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义属性注册表
 * 注册本模组新增的实体属性。
 *
 * <p>所有属性以 {@link Holder} 形式暴露，可直接传入
 * {@link net.minecraft.world.entity.LivingEntity#getAttributeValue(Holder)}。</p>
 */
public final class ModAttributes {

    private static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, tizMod.MODID);

    // ==================== 攻击属性 ====================

    /**
     * 灵梦固定伤害
     * 每点数值在伤害计算中作为固定伤害值。
     * 例：10 → 每次造成 10 点固定伤害
     */
    public static final Holder<Attribute> REIMU_FLAT_DAMAGE = ATTRIBUTES.register(
        "reimu_flat_damage",
        () -> new RangedAttribute("attribute.yizmodqzk.reimu_flat_damage", 0.0, 0.0, Double.MAX_VALUE)
    );

    /**
     * 灵梦百分比伤害
     * 每点数值在伤害计算中作为目标最大生命值的百分比。
     * 例：10 → 造成目标最大生命值 10% 的伤害
     */
    public static final Holder<Attribute> REIMU_PERCENT_DAMAGE = ATTRIBUTES.register(
        "reimu_percent_damage",
        () -> new RangedAttribute("attribute.yizmodqzk.reimu_percent_damage", 0.0, 0.0, Double.MAX_VALUE)
    );

    // ==================== 防御属性 ====================

    /**
     * 眷恋
     * 1 ~ 10，每点提供 10% 禁疗效果。
     * 10 点 = 100% 禁疗（完全无法被治疗）
     */
    public static final Holder<Attribute> ATTACHMENT = ATTRIBUTES.register(
        "attachment",
        () -> new RangedAttribute("attribute.yizmodqzk.attachment", 0.0, 0.0, Double.MAX_VALUE)
    );

    /**
     * 获取 DeferredRegister 实例，用于在 Mod 构造器中注册。
     */
    public static DeferredRegister<Attribute> getRegistry() {
        return ATTRIBUTES;
    }

    private ModAttributes() {}
}
