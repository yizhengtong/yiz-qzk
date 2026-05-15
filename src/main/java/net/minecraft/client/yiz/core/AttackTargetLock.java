package net.minecraft.client.yiz.core;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 攻击目标锁定器。
 *
 * <p>在 {@code Player.attack()} 被调用时（最早时机，所有 Mixin 之前）
 * 记录玩家本次攻击的原始目标。后续无论其他模组的 Mixin 或事件系统
 * 如何取消/偷换目标，下游模组始终可通过本类取回"玩家真正想攻击的实体"。</p>
 *
 * <p>底层注入由 {@code AttackTargetTransformer}（ASM Agent）完成，
 * 在 premain 阶段执行——早于所有 Mixin，不可被其他模组覆盖。</p>
 */
public final class AttackTargetLock {

    /** 玩家 UUID → 最近一次攻击的原始目标 */
    private static final ConcurrentHashMap<String, Entity> TARGETS = new ConcurrentHashMap<>();

    private AttackTargetLock() {}

    // ── Agent 调用的 Hook 方法 ──

    /**
     * 从 Player.attack() 入口注入：记录原始 target。
     * 由 {@code AttackTargetTransformer} 通过 ASM 调用。
     */
    @SuppressWarnings("unused")
    public static void captureOnAttack(Player player, Entity target) {
        if (player != null && target != null) {
            TARGETS.put(player.getStringUUID(), target);
        }
    }

    /**
     * 从数据包处理入口注入：通过实体 ID 查找原始目标并记录。
     * 由 {@code AttackTargetTransformer} 通过 ASM 调用。
     */
    @SuppressWarnings("unused")
    public static void captureFromPacket(Object playerObj, int entityId) {
        if (!(playerObj instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        Entity target = sp.level().getEntity(entityId);
        if (target != null) {
            TARGETS.put(sp.getStringUUID(), target);
        }
    }

    // ── 公开 API ──

    /**
     * 获取玩家最近一次攻击的原始目标（不被其他模组污染）。
     *
     * @return 原始目标实体，无记录时返回 null
     */
    public static Entity getOriginalTarget(Player player) {
        return TARGETS.get(player.getStringUUID());
    }

    /**
     * 对比当前 event target 与原始目标是否一致。
     *
     * @return true 表示目标未被偷换
     */
    public static boolean isTargetClean(Player player, Entity currentTarget) {
        Entity original = getOriginalTarget(player);
        return original != null && original.equals(currentTarget);
    }

    /**
     * 清理玩家的目标记录（攻击流程结束后调用）。
     */
    public static void cleanup(Player player) {
        TARGETS.remove(player.getStringUUID());
    }
}
