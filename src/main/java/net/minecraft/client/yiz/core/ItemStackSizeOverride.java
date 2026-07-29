package net.minecraft.client.yiz.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品最大堆叠数覆盖表（运行时拦截 + 双文件持久化）。
 *
 * <p>1.21.1 中最大堆叠数本质是 {@code DataComponents.MAX_STACK_SIZE} 数据组件，
 * 取值链：{@code ItemStack#getMaxStackSize()} → {@code Item#getMaxStackSize(stack)}
 * → {@code stack.getOrDefault(MAX_STACK_SIZE, 1)}。本类提供「物品 ID → 自定义堆叠数」
 * 的映射，由 {@code ItemStackMaxSizeMixin} 在 {@code ItemStack#getMaxStackSize()} 入口查表，
 * 命中则返回自定义值，实现对任意已注册物品（原版 + 所有 mod）的运行时改堆叠数。</p>
 *
 * <h3>双层隔离：先天默认 vs 后天设置</h3>
 * 为避免「下游 mod 注册的默认配置」与「玩家运行时自定义」互相污染，状态分两层存储：
 * <ul>
 *   <li><b>先天默认</b>（{@code DEFAULTS}）— 下游 mod 通过 {@link #setIfAbsent} 在启动时注册
 *       （如碗/桶/药水/附魔书=16）。存 {@code config/yizmodqzk-stacksize-defaults.json}，
 *       运行时生成，玩家一般不直接编辑。</li>
 *   <li><b>后天设置</b>（{@code OVERRIDES}）— 玩家通过 {@code /yiz stack set} 运行时设置。
 *       存 {@code config/yizmodqzk-stacksize.json}。优先级<b>高于</b>先天默认。</li>
 * </ul>
 * 查询时 {@link #getOverride} 先查后天、未命中再查先天；两层都未命中返回 -1（走原版）。
 * {@code /yiz stack reset} 只删后天设置 —— 该物品若有先天默认则回退到先天默认值，
 * 否则回原版。{@code setIfAbsent} 只写先天表，绝不污染后天设置。
 *
 * <p>属<b>全局创作偏好</b>（非世界隔离），跨所有存档保留。</p>
 *
 * <h3>说明</h3>
 * <ul>
 *   <li>本方案只改「最大堆叠数上限」，不会自动 split / merge 已存在的栈 ——
 *       比如把某物品上限从 64 改成 1，玩家手里那把 64 个的栈不会被拆开，只是不能再往上叠。</li>
 *   <li>同时影响 {@code ItemStack#isStackable()}，因为后者依赖 {@code getMaxStackSize() > 1}。</li>
 *   <li>上限严格 99：原版 {@code Container}/{@code Slot}（槽位）与 {@code ItemStack.CODEC}
 *       （序列化 {@code intRange(1,99)}）独立强制此值，超出会导致存盘数据丢失。</li>
 * </ul>
 */
public final class ItemStackSizeOverride {
    private static final Logger LOGGER = LoggerFactory.getLogger("ItemStackSize");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 后天设置文件（玩家 /yiz stack set 的值，优先级高） */
    private static final String OVERRIDES_FILE = "yizmodqzk-stacksize.json";
    /** 先天默认文件（下游 mod setIfAbsent 注册的默认值） */
    private static final String DEFAULTS_FILE = "yizmodqzk-stacksize-defaults.json";
    /** 堆叠核心强化次数文件（物品 ID → 已强化次数，上限 2） */
    private static final String ENHANCE_FILE = "yizmodqzk-stacksize-enhance.json";

    /**
     * 合法堆叠数边界：1 ~ 99。
     * <p>上限 {@value MAX} 取自 1.21.1 {@code Item#ABSOLUTE_MAX_STACK_SIZE}（99）。
     * 这是 Mojang 在 {@code Container}（槽位默认 99）、{@code Slot}、以及
     * {@code ItemStack.CODEC} 的 {@code intRange(1,99)} 多处共同强制的架构上限，
     * 超出会在序列化存盘时被 Codec 拒绝导致数据丢失，故严格夹紧到 99。</p>
     */
    public static final int MIN = 1;
    public static final int MAX = 99;

    /** 后天设置：玩家运行时自定义（优先级高） */
    private static final Map<ResourceLocation, Integer> OVERRIDES = new ConcurrentHashMap<>();
    /** 先天默认：下游 mod 启动时注册的默认配置（优先级低） */
    private static final Map<ResourceLocation, Integer> DEFAULTS = new ConcurrentHashMap<>();
    /** 堆叠核心强化次数：物品 ID → 已强化次数（0/1/2，最多 2 次） */
    private static final Map<ResourceLocation, Integer> ENHANCE_COUNT = new ConcurrentHashMap<>();
    /** 单个物品 ID 最多强化次数 */
    public static final int MAX_ENHANCE = 2;

    private static volatile boolean loaded = false;

    private ItemStackSizeOverride() {}

    // ══════════════════════════════════════════════════════════
    //  持久化
    // ══════════════════════════════════════════════════════════

    private static File getConfigDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return new File(mc.gameDirectory, "config");
        } catch (Exception e) {
            return new File("."); // fallback
        }
    }

    private static File getOverridesFile() {
        return new File(getConfigDir(), OVERRIDES_FILE);
    }

    private static File getDefaultsFile() {
        return new File(getConfigDir(), DEFAULTS_FILE);
    }

    private static File getEnhanceFile() {
        return new File(getConfigDir(), ENHANCE_FILE);
    }

    /** 从文件加载状态（仅在第一次调用时执行）。 */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        loadInto(getDefaultsFile(), DEFAULTS, "defaults");
        loadInto(getOverridesFile(), OVERRIDES, "overrides");
        loadInto(getEnhanceFile(), ENHANCE_COUNT, "enhance");
    }

    private static void loadInto(File file, Map<ResourceLocation, Integer> target, String label) {
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> raw = GSON.fromJson(reader, type);
            if (raw != null) {
                target.clear();
                raw.forEach((key, value) -> {
                    ResourceLocation id = ResourceLocation.tryParse(key);
                    if (id != null && value != null) {
                        target.put(id, clamp(value));
                    }
                });
                LOGGER.info("Loaded {} stack-size {} from {}", target.size(), label, file.getName());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load stack-size {} from {}", label, file, e);
        }
    }

    /** 保存后天设置到文件。 */
    private static synchronized void saveOverrides() {
        saveTo(getOverridesFile(), OVERRIDES);
    }

    /** 保存先天默认到文件。 */
    private static synchronized void saveDefaults() {
        saveTo(getDefaultsFile(), DEFAULTS);
    }

    private static void saveTo(File file, Map<ResourceLocation, Integer> source) {
        try {
            file.getParentFile().mkdirs();
            Map<String, Integer> raw = new TreeMap<>();
            source.forEach((id, size) -> raw.put(id.toString(), size));
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(raw, writer);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save stack-size overrides to {}", file, e);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  核心 API
    // ══════════════════════════════════════════════════════════

    /** 夹紧到合法区间。 */
    private static int clamp(int size) {
        return Math.max(MIN, Math.min(MAX, size));
    }

    /**
     * 玩家运行时设置物品的最大堆叠数（写<b>后天</b>表，优先级高于先天默认）。
     *
     * @param itemId 目标物品 ID
     * @param size   目标堆叠数，会被夹紧到 [{@value MIN}, {@value MAX}]
     * @return 实际写入的（夹紧后的）堆叠数
     */
    public static int set(ResourceLocation itemId, int size) {
        if (itemId == null) return -1;
        load();
        int clamped = clamp(size);
        OVERRIDES.put(itemId, clamped);
        saveOverrides();
        LOGGER.info("Set max stack size of {} to {}", itemId, clamped);
        return clamped;
    }

    /**
     * 移除某物品的<b>后天</b>设置。
     * <p>该物品若有先天默认（{@link #setIfAbsent} 注册的）则回退到先天默认值；
     * 否则回原版。不会动先天默认表。</p>
     *
     * @return true 表示原本存在后天设置并被移除
     */
    public static boolean reset(ResourceLocation itemId) {
        if (itemId == null) return false;
        load();
        boolean removed = OVERRIDES.remove(itemId) != null;
        if (removed) {
            saveOverrides();
            Integer def = DEFAULTS.get(itemId);
            LOGGER.info("Reset {} ; reverted to {}", itemId,
                    def != null ? "default " + def : "vanilla");
        }
        return removed;
    }

    /** 清空所有<b>后天</b>设置（不动先天默认）。 */
    public static int clear() {
        load();
        int n = OVERRIDES.size();
        OVERRIDES.clear();
        saveOverrides();
        LOGGER.info("Cleared all {} stack-size overrides (defaults untouched, {} remain)",
                n, DEFAULTS.size());
        return n;
    }

    /**
     * 查表（后天优先）：返回该物品的有效堆叠数；未覆盖返回 -1。
     * <p>由 Mixin 层每次 {@code getMaxStackSize()} 调用查询，必须轻量。
     * 先查后天设置，未命中再查先天默认。</p>
     */
    public static int getOverride(ResourceLocation itemId) {
        if (itemId == null) return -1;
        Integer v = OVERRIDES.get(itemId);
        if (v != null) return v;
        v = DEFAULTS.get(itemId);
        return v == null ? -1 : v;
    }

    /**
     * 是否存在任意层覆盖（先天或后天）。
     */
    public static boolean isOverridden(ResourceLocation itemId) {
        return itemId != null && (OVERRIDES.containsKey(itemId) || DEFAULTS.containsKey(itemId));
    }

    /**
     * 返回合并视图快照（按 ID 字符串排序，用于 list 指令）。
     * <p>同一物品后天设置覆盖先天默认；值带来源标记便于区分。</p>
     */
    public static Map<ResourceLocation, Integer> snapshot() {
        load();
        Map<ResourceLocation, Integer> sorted = new TreeMap<>(
                (a, b) -> a.toString().compareTo(b.toString()));
        sorted.putAll(DEFAULTS);
        sorted.putAll(OVERRIDES); // 后天覆盖先天
        return sorted;
    }

    /** 当前生效的覆盖条目数量（合并去重后）。 */
    public static int size() {
        load();
        // 并集大小
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>(DEFAULTS.keySet());
        ids.addAll(OVERRIDES.keySet());
        return ids.size();
    }

    /**
     * 注册<b>先天默认</b>堆叠数 —— 写先天表，不覆盖玩家后天设置。
     *
     * <p>供下游 mod 在启动时批量注册默认配置（如「桶/药水/附魔书默认堆叠 16」）。
     * 与 {@link #set} 的区别：只写先天默认表，绝不碰后天设置；且同一物品重复注册
     * 以<b>最后一条</b>为准（便于下游多模块各自注册时不冲突）。玩家的后天 set/reset
     * 优先级始终高于此处。</p>
     *
     * @param itemId 目标物品 ID
     * @param size   默认堆叠数，会被夹紧到 [{@value MIN}, {@value MAX}]
     */
    public static void setIfAbsent(ResourceLocation itemId, int size) {
        if (itemId == null) return;
        load();
        int clamped = clamp(size);
        Integer prev = DEFAULTS.put(itemId, clamped);
        saveDefaults();
        if (prev == null) {
            LOGGER.info("Registered default max stack size of {} to {}", itemId, clamped);
        } else if (prev != clamped) {
            LOGGER.info("Updated default max stack size of {} from {} to {}", itemId, prev, clamped);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  堆叠核心强化次数（物品 ID 粒度，最多 MAX_ENHANCE 次）
    // ══════════════════════════════════════════════════════════

    /**
     * 查询某物品已用堆叠核心强化的次数（0 ~ {@value MAX_ENHANCE}）。
     */
    public static int getEnhanceCount(ResourceLocation itemId) {
        if (itemId == null) return 0;
        load();
        Integer v = ENHANCE_COUNT.get(itemId);
        return v == null ? 0 : v;
    }

    /**
     * 该物品是否还能继续强化（次数未达上限）。
     */
    public static boolean canEnhance(ResourceLocation itemId) {
        return getEnhanceCount(itemId) < MAX_ENHANCE;
    }

    /**
     * 累加某物品的强化次数并持久化。调用方应先 {@link #canEnhance} 判定。
     */
    public static void incrementEnhanceCount(ResourceLocation itemId) {
        if (itemId == null) return;
        load();
        int next = getEnhanceCount(itemId) + 1;
        ENHANCE_COUNT.put(itemId, next);
        saveTo(getEnhanceFile(), ENHANCE_COUNT);
        LOGGER.info("Enhance count of {} → {}", itemId, next);
    }
}
