package net.minecraft.client.yiz.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能配置界面容器的服务端存储（v2: 430×200 新布局）。
 * <p>内存容器 + PlayerDataAPI 持久化。</p>
 */
public final class SkillConfigStorage {

    private SkillConfigStorage() {}

    public record Data(
        SimpleContainer skillUpgrade,   // B: 技能升级槽 ×1
        SimpleContainer bigLoad,        // F: 大装载槽 ×1
        SimpleContainer skillLoad,      // F: 技能装载槽 ×3
        SimpleContainer passiveLoad,    // F: 被动装载槽 ×3
        SimpleContainer skillLibrary    // G: 技能库 ×20
    ) {}

    private static final String PERSIST_KEY = "yizmodqzk:skill_config_slots";
    private static final Map<UUID, Data> STORE = new ConcurrentHashMap<>();
    private static final String ENHANCE_NBT_KEY = "yiz:enhance";

    /** 从物品 CUSTOM_DATA 读取加强等级。每物品实例独立。 */
    public static int[] getEnhanceLevels(ItemStack stack) {
        if (stack.isEmpty()) return new int[6];
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(ENHANCE_NBT_KEY)) return new int[6];
        return tag.getIntArray(ENHANCE_NBT_KEY);
    }

    /** 写入物品 CUSTOM_DATA：设置单个槽位加强等级。 */
    public static void setEnhanceLevel(ItemStack stack, int slot, int level) {
        if (stack.isEmpty()) return;
        int[] levels = getEnhanceLevels(stack);
        if (levels.length < 6) levels = new int[6];
        if (slot >= 0 && slot < levels.length) levels[slot] = Math.max(0, level);
        final int[] finalLevels = levels;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putIntArray(ENHANCE_NBT_KEY, finalLevels));
    }

    /** 兼容旧 API：通过 ItemStack 查等级。 */
    public static int getEnhanceLevel(ItemStack stack, int slot) {
        int[] levels = getEnhanceLevels(stack);
        return slot >= 0 && slot < levels.length ? levels[slot] : 0;
    }

    public static Data getOrCreate(UUID playerId) {
        return STORE.computeIfAbsent(playerId, k -> new Data(
            new SimpleContainer(1), new SimpleContainer(1),
            new SimpleContainer(3), new SimpleContainer(3),
            new SimpleContainer(20)));
    }

    public static Data get(UUID playerId) { return STORE.get(playerId); }

    // ── 持久化 ──

    /** 保存全部容器到 PlayerDataAPI（Menu 关闭时调用）。 */
    public static void saveToPlayerData(Player player, Data data) {
        JsonObject root = new JsonObject();
        root.addProperty("skill_upgrade", serialize(data.skillUpgrade.getItem(0), player));
        root.addProperty("big_load", serialize(data.bigLoad.getItem(0), player));
        root.add("skill_load", serializeContainer(data.skillLoad, 3, player));
        root.add("passive_load", serializeContainer(data.passiveLoad, 3, player));
        root.add("library", serializeContainer(data.skillLibrary, 20, player));
        // 加强等级现已存储在物品 NBT 中，随容器自动持久化
        PlayerDataAPI.set(player, PERSIST_KEY, root.toString());
    }

    /** 从 PlayerDataAPI 恢复容器内容（Menu 打开时调用）。 */
    public static void loadFromPlayerData(Player player, Data data) {
        try {
            String raw = PlayerDataAPI.get(player, PERSIST_KEY);
            if (raw == null || raw.isEmpty()) return;
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            deserializeInto(root, "skill_upgrade", data.skillUpgrade, 0, player);
            deserializeInto(root, "big_load", data.bigLoad, 0, player);
            deserializeContainerInto(root, "skill_load", data.skillLoad, 3, player);
            deserializeContainerInto(root, "passive_load", data.passiveLoad, 3, player);
            deserializeContainerInto(root, "library", data.skillLibrary, 20, player);
            // 加强等级已随物品 NBT 自动恢复
        } catch (Exception ignored) {}
    }

    private static String serialize(ItemStack stack, Player player) {
        if (stack.isEmpty()) return "";
        return stack.save(player.registryAccess()).toString();
    }

    private static JsonArray serializeContainer(SimpleContainer c, int size, Player player) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < size; i++)
            arr.add(serialize(c.getItem(i), player));
        return arr;
    }

    private static void deserializeInto(JsonObject root, String key, SimpleContainer c, int slot, Player player) {
        if (!root.has(key)) return;
        String snbt = root.get(key).getAsString();
        if (snbt.isEmpty()) return;
        try {
            ItemStack.parse(player.registryAccess(), TagParser.parseTag(snbt))
                .ifPresent(s -> c.setItem(slot, s));
        } catch (Exception ignored) {}
    }

    private static void deserializeContainerInto(JsonObject root, String key, SimpleContainer c, int size, Player player) {
        if (!root.has(key)) return;
        JsonArray arr = root.getAsJsonArray(key);
        for (int i = 0; i < Math.min(size, arr.size()); i++) {
            String snbt = arr.get(i).getAsString();
            if (snbt.isEmpty()) continue;
            try {
                final int idx = i;
                ItemStack.parse(player.registryAccess(), TagParser.parseTag(snbt))
                    .ifPresent(s -> c.setItem(idx, s));
            } catch (Exception ignored) {}
        }
    }
}
