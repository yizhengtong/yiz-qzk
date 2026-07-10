package net.minecraft.client.yiz.core;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 创造模式自动保护 + 重生 3 秒无敌。
 *
 * <p>自动保护与 {@code /yiz th} 手动切换共存：
 * <ul>
 *   <li>进入创造 → 自动开保护（标记 "creative"）</li>
 *   <li>离开创造 → 仅自动开的关掉，手动开的不动</li>
 *   <li>重生 → 自动开保护 3 秒（标记 "respawn:timestamp"）</li>
 *   <li>手动 /yiz th 关掉 → 同时清除自动标记（用户明确不需要保护）</li>
 * </ul>
 */
public final class CreativeProtectionHandler {

    /** UUID → auto reason */
    private static final Map<UUID, String> AUTO_REASONS = new ConcurrentHashMap<>();

    /** 快速重生标记 —— 服务端收到 C2SFastRespawnPayload 后暂存，下次 onPlayerRespawn 时消费 */
    private static final java.util.Set<UUID> FAST_RESPAWN_MARKERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 快速重生保护时长（毫秒） */
    public static final long FAST_RESPAWN_DURATION_MS = 30_000L;

    private CreativeProtectionHandler() {}

    // ══════════════════════════════════════════════════════════════
    //  每 tick 检查
    // ══════════════════════════════════════════════════════════════

    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();
        boolean configOn = CreativeProtectionConfig.isCreativeProtectionEnabled();
        boolean isCreative = player.isCreative();
        boolean isProtected = PlayerClassSwapper.isProtected(player);
        String autoReason = AUTO_REASONS.get(uuid);

        // 创造模式自动保护（配置开启时）
        if (configOn && isCreative && !isProtected) {
            PlayerClassSwapper.enableProtection(player);
            AUTO_REASONS.put(uuid, "creative");
        }

        // 关闭自动保护：① 离开创造 ② 配置文件关闭时
        if ("creative".equals(autoReason)) {
            if (!isCreative || !configOn) {
                PlayerClassSwapper.disableProtection(player);
                AUTO_REASONS.remove(uuid);
            }
        }

        // 重生无敌过期检查（respawn:expiryMs）
        if (autoReason != null && autoReason.startsWith("respawn:")) {
            try {
                long expiryMs = Long.parseLong(autoReason.substring(8));
                if (System.currentTimeMillis() >= expiryMs) {
                    PlayerClassSwapper.disableProtection(player);
                    AUTO_REASONS.remove(uuid);
                }
            } catch (NumberFormatException e) {
                AUTO_REASONS.remove(uuid);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  重生事件
    // ══════════════════════════════════════════════════════════════

    /** 服务端接收快速重生请求后调用 —— 标记此 UUID 下一次重生应获得 30s 保护。 */
    public static void markFastRespawn(UUID uuid) {
        FAST_RESPAWN_MARKERS.add(uuid);
    }

    /** 玩家重生后调用 —— 快速重生给 30 秒，普通重生给 3 秒。 */
    public static void onPlayerRespawn(Player player) {
        if (player.level().isClientSide()) return;
        if (PlayerClassSwapper.isProtected(player)) return; // 已在保护态（创造/手动）

        UUID uuid = player.getUUID();
        long durationMs = FAST_RESPAWN_MARKERS.remove(uuid) ? FAST_RESPAWN_DURATION_MS : 3_000L;

        PlayerClassSwapper.enableProtection(player);
        long expiry = System.currentTimeMillis() + durationMs;
        AUTO_REASONS.put(uuid, "respawn:" + expiry);
    }

    // ══════════════════════════════════════════════════════════════
    //  手动切换回调
    // ══════════════════════════════════════════════════════════════

    /** 玩家通过 /yiz th 手动切换保护态后调用。 */
    public static void onManualToggle(Player player, boolean nowProtected) {
        if (!nowProtected) {
            // 手动关 → 清除自动标记（用户明确不需要保护）
            AUTO_REASONS.remove(player.getUUID());
        }
        // 手动开 → 不管自动标记，保持原样
    }

    /** 查询玩家是否处于自动保护中。 */
    public static boolean isAutoProtected(Player player) {
        return AUTO_REASONS.containsKey(player.getUUID());
    }
}
