package net.minecraft.client.yiz.api;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 伤害属性注册表
 * <p>
 * 注册的属性将作为额外伤害源。攻击者每次近战攻击时，
 * 自动计算所有已注册属性的加权总和，通过 {@link YizModQZKAPI#damage} 附加等量伤害。
 * </p>
 * <p>
 * 伤害经由三層系统（Delta → ChannelScanner → DirectHealthFallback）施加，
 * 绕过目标实体的自定义 {@code hurt()} 方法。
 * </p>
 */
// 大白话: 改血方法
public final class DamageAttributeRegistry {

    private static final Map<Holder<Attribute>, Float> DAMAGE_ATTRIBUTES = new ConcurrentHashMap<>();

    private DamageAttributeRegistry() {}

    /**
     * 注册一个属性为伤害属性（比例系数 = 1.0）。
     * 攻击者拥有该属性时，每次攻击额外附加等量伤害。
     */
    public static void register(Holder<Attribute> holder) {
        DAMAGE_ATTRIBUTES.put(holder, 1.0f);
    }

    /**
     * 注册一个属性为伤害属性，并指定比例系数。
     * 攻击者拥有该属性时，每次攻击额外附加 {@code value * scale} 点伤害。
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性值产生的伤害量）
     */
    public static void register(Holder<Attribute> holder, float scale) {
        DAMAGE_ATTRIBUTES.put(holder, scale);
    }

    /**
     * 获取攻击者身上所有已注册伤害属性的加权总值。
     */
    public static float getTotalValue(LivingEntity attacker) {
        double total = 0;
        for (var entry : DAMAGE_ATTRIBUTES.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return (float) total;
    }

    /**
     * 获取所有已注册的属性集合（只读）。
     */
    public static Set<Holder<Attribute>> getRegistered() {
        return Set.copyOf(DAMAGE_ATTRIBUTES.keySet());
    }
}
