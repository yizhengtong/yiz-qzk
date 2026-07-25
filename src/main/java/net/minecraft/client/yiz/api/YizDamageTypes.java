package net.minecraft.client.yiz.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 前置库自定义伤害类型（data-driven DamageType）。
 *
 * <p>{@link #SPILL} — 卢登激荡溅射伤害专用类型，设计目标"无视大部分生物免疫"：
 * <ul>
 *   <li>不在任何 {@code DamageTypeTags}（IS_FIRE / IS_LIGHTNING / IS_EXPLOSION / IS_PROJECTILE /
 *       IS_PLAYER_ATTACK / IS_FREEZING …）→ mob 的 tag 免疫（含末影龙对火/弹射物等）全部命中不到它</li>
 *   <li>{@code scaling = never}：不随难度缩放</li>
 *   <li>配合 {@code LudenOverkillHandler.isSpilling} 跳过 modifyHealthForHealBan，全额扣血、不被任何属性加减</li>
 * </ul>
 * 数据包定义：{@code data/yizmodqzk/damage_type/spill.json}</p>
 */
public final class YizDamageTypes {

    private YizDamageTypes() {}

    /** 卢登激荡溅射伤害类型（yizmodqzk:spill）。 */
    public static final ResourceKey<DamageType> SPILL = ResourceKey.create(
        Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("yizmodqzk", "spill"));
}
