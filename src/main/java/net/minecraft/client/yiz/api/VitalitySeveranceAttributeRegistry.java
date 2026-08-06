package net.minecraft.client.yiz.api;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 禁疗属性注册表
 * <p>
 * 注册的属性将自动作为禁疗源。攻击者每次近战攻击时，
 * 自动计算所有已注册属性的总和，为目标施加禁疗配置。
 * </p>
 *
 * <h3>两种禁疗方式</h3>
 * <ul>
 *   <li>{@link #registerPercent(Holder)} — 百分比禁疗绑定</li>
 *   <li>{@link #registerFixed(Holder)} — 固定值禁疗绑定</li>
 * </ul>
 */
// 大白话: 禁疗方法
public final class VitalitySeveranceAttributeRegistry {

    /**
     * 百分比禁疗属性条目：属性 + 缩放系数。
     */
    private static final Map<Holder<Attribute>, Float> PERCENT_ENTRIES = new ConcurrentHashMap<>();

    /**
     * 固定值禁疗属性条目：属性 + 缩放系数。
     */
    private static final Map<Holder<Attribute>, Float> FIXED_ENTRIES = new ConcurrentHashMap<>();

    private VitalitySeveranceAttributeRegistry() {}

    /**
     * 注册一个属性为百分比禁疗属性。
     * <p>
     * 攻击者每拥有 1 点该属性，目标受到 {@code scale}% 的治疗削减。
     * 例：scale=10，攻击者有 3 点 → 目标 30% 禁疗。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的禁疗百分比）
     */
    public static void registerPercent(Holder<Attribute> holder, float scale) {
        PERCENT_ENTRIES.put(holder, scale);
    }

    /**
     * 注册一个属性为固定值禁疗属性。
     * <p>
     * 攻击者每拥有 1 点该属性，目标每次治疗被削减 {@code scale} 点。
     * 例：scale=5，攻击者有 3 点 → 目标每次治疗减 15 点。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的禁疗值）
     */
    public static void registerFixed(Holder<Attribute> holder, float scale) {
        FIXED_ENTRIES.put(holder, scale);
    }

    /**
     * 获取攻击者身上所有已注册百分比禁疗属性的总值（已乘缩放系数）。
     */
    public static float getPercentTotal(LivingEntity attacker) {
        double total = 0;
        for (var entry : PERCENT_ENTRIES.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return (float) total;
    }

    /**
     * 获取攻击者身上所有已注册固定值禁疗属性的总值（已乘缩放系数）。
     */
    public static float getFixedTotal(LivingEntity attacker) {
        double total = 0;
        for (var entry : FIXED_ENTRIES.entrySet()) {
            total += attacker.getAttributeValue(entry.getKey()) * entry.getValue();
        }
        return (float) total;
    }

    // ==================== 查询 / 调试 ====================

    public static Map<Holder<Attribute>, Float> getRegisteredPercent() {
        return Map.copyOf(PERCENT_ENTRIES);
    }

    public static Map<Holder<Attribute>, Float> getRegisteredFixed() {
        return Map.copyOf(FIXED_ENTRIES);
    }
}
