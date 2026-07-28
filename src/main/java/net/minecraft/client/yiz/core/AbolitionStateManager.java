package net.minecraft.client.yiz.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品废除 + 背包废除 中央状态管理器（带文件持久化）。
 *
 * <p>状态保存到 {@code config/yizmodqzk-abolish.json}，游戏重启后自动恢复。
 * 属于<b>全局创作偏好</b>（非世界隔离）：存到游戏目录而非存档目录，
 * 刻意跨所有世界/存档保留 —— 玩家在任意存档废除的物品，换档后仍保持废除。</p>
 */
public final class AbolitionStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AbolitionState");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "yizmodqzk-abolish.json";

    /** 已废除的物品 ID 集合 */
    private static final Set<ResourceLocation> ABOLISHED_ITEMS = ConcurrentHashMap.newKeySet();

    /** 护甲废除全局开关 */
    private static volatile boolean ARMOR_ABOLISHED = false;

    /** 是否已从文件加载 */
    private static volatile boolean loaded = false;

    private AbolitionStateManager() {}

    // ══════════════════════════════════════════════════════════
    //  持久化
    // ══════════════════════════════════════════════════════════

    private static File getSaveFile() {
        try {
            Minecraft mc = Minecraft.getInstance();
            File dir = mc.gameDirectory;
            return new File(dir, "config/" + FILE_NAME);
        } catch (Exception e) {
            return new File(FILE_NAME); // fallback
        }
    }

    /** 从文件加载状态（仅在第一次调用时执行） */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        File file = getSaveFile();
        if (!file.exists()) {
            LOGGER.info("No saved abolition state found at {}", file);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            // 读取 JSON 对象 {"abolished":["mod:item1","mod:item2"],"armor":true}
            Map<String, Object> data = GSON.fromJson(reader, Map.class);
            if (data == null) return;

            // 物品废除列表
            Object abolishedList = data.get("abolished");
            if (abolishedList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        ResourceLocation id = ResourceLocation.tryParse(s);
                        if (id != null) ABOLISHED_ITEMS.add(id);
                    }
                }
            }

            // 护甲废除
            Object armor = data.get("armor");
            if (armor instanceof Boolean b) {
                ARMOR_ABOLISHED = b;
            }

            LOGGER.info("Loaded abolition state: {} items abolished, armor={}",
                    ABOLISHED_ITEMS.size(), ARMOR_ABOLISHED);
        } catch (Exception e) {
            LOGGER.warn("Failed to load abolition state", e);
        }
    }

    /** 保存当前状态到文件 */
    private static synchronized void save() {
        File file = getSaveFile();
        file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("abolished", ABOLISHED_ITEMS.stream()
                    .map(ResourceLocation::toString).toList());
            data.put("armor", ARMOR_ABOLISHED);
            GSON.toJson(data, writer);
            LOGGER.debug("Saved abolition state ({} items)", ABOLISHED_ITEMS.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to save abolition state", e);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  物品废除
    // ══════════════════════════════════════════════════════════

    public static void abolishItem(ResourceLocation itemId) {
        load(); // 确保已加载
        ABOLISHED_ITEMS.add(itemId);
        save();
        LOGGER.info("Item abolished: {}", itemId);
    }

    public static void restoreItem(ResourceLocation itemId) {
        load();
        ABOLISHED_ITEMS.remove(itemId);
        save();
        LOGGER.info("Item restored: {}", itemId);
    }

    public static boolean isItemAbolished(ResourceLocation itemId) {
        load();
        return ABOLISHED_ITEMS.contains(itemId);
    }

    public static boolean isItemAbolished(Item item) {
        load();
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return id != null && ABOLISHED_ITEMS.contains(id);
    }

    public static Set<ResourceLocation> getAbolishedItems() {
        load();
        return ABOLISHED_ITEMS;
    }

    public static void clearAbolishedItems() {
        load();
        ABOLISHED_ITEMS.clear();
        save();
        LOGGER.info("All items restored");
    }

    // ══════════════════════════════════════════════════════════
    //  护甲废除
    // ══════════════════════════════════════════════════════════

    public static void setArmorAbolished(boolean abolished) {
        load();
        ARMOR_ABOLISHED = abolished;
        save();
        LOGGER.info("Armor abolition set to: {}", abolished);
    }

    public static boolean isArmorAbolished() {
        load();
        return ARMOR_ABOLISHED;
    }

    public static boolean toggleArmorAbolished() {
        load();
        ARMOR_ABOLISHED = !ARMOR_ABOLISHED;
        save();
        LOGGER.info("Armor abolition toggled to: {}", ARMOR_ABOLISHED);
        return ARMOR_ABOLISHED;
    }
}
