package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 回击注册表
 * <p>
 * 在下游模组注册条件判断，前置模组在受击时触发回击。
 * 通过临时修饰玩家 ATTACK_DAMAGE 实现伤害缩放，再调用 {@link Player#attack}，
 * 让所有子属性绑定系统自动生效。
 * </p>
 */
// 大白话: 回击方法
public final class CounterAttackRegistry {

    private static final ResourceLocation MODIFIER_ID = ResourceLocation.parse("yizmodqzk:counter_attack");
    private static final List<Trigger> TRIGGERS = new CopyOnWriteArrayList<>();

    private CounterAttackRegistry() {}

    @FunctionalInterface
    public interface Trigger {
        /**
         * @param player 受击玩家
         * @param source 伤害来源实体
         * @return 回击伤害倍率（0 表示不回击，0.1 表示 1/10 伤害）
         */
        float getMultiplier(Player player, LivingEntity source);
    }

    public static void register(Trigger trigger) {
        TRIGGERS.add(trigger);
    }

    /**
     * 尝试触发回击。
     * 由 Mixin 在 actuallyHurt TAIL 调用。
     */
    public static void tryCounterAttack(Player player, LivingEntity source) {
        if (player.level().isClientSide()) return;
        if (source.isRemoved() || !source.isAlive()) return;

        float multiplier = 0;
        for (Trigger t : TRIGGERS) {
            multiplier = Math.max(multiplier, t.getMultiplier(player, source));
        }
        if (multiplier <= 0) return;

        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;

        // 临时缩放攻击伤害
        // ADD_MULTIPLIED_TOTAL: final = base × (1 + amount); amount = multiplier - 1
        AttributeModifier mod = new AttributeModifier(
                MODIFIER_ID, (double) (multiplier - 1.0f), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        // 防御性先移除旧修饰符（同 ComboAttackHelper）：连续触发时残留会导致
        // addTransientModifier 抛 "Modifier is already applied on this attribute!"
        attack.removeModifier(MODIFIER_ID);
        attack.addTransientModifier(mod);

        // 重置攻击冷却，确保 attack() 不会因冷却为零而打出 0 伤害
        try {
            var field = net.minecraft.world.entity.player.Player.class.getDeclaredField("attackStrengthTicker");
            field.setAccessible(true);
            field.setInt(player, (int) player.getCurrentItemAttackStrengthDelay());
        } catch (Exception ignored) {}

        try {
            player.attack(source);
        } finally {
            attack.removeModifier(MODIFIER_ID);
        }
    }
}
