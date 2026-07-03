package net.minecraft.client.yiz.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 前置库自定义属性注册中心。
 *
 * <p>使用 NeoForge {@link DeferredRegister} 注册为原版 Attribute 系统的一等公民，
 * 支持 {@code DataComponents.ATTRIBUTE_MODIFIERS} 物品修饰符、
 * {@code /attribute} 指令查询和修改。</p>
 *
 * <h3>装备栏自动过滤</h3>
 * <p>物品通过 {@code EquipmentSlotGroup} 挂载的 AttributeModifier 只在对应槽位生效。
 * 例如主手武器上的暴击率只在手持时计入 {@code getAttributeValue()}，背包中不计入。</p>
 */
public final class YizAttributes {

    private YizAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, "yizmodqzk");

    /**
     * 暴击率 — 任何攻击触发暴击的概率。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> CRIT_RATE =
        ATTRIBUTES.register("crit_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.crit_rate", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 暴伤 — 暴击时的额外伤害倍率（增量百分比，非相乘）。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> CRIT_DAMAGE =
        ATTRIBUTES.register("crit_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.crit_damage", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 吸血 — 造成伤害时回复自身生命的比例。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> LIFE_STEAL =
        ATTRIBUTES.register("life_steal",
            () -> new RangedAttribute("attribute.yizmodqzk.life_steal", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  伤害范围（溅射/AoE）
    // ═══════════════════════════════════════════════════════════

    /**
     * 伤害范围半径 — 攻击命中后对目标周围实体的溅射半径。
     * <p>值域 ≥0，无上限（格）。</p>
     */
    public static final Holder<Attribute> SPLASH_RADIUS =
        ATTRIBUTES.register("splash_radius",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_radius", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 伤害范围百分比 — 溅射伤害占原伤害的比例。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> SPLASH_DAMAGE =
        ATTRIBUTES.register("splash_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_damage", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 伤害范围衰减 — 溅射伤害从中心到边缘的衰减强度。
     * <p>值域 ≥0，无上限。中间使用平滑二次曲线插值。</p>
     */
    public static final Holder<Attribute> SPLASH_FALLOFF =
        ATTRIBUTES.register("splash_falloff",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_falloff", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));
}
