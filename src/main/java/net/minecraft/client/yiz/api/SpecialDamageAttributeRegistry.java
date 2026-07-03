package net.minecraft.client.yiz.api;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 特殊伤害属性注册表
 * <p>
 * 注册的属性将自动作为真实伤害/破甲伤害/破无敌帧的数值来源。
 * 攻击者每次近战攻击时，自动计算所有已注册属性的总值并附加对应类型的伤害。
 * </p>
 *
 * <h3>三种特殊伤害类型</h3>
 * <ul>
 *   <li>{@link #registerTrueDamage(Holder, float)} — 真实伤害（直接扣血，无视一切）</li>
 *   <li>{@link #registerArmorPiercing(Holder, float)} — 破甲伤害（跳过护甲）</li>
 *   <li>{@link #registerPierceInvulnerability(Holder, float)} — 破无敌帧（与破甲组合使用）</li>
 * </ul>
 */
// 大白话: 真伤/破甲/破无敌方法
public final class SpecialDamageAttributeRegistry {

    private static final Map<Holder<Attribute>, Float> TRUE_DAMAGE = new ConcurrentHashMap<>();
    private static final Map<Holder<Attribute>, Float> ARMOR_PIERCING = new ConcurrentHashMap<>();
    private static final Map<Holder<Attribute>, Float> PIERCE_INVULNERABILITY = new ConcurrentHashMap<>();

    private SpecialDamageAttributeRegistry() {}

    // ==================== 注册 ====================

    /**
     * 注册一个属性为真实伤害属性。
     * <p>
     * 攻击者每拥有 1 点该属性，每次近战攻击额外附加 {@code scale} 点真实伤害。
     * 真实伤害直接 {@code setHealth(health - damage)}，无视护甲/无敌帧/减伤。
     * </p>
     */
    public static void registerTrueDamage(Holder<Attribute> holder, float scale) {
        TRUE_DAMAGE.put(holder, scale);
    }

    /**
     * 注册一个属性为破甲伤害属性。
     * <p>
     * 攻击者每拥有 1 点该属性，每次近战攻击附加 {@code scale} 点破甲伤害。
     * 破甲伤害跳过护甲减伤，但仍受无敌帧限制。
     * </p>
     */
    public static void registerArmorPiercing(Holder<Attribute> holder, float scale) {
        ARMOR_PIERCING.put(holder, scale);
    }

    /**
     * 注册一个属性为破无敌帧属性。
     * <p>
     * 攻击者拥有该属性时（总值 > 0），破甲伤害同时无视无敌帧。
     * 单独注册该属性但不注册破甲属性时无效。
     * </p>
     */
    public static void registerPierceInvulnerability(Holder<Attribute> holder, float scale) {
        PIERCE_INVULNERABILITY.put(holder, scale);
    }

    // ==================== 查询 ====================

    /**
     * 获取攻击者身上所有已注册真实伤害属性的总值（已乘缩放系数）。
     */
    public static float getTrueDamageTotal(LivingEntity attacker) {
        double total = 0;
        for (var entry : TRUE_DAMAGE.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return (float) total;
    }

    /**
     * 获取攻击者身上所有已注册破甲伤害属性的总值（已乘缩放系数）。
     */
    public static float getArmorPiercingTotal(LivingEntity attacker) {
        double total = 0;
        for (var entry : ARMOR_PIERCING.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return (float) total;
    }

    /**
     * 检查攻击者是否拥有破无敌帧属性（总值 > 0）。
     */
    public static boolean hasPierceInvulnerability(LivingEntity attacker) {
        double total = 0;
        for (var entry : PIERCE_INVULNERABILITY.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return total > 0;
    }
}
