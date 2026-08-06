package net.minecraft.client.yiz.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 启动期 abolish 列表 —— 在 Item 还没注册的时候就能读，
 * 不依赖 {@code Minecraft.getInstance()}。
 *
 * <p>独立于 {@link AbolitionStateManager}（全局创作偏好的运行时状态）。
 * 两者都是<b>全局</b>偏好（存游戏目录、跨所有存档保留），区别仅在生效阶段：</p>
 * <ul>
 *   <li>{@code config/yizmodqzk-startup-abolish.json} —— 启动期固化的黑名单。
 *       影响 {@code MappedRegistry.register} 注入：被列入的 Item 会被替换为
 *       基类 Item，等同于该 mod 没注册过它</li>
 *   <li>{@code config/yizmodqzk-abolish.json} —— 运行时 abolish 状态（全局偏好）。
 *       走 vtable / Mixin 层拦截</li>
 * </ul>
 *
 * <p>JSON 格式：</p>
 * <pre>{
 *   "abolished": ["modid:item_id"]
 * }</pre>
 */
public final class StartupAbolishConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("StartupAbolishConfig");

    private static final String FILE_NAME = "yizmodqzk-startup-abolish.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 启动期被废除的物品 id 集合（已规范化为小写 mod:path）*/
    private static final Set<String> STARTUP_ABOLISHED = new HashSet<>();

    /** 是否已尝试加载（避免每次注册都重读文件）*/
    private static volatile boolean loaded = false;

    private StartupAbolishConfig() {}

    /**
     * 首次调用时从 config 目录读取黑名单。
     * 后续调用复用缓存。线程安全：第一次进来的线程读，其他线程等待。
     */
    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path file = configDir.resolve(FILE_NAME);
            if (!Files.exists(file)) {
                // 不存在：写个空模板提示用户怎么用
                writeTemplate(file);
                LOGGER.info("[StartupAbolish] No config found; wrote empty template at {}", file);
                return;
            }

            try (FileReader reader = new FileReader(file.toFile())) {
                Type t = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> data = GSON.fromJson(reader, t);
                if (data == null) return;

                Object list = data.get("abolished");
                if (list instanceof List<?> raw) {
                    for (Object o : raw) {
                        if (o instanceof String s && !s.isBlank()) {
                            STARTUP_ABOLISHED.add(s.trim().toLowerCase());
                        }
                    }
                }
                LOGGER.info("[StartupAbolish] Loaded {} startup-abolished items: {}", STARTUP_ABOLISHED.size(),
                        " items from " + file);
            }
        } catch (Throwable t) {
            // 启动期任何异常都不能阻塞 mod 加载
            LOGGER.error("[StartupAbolish] Load failed: {}", t.getMessage(), t);
        }
    }

    private static void writeTemplate(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("_comment", "List item IDs (mod:path) to disable at registration time. " +
                    "These items will register as a plain empty Item.");
            template.put("abolished", List.of());
            Files.writeString(file, GSON.toJson(template));
        } catch (Throwable ignored) {}
    }

    /**
     * 启动期判断某个 item id 是否要被替换为空 Item。
     *
     * @param idLower 已小写化的 "mod:path" 字符串
     */
    public static boolean isAbolished(String idLower) {
        ensureLoaded();
        return STARTUP_ABOLISHED.contains(idLower);
    }

    /** 启动期黑名单元素数。 */
    public static int size() {
        ensureLoaded();
        return STARTUP_ABOLISHED.size();
    }

    /** 返回不可变快照（已规范化的小写 id 集合）。 */
    public static Set<String> getAll() {
        ensureLoaded();
        return Collections.unmodifiableSet(new HashSet<>(STARTUP_ABOLISHED));
    }

    /**
     * 添加一项到启动黑名单并立刻写文件。
     * 重启后注册替换生效。
     *
     * @return true 表示这次确实新增了（之前不在列表里）
     */
    public static synchronized boolean add(String id) {
        ensureLoaded();
        if (id == null || id.isBlank()) return false;
        boolean added = STARTUP_ABOLISHED.add(id.trim().toLowerCase());
        if (added) save();
        return added;
    }

    /** 一键清除所有启动黑名单项并写文件。 */
    public static synchronized void clearAll() {
        ensureLoaded();
        if (!STARTUP_ABOLISHED.isEmpty()) {
            STARTUP_ABOLISHED.clear();
            save();
        }
    }

    /**
     * 从启动黑名单移除并立刻写文件。
     *
     * @return true 表示这次确实移除了（之前在列表里）
     */
    public static synchronized boolean remove(String id) {
        ensureLoaded();
        if (id == null || id.isBlank()) return false;
        boolean removed = STARTUP_ABOLISHED.remove(id.trim().toLowerCase());
        if (removed) save();
        return removed;
    }

    /**
     * 写文件 —— 排序后保存以便人类阅读。
     */
    private static void save() {
        try {
            Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            Files.createDirectories(file.getParent());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_comment", "List item IDs (mod:path) to disable at registration time. " +
                    "These items will register as a plain empty Item. " +
                    "Edits take effect on next game start.");
            data.put("abolished", new ArrayList<>(new TreeSet<>(STARTUP_ABOLISHED)));

            try (FileWriter writer = new FileWriter(file.toFile())) {
                GSON.toJson(data, writer);
            }
        } catch (Throwable t) {
            LOGGER.error("[StartupAbolish] Save failed: {}", t.getMessage(), t);
        }
    }
}
