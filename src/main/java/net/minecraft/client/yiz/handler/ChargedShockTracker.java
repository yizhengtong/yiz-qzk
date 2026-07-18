package net.minecraft.client.yiz.handler;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 充能态攻击感电标记 — 攻击时标记目标，tick 时批量施感电，避免 hurt 内递归。
 */
public final class ChargedShockTracker {

    /** 玩家 UUID → 本次 tick 要施加感电的目标列表 */
    private static final ConcurrentHashMap<UUID, List<LivingEntity>> PENDING = new ConcurrentHashMap<>();

    private ChargedShockTracker() {}

    /** 攻击时标记目标（hurt 层调用）。 */
    public static void markTarget(LivingEntity target) {
        // target 的 lastHurtByMob 即为攻击者
        if (!(target.getLastHurtByMob() instanceof ServerPlayer player)) return;
        if (!PassiveChargeTracker.hasTempBuff(player)) return;
        PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(target);
    }

    /** 玩家 tick 时调用，对积攒目标批量施加感电。 */
    public static void tick(ServerPlayer player) {
        List<LivingEntity> targets = PENDING.remove(player.getUUID());
        if (targets == null || targets.isEmpty()) return;

        double spellPow = YizAttributes.getEffectiveSpellPower(player);
        float dmg = (float)(0.85 + spellPow * 0.225);

        for (LivingEntity t : targets) {
            if (!t.isAlive()) continue;
            // 与雷鸣电甲同款：目标成为感电中心，周期性自身周围 AoE
            net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockWithDamage(
                t, player, dmg, 5.0f, 20, 10);
        }
    }
}
