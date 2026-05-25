package net.minecraft.client.yiz.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * 道宫 API — 落点管理、方块放置、范围计算、属性增益。
 * <p>
 * 前置库提供框架，下游模组（yizxian）负责落点激活逻辑（R=10 检测）和面板 UI。
 * 数据通过 {@link PlayerDataAPI} 持久化到玩家存档。
 * </p>
 *
 * <h3>核心公式</h3>
 * <ul>
 *   <li>影响力范围 = 边长 L × 4^落点总数</li>
 *   <li>扩展成本 = [(L+2)³ − L³] / (10 × 落点数²)</li>
 *   <li>终端衰减 = max(0.1, 1.0 − 距离/最大范围)</li>
 * </ul>
 */
public final class DaoPalaceAPI {

    private DaoPalaceAPI() {}

    /** PlayerDataAPI 存储键 */
    public static final String DATA_KEY = "yizmodqzk:dao_palaces";

    /** 玩家 → 道宫列表（并发安全） */
    private static final Map<UUID, List<DaoPalace>> playerPalaces = new ConcurrentHashMap<>();

    /** 道宫变更监听器 */
    private static final List<BiConsumer<ServerPlayer, DaoPalace>> palaceListeners = new CopyOnWriteArrayList<>();

    /** 网络同步回调 */
    static volatile BiConsumer<ServerPlayer, List<DaoPalace>> syncCallback = (p, l) -> {};

    // ==================== 初始化 ====================

    /**
     * 注册持久化数据键和编解码器。
     * 必须在模组构造器中尽早调用。
     */
    public static void initDataKey() {
        PlayerDataAPI.register(DATA_KEY, DaoPalaceListCodec.CODEC, Collections.emptyList());
    }

    // ==================== 落点管理 ====================

    /**
     * 激活一个新落点（建造一座新道宫）。
     *
     * @param player     目标玩家
     * @param anchorId   落点标识（如 "yizxian:first_palace"）
     * @param centerPos  道宫中心坐标
     * @param totalAnchors 该玩家当前落点总数（含新激活的）
     * @return 新创建的道宫
     */
    public static DaoPalace activateAnchor(ServerPlayer player, ResourceLocation anchorId,
                                            BlockPos centerPos, int totalAnchors) {
        DaoPalace palace = new DaoPalace(
            anchorId, centerPos, 1, Collections.emptySet(), totalAnchors
        );
        List<DaoPalace> palaces = getPalacesInternal(player);
        palaces.add(palace);
        save(player, palaces);
        notifyListeners(player, palace);
        sendSync(player);
        return palace;
    }

    /**
     * 获取玩家的所有道宫。
     */
    public static List<DaoPalace> getPalaces(Player player) {
        return Collections.unmodifiableList(getPalacesInternal(player));
    }

    /**
     * 获取玩家指定落点的道宫，不存在返回 null。
     */
    public static DaoPalace getPalace(Player player, ResourceLocation anchorId) {
        return getPalacesInternal(player).stream()
            .filter(p -> p.anchorId().equals(anchorId))
            .findFirst().orElse(null);
    }

    // ==================== 方块管理 ====================

    /**
     * 检查方块是否在道宫的正方体范围内（±L）。
     */
    public static boolean canPlaceBlock(Player player, BlockPos pos) {
        for (DaoPalace palace : getPalacesInternal(player)) {
            BlockPos center = palace.centerPos();
            int d = palace.sideLength();
            if (Math.abs(pos.getX() - center.getX()) <= d
                && Math.abs(pos.getY() - center.getY()) <= d
                && Math.abs(pos.getZ() - center.getZ()) <= d) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在道宫中放置一个方块（自动归属到包含此坐标的道宫）。
     * 方块数达标时自动触发边长进阶。
     *
     * @return 操作后的道宫（可能已进阶），不在范围内返回 null
     */
    public static DaoPalace placeBlock(ServerPlayer player, BlockPos pos) {
        List<DaoPalace> palaces = getPalacesInternal(player);
        for (int i = 0; i < palaces.size(); i++) {
            DaoPalace palace = palaces.get(i);
            BlockPos center = palace.centerPos();
            int d = palace.sideLength();
            if (Math.abs(pos.getX() - center.getX()) <= d
                && Math.abs(pos.getY() - center.getY()) <= d
                && Math.abs(pos.getZ() - center.getZ()) <= d) {

                if (palace.placedBlocks().contains(pos)) return palace; // 已放置

                DaoPalace updated = palace.withBlock(pos);
                palaces.set(i, updated);
                save(player, palaces);

                // 自动进阶
                while (updated.currentVolume() >= updated.costToExpand()) {
                    updated = updated.withSideLength(updated.sideLength() + 2);
                    palaces.set(i, updated);
                    save(player, palaces);
                }

                notifyListeners(player, updated);
                sendSync(player);
                return updated;
            }
        }
        return null;
    }

    // ==================== 属性增益 ====================

    /**
     * 获取玩家所有道宫叠加后的属性增益。
     * <p>
     * 每座道宫独立计算：终端衰减 × 边长加成，然后各道宫之和。
     * 边长加成 = 1.0 + sideLength × 0.02（每边长 +2%）
     * </p>
     *
     * @param playerPos 玩家当前坐标（用于计算距离衰减）
     * @return {attack, defense, speed} 等属性的加值
     */
    public static Map<String, Double> getStatGains(Player player, BlockPos playerPos) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (DaoPalace palace : getPalacesInternal(player)) {
            double distance = Math.sqrt(playerPos.distSqr(palace.centerPos()));
            double maxRange = palace.influenceRange();
            double decay = Math.max(0.1, 1.0 - distance / maxRange);
            double sideBonus = 1.0 + palace.sideLength() * 0.02;

            result.merge("attack", decay * sideBonus, Double::sum);
            result.merge("defense", decay * sideBonus * 0.8, Double::sum);
            result.merge("speed", decay * sideBonus * 0.5, Double::sum);
        }
        return result;
    }

    /**
     * 获取指定道宫的属性增益（终端衰减）。
     *
     * @param palace    目标道宫
     * @param playerPos 玩家当前坐标
     * @param baseStat  基础属性值
     * @return 衰减后的属性值（baseStat × decay）
     */
    public static double getStatGain(DaoPalace palace, BlockPos playerPos, double baseStat) {
        double distance = Math.sqrt(playerPos.distSqr(palace.centerPos()));
        double maxRange = palace.influenceRange();
        double decay = Math.max(0.1, 1.0 - distance / maxRange);
        return baseStat * decay;
    }

    // ==================== 事件监听 ====================

    /**
     * 注册道宫变更监听器（方块放置、进阶时触发）。
     */
    public static void onChange(BiConsumer<ServerPlayer, DaoPalace> listener) {
        palaceListeners.add(listener);
    }

    // ==================== 网络同步 ====================

    public static void setSyncCallback(BiConsumer<ServerPlayer, List<DaoPalace>> callback) {
        syncCallback = callback != null ? callback : (p, l) -> {};
    }

    /**
     * 向客户端同步所有道宫数据。
     */
    public static void syncToClient(ServerPlayer player) {
        sendSync(player);
    }

    // ==================== 内部方法 ====================

    @SuppressWarnings("unchecked")
    private static List<DaoPalace> getPalacesInternal(Player player) {
        return playerPalaces.computeIfAbsent(player.getUUID(), uuid -> {
            Object stored = PlayerDataAPI.get(player, DATA_KEY);
            if (stored instanceof List<?> list) {
                List<DaoPalace> palaces = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof DaoPalace dp) palaces.add(dp);
                }
                return palaces;
            }
            return new ArrayList<>();
        });
    }

    private static void save(ServerPlayer player, List<DaoPalace> palaces) {
        playerPalaces.put(player.getUUID(), new ArrayList<>(palaces));
        PlayerDataAPI.set(player, DATA_KEY, new ArrayList<>(palaces));
    }

    private static void notifyListeners(ServerPlayer player, DaoPalace palace) {
        for (BiConsumer<ServerPlayer, DaoPalace> listener : palaceListeners) {
            try {
                listener.accept(player, palace);
            } catch (Exception ignored) {}
        }
    }

    private static void sendSync(ServerPlayer player) {
        syncCallback.accept(player, getPalacesInternal(player));
    }

    // ==================== 编解码器 ====================

    /**
     * List&lt;DaoPalace&gt; 的 Codec，用于 PlayerDataAPI 持久化。
     */
    private static final class DaoPalaceListCodec {
        static final Codec<BlockPos> BLOCKPOS_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
            Codec.INT.fieldOf("y").forGetter(BlockPos::getY),
            Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)
        ).apply(inst, BlockPos::new));

        static final Codec<DaoPalace> PALACE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("anchorId").forGetter(DaoPalace::anchorId),
            BLOCKPOS_CODEC.fieldOf("centerPos").forGetter(DaoPalace::centerPos),
            Codec.INT.fieldOf("sideLength").forGetter(DaoPalace::sideLength),
            BLOCKPOS_CODEC.listOf().fieldOf("placedBlocks").forGetter(p -> new ArrayList<>(p.placedBlocks())),
            Codec.INT.fieldOf("totalAnchors").forGetter(DaoPalace::totalAnchors)
        ).apply(inst, (id, pos, len, blocks, anchors) ->
            new DaoPalace(id, pos, len, new HashSet<>(blocks), anchors)
        ));

        static final Codec<List<DaoPalace>> CODEC = PALACE_CODEC.listOf();
    }
}
