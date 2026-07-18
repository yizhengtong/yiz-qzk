package net.minecraft.client.yiz.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * transient 效果统一清理表。
 *
 * <p><b>核心原则</b>（用户提出）：技能/被动提供的任何状态或效果，默认随「卸载」自动解除。
 * 效果的存活期 ≤ 提供它的载体的存活期。只有明确定义为「永久属性」的才在卸载后保留。</p>
 *
 * <p>每个 transient 效果在产生时，向本表登记一条「来源 → 清理回调」记录。
 * 载体（技能/被动/标签来源）被卸载时，框架按来源批量执行清理回调。</p>
 *
 * <h3>来源 key 命名约定</h3>
 * <ul>
 *   <li>{@code "passive:<regName>"} — 被动物品（如 passive:tianleiyin）</li>
 *   <li>{@code "skill:<regName>"}    — 主动技能（如 skill:leimingdianjia）</li>
 *   <li>{@code "tag:<tagKey>"}       — 强化标签（如 tag:pili）</li>
 * </ul>
 *
 * <h3>触发方式（混合）</h3>
 * <ul>
 *   <li><b>钩子为主</b>：{@code SkillConfigMenu.removed()} 关闭装配界面时对比装载槽快照，
 *       对被换走的物品调 {@link #clearSource}。</li>
 *   <li><b>主动兜底</b>：各 transient 效果 tick 时用 {@link EffectSources} 校验来源仍在，
 *       不在则自清（防死亡掉落/物品销毁/重启残留）。</li>
 * </ul>
 */
public final class EffectRegistry {

    private EffectRegistry() {}

    /** 玩家UUID → (来源key → 清理回调列表)。一个来源可挂多个清理回调。 */
    private static final Map<UUID, Map<String, List<Runnable>>> ENTRIES = new ConcurrentHashMap<>();

    /**
     * 登记一个清理回调。
     * <p>同一来源可多次登记（每次产生效果时登记对应的清理）。清理回调在 {@link #clearSource} 时按登记顺序执行。</p>
     *
     * @param player    玩家 UUID
     * @param sourceKey 来源 key（passive:/skill:/tag: 前缀）
     * @param cleanup   清理回调（如 removeModifier / 清 PersistentData）
     */
    public static void register(UUID player, String sourceKey, Runnable cleanup) {
        ENTRIES.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
               .computeIfAbsent(sourceKey, k -> new CopyOnWriteArrayList<>())
               .add(cleanup);
    }

    /**
     * 清理指定来源的全部效果：执行该来源所有清理回调并移除登记。
     * <p>卸载技能/被动/标签来源时调用。每个回调独立 try-catch，一个失败不影响其他。</p>
     */
    public static void clearSource(UUID player, String sourceKey) {
        Map<String, List<Runnable>> sources = ENTRIES.get(player);
        if (sources == null) return;
        List<Runnable> cleanups = sources.remove(sourceKey);
        if (cleanups != null) {
            for (Runnable r : cleanups) {
                try { r.run(); }
                catch (Exception e) {
                    // 清理失败不应阻断其他清理，记录即可（避免日志噪音用 stderr）
                    System.err.println("[EffectRegistry] cleanup failed for " + sourceKey + ": " + e);
                }
            }
        }
    }

    /**
     * 清理玩家的全部 transient 效果（死亡彻底重置 / 退出时用）。
     */
    public static void clearAll(UUID player) {
        Map<String, List<Runnable>> sources = ENTRIES.remove(player);
        if (sources == null) return;
        for (Map.Entry<String, List<Runnable>> e : sources.entrySet()) {
            for (Runnable r : e.getValue()) {
                try { r.run(); }
                catch (Exception ex) {
                    System.err.println("[EffectRegistry] cleanup failed for " + e.getKey() + ": " + ex);
                }
            }
        }
    }

    /** 调试用：某来源是否有登记。 */
    public static boolean hasSource(UUID player, String sourceKey) {
        Map<String, List<Runnable>> sources = ENTRIES.get(player);
        return sources != null && sources.containsKey(sourceKey);
    }
}
