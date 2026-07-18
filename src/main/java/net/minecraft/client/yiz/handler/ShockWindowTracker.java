package net.minecraft.client.yiz.handler;

import net.minecraft.client.yiz.core.StatusEffectDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 感电窗口追踪器 — 奔雷疾等感电技能释放后，在持续时间内每次攻击对目标施加感电，
 * 使其成为链式闪电源头向周围扩散。
 *
 * <h3>窗口语义</h3>
 * <ul>
 *   <li>释放技能 → {@link #start} 开启窗口（如 4 秒 = 80 tick）</li>
 *   <li>窗口期内玩家每次攻击 → {@link #tryShock} 对目标施加感电</li>
 *   <li>被标记的目标自动成为感电中心，周期性向周围释放链式闪电 + 范围伤害</li>
 *   <li>伤害 = 技能面板公式（damage_base + spell_power × damage_spell_coeff/100）</li>
 * </ul>
 */
public final class ShockWindowTracker {

    private ShockWindowTracker() {}

    private record Window(long endTick, float dmg, float range, int shockTicks, int interval) {}

    private static final ConcurrentHashMap<UUID, Window> WINDOWS = new ConcurrentHashMap<>();

    /**
     * 开启感电窗口。
     *
     * @param player     施法玩家
     * @param dmg        单次感电伤害（技能公式值）
     * @param range      链式闪电范围（格）
     * @param windowTicks 窗口持续 tick（如 80 = 4 秒）
     * @param interval   感电 AoE 间隔 tick
     */
    public static void start(Player player, float dmg, float range, int windowTicks, int interval) {
        if (player.level().isClientSide()) return;
        long endTick = player.level().getGameTime() + windowTicks;
        WINDOWS.put(player.getUUID(), new Window(endTick, dmg, range, windowTicks, interval));
    }

    /**
     * 攻击时尝试对目标施加感电。窗口到期自动清理。
     *
     * @return true 表示成功施加感电（窗口有效且目标合法）
     */
    public static boolean tryShock(Player player, LivingEntity target) {
        if (player.level().isClientSide()) return false;
        Window w = WINDOWS.get(player.getUUID());
        if (w == null) return false;
        if (player.level().getGameTime() > w.endTick) {
            WINDOWS.remove(player.getUUID());
            return false;
        }
        if (target == player) return false; // 不自伤
        // 对目标施加感电：目标成为链式闪电中心，向周围实体扩散
        StatusEffectDispatcher.applyShockWithDamage(
            target, player, w.dmg, w.range, w.shockTicks, w.interval);
        return true;
    }

    /** 窗口是否仍有效。 */
    public static boolean isActive(Player player) {
        Window w = WINDOWS.get(player.getUUID());
        if (w == null) return false;
        if (player.level().getGameTime() > w.endTick) {
            WINDOWS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** 手动结束窗口（如玩家死亡/切换世界）。 */
    public static void clear(Player player) {
        WINDOWS.remove(player.getUUID());
    }
}
