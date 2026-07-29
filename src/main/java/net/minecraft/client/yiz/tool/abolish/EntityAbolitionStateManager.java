package net.minecraft.client.yiz.tool.abolish;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体废除中央状态管理器（带文件持久化）。
 *
 * <p>管理与持久化已废除的实体类型列表。Mixin 层读取此状态来决定
 * 是否阻止特定实体类型加入世界或被渲染。VTable 层在注册时额外执行
 * vtable 入口覆写。</p>
 *
 * <p>状态保存到 {@code config/yizmodqzk-abolish-entities.json}，
 * 跨存档生效（全局实体偏好，非世界隔离）。</p>
 *
 * <p>与 {@link net.minecraft.client.yiz.core.AbolitionStateManager}（物品废除）
 * 同架构、独立文件——实体和物品的废除列表互不干扰。</p>
 */
public final class EntityAbolitionStateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("EntityAbolitionState");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "yizmodqzk-abolish-entities.json";

    /** 已废除的实体类型 ID 集合 */
    private static final Set<ResourceLocation> ABOLISHED_ENTITIES = ConcurrentHashMap.newKeySet();

    /** 是否启用实体废除（全局开关） */
    private static volatile boolean ENABLED = true;

    /** 是否已从文件加载 */
    private static volatile boolean loaded = false;

    private EntityAbolitionStateManager() {}

    // ══════════════════════════════════════════════════════════
    //  持久化
    // ══════════════════════════════════════════════════════════

    private static File getSaveFile() {
        try {
            Minecraft mc = Minecraft.getInstance();
            File dir = mc.gameDirectory;
            return new File(dir, "config/" + FILE_NAME);
        } catch (Exception e) {
            return new File(FILE_NAME);
        }
    }

    /** 从文件加载状态（仅在第一次调用时执行） */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;

        File file = getSaveFile();
        if (!file.exists()) {
            LOGGER.info("No saved entity abolition state found at {}", file);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = GSON.fromJson(reader, Map.class);
            if (data == null) return;

            // 实体废除列表
            Object abolishedList = data.get("abolished");
            if (abolishedList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s) {
                        ResourceLocation id = ResourceLocation.tryParse(s);
                        if (id != null) ABOLISHED_ENTITIES.add(id);
                    }
                }
            }

            // 全局开关
            Object enabled = data.get("enabled");
            if (enabled instanceof Boolean b) {
                ENABLED = b;
            }

            LOGGER.info("Loaded entity abolition state: {} entities abolished, enabled={}",
                    ABOLISHED_ENTITIES.size(), ENABLED);
        } catch (Exception e) {
            LOGGER.warn("Failed to load entity abolition state", e);
        }
    }

    /** 保存当前状态到文件 */
    private static synchronized void save() {
        File file = getSaveFile();
        file.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(file)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("abolished", ABOLISHED_ENTITIES.stream()
                    .map(ResourceLocation::toString).toList());
            data.put("enabled", ENABLED);
            GSON.toJson(data, writer);
            LOGGER.debug("Saved entity abolition state ({} entities)", ABOLISHED_ENTITIES.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to save entity abolition state", e);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  实体废除 CRUD
    // ══════════════════════════════════════════════════════════

    public static void abolishEntity(ResourceLocation entityId) {
        load();
        ABOLISHED_ENTITIES.add(entityId);
        save();
        LOGGER.info("Entity abolished: {}", entityId);
    }

    public static void restoreEntity(ResourceLocation entityId) {
        load();
        ABOLISHED_ENTITIES.remove(entityId);
        save();
        LOGGER.info("Entity restored: {}", entityId);
    }

    public static boolean isEntityAbolished(ResourceLocation entityId) {
        load();
        return ENABLED && ABOLISHED_ENTITIES.contains(entityId);
    }

    /**
     * 检查实体是否被废除——按 EntityType 的注册 ID。
     */
    public static boolean isEntityAbolished(net.minecraft.world.entity.Entity entity) {
        load();
        if (!ENABLED) return false;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType());
        return id != null && ABOLISHED_ENTITIES.contains(id);
    }

    public static Set<ResourceLocation> getAbolishedEntities() {
        load();
        return Collections.unmodifiableSet(new HashSet<>(ABOLISHED_ENTITIES));
    }

    public static void clearAbolishedEntities() {
        load();
        ABOLISHED_ENTITIES.clear();
        save();
        LOGGER.info("All entity abolitions cleared");
    }

    // ══════════════════════════════════════════════════════════
    //  全局开关
    // ══════════════════════════════════════════════════════════

    public static boolean isEnabled() {
        load();
        return ENABLED;
    }

    public static void setEnabled(boolean enabled) {
        load();
        ENABLED = enabled;
        save();
        LOGGER.info("Entity abolition enabled: {}", enabled);
    }

    public static boolean toggleEnabled() {
        load();
        ENABLED = !ENABLED;
        save();
        LOGGER.info("Entity abolition toggled to: {}", ENABLED);
        return ENABLED;
    }
}
