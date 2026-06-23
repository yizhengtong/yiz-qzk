package net.minecraft.client.yiz.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 伪装伤害 API — 触发假伤害动画。
 *
 * <h3>概念</h3>
 * <ol>
 *   <li>{@link #fakeHurt(LivingEntity, ServerPlayer)} — 只触发原版受击动画 + 闪烁红帧，
 *       不造成任何实际伤害。用于任何需要"看起来被打了一下"的场景。</li>
 *   <li>{@link #fakeKnockback(LivingEntity, double, double, double)} — 独立击退，
 *       不依赖 hurt 调用也能推动实体。</li>
 * </ol>
 *
 * <p>调用方随后自行决定如何施加真实伤害（如 {@code trueDamage} / {@code modifyHealth}）。</p>
 */
public final class YizModQZKWZAPI {

    private YizModQZKWZAPI() {}

    /**
     * 假受击动画 — hurt(amount=0) 触发原版受伤闪烁 + 音效 + 击退待处理信号。
     * 不造成任何血量变化。返回是否成功触发（实体是否处于可受伤状态）。
     */
    public static boolean fakeHurt(LivingEntity target, ServerPlayer source) {
        if (target == null || !target.isAlive()) return false;
        // 伤害值设 0 → 只触发动画，不掉血
        return target.hurt(source.damageSources().playerAttack(source), 0);
    }

    /**
     * 独立击退 — 不通过 hurt 直接推动实体。
     *
     * @param target   目标实体
     * @param dx       击退方向 X（归一化后）
     * @param dy       击退方向 Y
     * @param dz       击退方向 Z
     * @param strength 击退强度（原版约 0.4，剑默认 0.05）
     */
    public static void fakeKnockback(LivingEntity target, double dirX, double dirZ, double strength) {
        if (target == null || !target.isAlive()) return;
        target.knockback(strength, dirX, dirZ);
        target.hurtMarked = true;
    }

    /**
     * 设置受击红帧时间（ticks）。
     * hurt() 已自动设置，此方法用于手动覆盖。
     */
    public static void setHurtTime(LivingEntity target, int ticks) {
        if (target != null) target.hurtTime = ticks;
    }
}
