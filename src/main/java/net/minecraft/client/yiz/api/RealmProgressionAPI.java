package net.minecraft.client.yiz.api;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 境界跨度 API — 境界注册、突破触发、属性叠加计算
 * <p>
 * 前置库提供框架，下游模组（yizxian）定义具体境界和突破条件。
 * 当前境界通过 {@link PlayerDataAPI} 持久化到玩家存档。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 初始化时注册境界
 * RealmProgressionAPI.registerStage(new RealmStage(
 *     ResourceLocation.fromNamespaceAndPath("yizxian", "build_destiny"),
 *     0, "筑命", Map.of("attack", 1.20)
 * ));
 * RealmProgressionAPI.registerStage(new RealmStage(
 *     ResourceLocation.fromNamespaceAndPath("yizxian", "realize_self"),
 *     1, "谌我", Map.of("attack", 1.15)
 * ));
 *
 * // 2. 下游模组检测条件成功后触发突破
 * RealmProgressionAPI.breakthrough(player, ResourceLocation.fromNamespaceAndPath("yizxian", "realize_self"));
 *
 * // 3. 获取累加属性倍率
 * Map<String, Double> mods = RealmProgressionAPI.getCumulativeModifiers(player);
 * double atkMult = mods.getOrDefault("attack", 1.0); // 筑命+谌我 = 1.20 * 1.15 = 1.38
 * }</pre>
 */
public final class RealmProgressionAPI {

    private RealmProgressionAPI() {}

    /** PlayerDataAPI 存储键：当前境界的 ResourceLocation 字符串 */
    public static final String DATA_KEY = "yizmodqzk:realm_stage";

    /** 已注册的境界表 */
    private static final Map<ResourceLocation, RealmStage> STAGES = new ConcurrentHashMap<>();

    /** 按 order 排序的境界列表（注册后自动重建） */
    private static volatile List<RealmStage> sortedStages = List.of();

    /** 突破事件监听器列表 */
    private static final List<BiConsumer<ServerPlayer, RealmStage>> breakthroughListeners = new CopyOnWriteArrayList<>();

    // ==================== 注册 ====================

    /**
     * 注册一个境界阶段。
     * 必须在 PlayerDataAPI.register(DATA_KEY, ...) 之后调用。
     */
    public static void registerStage(RealmStage stage) {
        STAGES.put(stage.id(), stage);
        rebuildSortedStages();
    }

    /**
     * 初始化境界存储（在 PlayerDataAPI 注册 DATA_KEY 后调用一次）。
     */
    public static void initDataKey() {
        PlayerDataAPI.register(DATA_KEY, Codec.STRING, "");
    }

    private static void rebuildSortedStages() {
        List<RealmStage> list = new ArrayList<>(STAGES.values());
        list.sort(Comparator.comparingInt(RealmStage::order));
        sortedStages = List.copyOf(list);
    }

    // ==================== 查询 ====================

    /**
     * 获取玩家当前境界，未突破过返回 null，未注册任何境界返回 null。
     */
    public static RealmStage getCurrentStage(Player player) {
        String raw = PlayerDataAPI.get(player, DATA_KEY);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) return null;
        return STAGES.get(id);
    }

    /**
     * 获取指定 ID 的境界，不存在返回 null。
     */
    public static RealmStage getStage(ResourceLocation id) {
        return STAGES.get(id);
    }

    /**
     * 获取起始境界（order 最小者），未注册返回 null。
     */
    public static RealmStage getStartingStage() {
        List<RealmStage> stages = sortedStages;
        return stages.isEmpty() ? null : stages.get(0);
    }

    /**
     * 获取所有境界，按 order 升序排列。
     */
    public static List<RealmStage> getAllStages() {
        return sortedStages;
    }

    /**
     * 获取比当前境界高一级的下一个境界。
     * 无境界时返回起始境界（筑命），已是最高境界返回 null。
     */
    public static RealmStage getNextStage(Player player) {
        RealmStage current = getCurrentStage(player);
        if (current == null) {
            return getStartingStage();
        }
        int nextOrder = current.order() + 1;
        return sortedStages.stream()
            .filter(s -> s.order() == nextOrder)
            .findFirst().orElse(null);
    }

    // ==================== 突破 ====================

    /**
     * 执行境界突破：设置玩家当前境界、触发事件、同步客户端。
     *
     * @param player  目标玩家
     * @param stageId 目标境界 ID（必须是已注册的境界）
     * @return true 突破成功，false 失败（境界不存在或已是当前境界）
     */
    public static boolean breakthrough(ServerPlayer player, ResourceLocation stageId) {
        RealmStage target = STAGES.get(stageId);
        if (target == null) return false;

        RealmStage current = getCurrentStage(player);
        if (current != null && current.id().equals(stageId)) return false;

        // 持久化到玩家存档
        PlayerDataAPI.set(player, DATA_KEY, stageId.toString());

        // 触发事件
        for (BiConsumer<ServerPlayer, RealmStage> listener : breakthroughListeners) {
            try {
                listener.accept(player, target);
            } catch (Exception e) {
                // 单个监听器异常不影响其他监听器
            }
        }

        // 同步到客户端
        sendSync(player);

        return true;
    }

    /**
     * 注册突破事件监听器。
     * 每次突破时回调，可用于播放特效、发送聊天消息等。
     */
    public static void onBreakthrough(BiConsumer<ServerPlayer, RealmStage> listener) {
        breakthroughListeners.add(listener);
    }

    // ==================== 属性叠加 ====================

    /**
     * 获取玩家当前境界及之前所有境界的属性叠加（累积）。
     * <p>
     * 例如：筑命 attack=1.20，谌我 attack=1.15，
     * 突破到谌我后 attack 倍率为 1.20 × 1.15 = 1.38。
     * </p>
     *
     * @return 属性名 → 累积倍率（不含 1.0 的属性不出现）
     */
    public static Map<String, Double> getCumulativeModifiers(Player player) {
        RealmStage current = getCurrentStage(player);
        if (current == null) return Collections.emptyMap();

        Map<String, Double> result = new LinkedHashMap<>();
        for (RealmStage stage : sortedStages) {
            for (Map.Entry<String, Double> entry : stage.attributeModifiers().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), (a, b) -> a * b);
            }
            if (stage.id().equals(current.id())) break;
        }
        return Collections.unmodifiableMap(result);
    }

    // ==================== 网络同步 ====================

    /** 同步回调：由 NetworkHandler 注入 */
    static volatile BiConsumer<ServerPlayer, String> syncCallback = (p, s) -> {};

    public static void setSyncCallback(BiConsumer<ServerPlayer, String> callback) {
        syncCallback = callback != null ? callback : (p, s) -> {};
    }

    private static void sendSync(ServerPlayer player) {
        String stageId = PlayerDataAPI.get(player, DATA_KEY);
        if (stageId != null && !stageId.isEmpty()) {
            syncCallback.accept(player, stageId);
        }
    }

    /**
     * 向客户端同步当前境界（登录/重生时调用）。
     */
    public static void syncToClient(ServerPlayer player) {
        sendSync(player);
    }
}
