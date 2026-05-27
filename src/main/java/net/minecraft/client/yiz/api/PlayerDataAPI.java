package net.minecraft.client.yiz.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.tizMod;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 通用玩家数据附件 API
 * <p>
 * 为下游模组提供类型安全的玩家数据存储，自动持久化。
 * 所有数据默认持久化到玩家 NBT 存档文件。
 * 需要临时数据（不持久化）时调用 {@link #discard(Player, String)}。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 1. 初始化时注册数据条目
 * PlayerDataAPI.register("mymod:star_level", Codec.INT, 0);
 *
 * // 2. 读写数据
 * int level = PlayerDataAPI.get(player, "mymod:star_level");
 * PlayerDataAPI.set(player, "mymod:star_level", 5);
 *
 * // 3. 丢弃（恢复为默认值，下次存档不再保存）
 * PlayerDataAPI.discard(player, "mymod:star_level");
 * PlayerDataAPI.discardAll(player);
 * }</pre>
 */
// 大白话: 玩家数据方法
public final class PlayerDataAPI {

    private PlayerDataAPI() {}

    private static final Map<String, Entry<?>> REGISTRY = new ConcurrentHashMap<>();

    /** 数据变更同步到客户端的回调，由 NetworkHandler 在启动时注入 */
    static volatile java.util.function.BiConsumer<Player, String> onDataSet = (p, d) -> {};

    /**
     * 设置数据变更同步回调（由 NetworkHandler 在初始化时调用）。
     */
    public static void setSyncCallback(java.util.function.BiConsumer<Player, String> callback) {
        onDataSet = callback != null ? callback : (p, d) -> {};
    }

    // ==================== 注册 ====================

    /**
     * 注册一个玩家数据条目。
     *
     * @param key          唯一标识（建议用 modid:name 格式）
     * @param codec        该类型的序列化编解码器
     * @param defaultValue 默认值（玩家不存在该数据时返回此值）
     * @param <T>          数据类型
     */
    public static <T> void register(String key, Codec<T> codec, T defaultValue) {
        REGISTRY.put(key, new Entry<>(codec, defaultValue));
    }

    @SuppressWarnings("unchecked")
    private static <T> Entry<T> getEntry(String key) {
        Entry<T> entry = (Entry<T>) REGISTRY.get(key);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown PlayerData key: " + key);
        }
        return entry;
    }

    // ==================== 读写 ====================

    /**
     * 获取玩家指定键的数据，不存在时返回默认值。
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Player player, String key) {
        Entry<T> entry = getEntry(key);
        CompoundTag root = getRoot(player);
        if (root.contains(key)) {
            Tag tag = root.get(key);
            DataResult<Pair<T, Tag>> result = entry.codec().decode(NbtOps.INSTANCE, tag);
            Optional<Pair<T, Tag>> pair = result.result();
            if (pair.isPresent()) {
                return pair.get().getFirst();
            }
        }
        return entry.defaultValue();
    }

    /**
     * 设置玩家指定键的数据（自动标记为持久化）。
     */
    public static <T> void set(Player player, String key, T value) {
        Entry<T> entry = getEntry(key);
        CompoundTag root = getRoot(player);
        entry.codec().encodeStart(NbtOps.INSTANCE, value)
                .result()
                .ifPresent(tag -> root.put(key, tag));
        setRoot(player, root);
        onDataSet.accept(player, root.isEmpty() ? "{}" : root.toString());
    }

    // ==================== 丢弃 ====================

    /**
     * 丢弃指定键的数据：从 NBT 中移除，下次存档不再保存，
     * {@link #get} 将返回默认值。
     */
    public static void discard(Player player, String key) {
        CompoundTag root = getRoot(player);
        if (root.contains(key)) {
            root.remove(key);
            setRoot(player, root);
        }
    }

    /**
     * 丢弃该玩家所有已注册键的数据，完全重置为默认状态。
     */
    public static void discardAll(Player player) {
        setRoot(player, new CompoundTag());
    }

    // ==================== 内部持久化 ====================

    /** 用于持久化的 AttachmentType key，由 yiz1.21.1 的 ModAttachments 注册 */
    static final String ATTACHMENT_KEY = "player_data";

    private static CompoundTag getRoot(Player player) {
        String raw = player.getData(
                net.minecraft.client.yiz.core.registry.ModAttachments.PLAYER_DATA_ATTACHMENT.get()
        );
        if (raw == null || raw.isEmpty() || raw.equals("{}")) {
            return new CompoundTag();
        }
        try {
            return net.minecraft.nbt.TagParser.parseTag(raw);
        } catch (Exception e) {
            tizMod.LOGGER.warn("Failed to parse player data NBT for {}, resetting: {}", player.getName().getString(), e.getMessage());
            return new CompoundTag();
        }
    }

    private static void setRoot(Player player, CompoundTag tag) {
        player.setData(
                net.minecraft.client.yiz.core.registry.ModAttachments.PLAYER_DATA_ATTACHMENT.get(),
                tag.isEmpty() ? "{}" : tag.toString()
        );
    }

    // ==================== 内部类型 ====================

    private record Entry<T>(Codec<T> codec, T defaultValue) {}
}
