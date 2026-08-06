package net.minecraft.client.yiz.tool;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Yiz 实体管理器 — 通用实体生命周期管理入口（前置库，所有实体可用）。
 *
 * <p>核心方法 {@link #checkAndRemove}：检测实体生命值 ≤0 → 走<b>原版移除链</b>移除。
 * 与 {@link EntityRemovalUtil#forceRemove}（强制绕过路径）互补——本管理器走原版
 * {@code remove(RemovalReason.KILLED)} 管道，保留正常死亡/掉落/移除保护协同：
 * <ul>
 *   <li>对 <b>YizxianMob</b>（下游本模组实体）：前置库在 {@code net.minecraft.client.yiz} 包下，
 *       下游 {@code EntityRemoveProtectionMixin.isYizCaller()} 对同包前缀天然放行 → 不被移除保护拦。</li>
 *   <li>对普通原版/第三方实体：remove 直接生效。</li>
 * </ul></p>
 *
 * <p>方法式（调用方触发）：不自发扫描，由调用方在合适时机（如每 tick / 死亡判定后）调用。</p>
 */
public final class YizieManager {

    private YizieManager() {}

    /**
     * 检测实体生命值 ≤0 → 走原版移除链移除。
     *
     * @param entity 目标实体
     * @return true = 已移除；false = 未移除（存活 / 已移除 / 客户端 / 非生物）
     */
    public static boolean checkAndRemove(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.isRemoved()) return false;
        if (living.level().isClientSide()) return false;
        if (living.getHealth() > 0.0F) return false;

        // 走原版移除链（KILLED 原因）——保留正常死亡/掉落流程，与现有移除保护协同
        living.remove(Entity.RemovalReason.KILLED);
        return true;
    }

    /**
     * 便捷入口：只检测是否「该移除」（生命值 ≤0 且存活），不移除。
     * 供调用方先判定再决定移除方式。
     */
    public static boolean shouldRemove(LivingEntity entity) {
        return entity != null && !entity.isRemoved()
                && !entity.level().isClientSide()
                && entity.getHealth() <= 0.0F;
    }
}
