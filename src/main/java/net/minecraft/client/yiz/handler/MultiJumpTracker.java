package net.minecraft.client.yiz.handler;

import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多段跳追踪器 — 消费 {@link YizAttributes#JUMP_COUNT} + {@link YizAttributes#JUMP_HEIGHT}（仅玩家）。
 *
 * <p>yizmodqzk 版（阶段3A），替代 yizxian {@code ExtraJumpData}。<b>单源模型</b>：
 * 总跳跃次数 = JUMP_COUNT（EquipmentAttributeSync 每 tick 汇总全槽位），单一 int，
 * 不再像 yizxian 按饰品槽位数组。</p>
 *
 * <h3>数据流</h3>
 * <ul>
 *   <li>客户端：跳跃键按下（空中）→ 乐观预测 Y 初速 + 发 C2S 消耗请求</li>
 *   <li>服务端：tryConsume 权威消耗（剩余次数 -1）→ PlayerDataAPI 自动 S2C 同步纠正客户端</li>
 *   <li>落地充能：recharge（剩余 = JUMP_COUNT 满值），由事件处理器调用</li>
 * </ul>
 *
 * <h3>物理反解（复刻 ExtraJumpData）</h3>
 * <p>{@link #velocityFromHeight(int)} 把跳跃高度（格）反解为 Y 初速。MC 垂直方程
 * {@code v=(v-0.08)*0.98}，二分法 80 轮 + 缓存。校准：4→0.803、5→0.910、7→1.099。</p>
 */
public final class MultiJumpTracker {

    private MultiJumpTracker() {}

    /** 剩余多段跳次数（PlayerDataAPI 存储，服务端权威，自动 S2C 同步）。 */
    public static final String KEY_REMAINING = "yizmodqzk:multijump_remaining";

    /** 多段跳内置 CD（tick，5 = 0.25 秒），防长按快速消耗。纯客户端。 */
    public static final int JUMP_COOLDOWN_TICKS = 5;

    /** 默认跳跃高度（格），JUMP_HEIGHT 属性未声明时回退。 */
    private static final int DEFAULT_JUMP_HEIGHT = 4;

    // ── PlayerDataAPI 注册（tizMod 调用一次）──

    public static void register() {
        PlayerDataAPI.register(KEY_REMAINING, com.mojang.serialization.Codec.INT, 0);
    }

    // ── 跳跃物理：高度 → Y 初速（反解 + 缓存）──────────────────

    /** heightBlocks → Y 初速 缓存（避免每次跳跃重复二分反解）。 */
    private static final Map<Integer, Float> HEIGHT_TO_VELOCITY = new ConcurrentHashMap<>();

    /** 由跳跃高度（格）反解 Y 向初速。 */
    public static float velocityFromHeight(int heightBlocks) {
        if (heightBlocks <= 0) return 0f;
        return HEIGHT_TO_VELOCITY.computeIfAbsent(heightBlocks, MultiJumpTracker::solveVelocity);
    }

    /** 二分反解：找 v0 使模拟高度 ≈ target。 */
    private static float solveVelocity(int target) {
        double lo = 0.05, hi = 3.0;
        for (int i = 0; i < 80; i++) {
            double mid = (lo + hi) / 2;
            if (simulateHeight(mid) < target) lo = mid; else hi = mid;
        }
        return (float) ((lo + hi) / 2);
    }

    /** 模拟 MC 垂直运动累加位移，返回总上升高度（格）。 */
    private static double simulateHeight(double v0) {
        double v = v0, total = 0;
        int t = 0;
        while (v > 0 && t < 400) {
            total += v;
            v = (v - 0.08) * 0.98;
            t++;
        }
        return total;
    }

    // ── 属性查询（YizAttributes 单源）──────────────────────────

    /** 最大跳跃次数（满值）= JUMP_COUNT 属性值。 */
    public static int maxJumps(Player player) {
        var inst = player.getAttribute(YizAttributes.JUMP_COUNT);
        if (inst == null) return 0;
        int v = (int) inst.getValue();
        return Math.max(0, v);
    }

    /** 每次跳跃高度（格）= JUMP_HEIGHT 属性值，无则默认 4。 */
    public static int jumpHeight(Player player) {
        var inst = player.getAttribute(YizAttributes.JUMP_HEIGHT);
        if (inst == null) return DEFAULT_JUMP_HEIGHT;
        int v = (int) inst.getValue();
        return v > 0 ? v : DEFAULT_JUMP_HEIGHT;
    }

    // ── 剩余次数（服务端权威，PlayerDataAPI 同步）──────────────

    /** 当前剩余多段跳次数。 */
    public static int getRemaining(Player player) {
        Integer v = PlayerDataAPI.get(player, KEY_REMAINING);
        return v != null ? v : 0;
    }

    /** 服务端写入剩余次数，自动 S2C 同步。 */
    public static void setRemaining(Player player, int remaining) {
        PlayerDataAPI.set(player, KEY_REMAINING, Math.max(0, remaining));
    }

    /** 客户端只读：是否还能多段跳。 */
    public static boolean hasJump(Player player) {
        return getRemaining(player) > 0 && maxJumps(player) > 0;
    }

    /**
     * 服务端权威消耗一次。<b>仅服务端调用</b>（C2S 包处理里）。
     * @return true = 消耗成功
     */
    public static boolean tryConsume(Player player) {
        if (getRemaining(player) <= 0) return false;
        setRemaining(player, getRemaining(player) - 1);
        return true;
    }

    /** 落地充能：剩余 = JUMP_COUNT 满值。由 ExtraJumpHandler 等价事件调用。 */
    public static void recharge(Player player) {
        int full = maxJumps(player);
        if (getRemaining(player) != full) setRemaining(player, full);
    }

    /**
     * 每 tick 服务端调用：空中 cap（剩余不超过当前装备 JUMP_COUNT 满值）。
     * 处理装备变动：卸下跳跃装备 → cap 到 0。
     */
    public static void tickCap(Player player) {
        int cur = getRemaining(player);
        int full = maxJumps(player);
        if (cur > full) setRemaining(player, full);
    }

    /** 玩家下线清理（防残留，可选）。 */
    public static void clear(Player player) {
        // PlayerDataAPI 持久化，不主动清；下线后数据保留属正常
    }
}
