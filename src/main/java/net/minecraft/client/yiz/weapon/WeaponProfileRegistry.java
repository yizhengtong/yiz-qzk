package net.minecraft.client.yiz.weapon;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 集中式武器 Profile 注册表 — 支撑 JSON 热重载。
 *
 * <h3>生命周期</h3>
 * <ol>
 * <li>mod 初始化时：通过 {@link #register(ResourceLocation, WeaponProfile)} 注册代码默认值</li>
 * <li>运行时：通过 {@link #getEffective(ResourceLocation)} 获取生效值</li>
 * <li>/reload 时：调用 {@link #reload(Map)} 应用 JSON 覆写</li>
 * </ol>
 */
public final class WeaponProfileRegistry {

    private static final Map<ResourceLocation, WeaponProfile> CODE_DEFAULTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, WeaponProfile> EFFECTIVE = new LinkedHashMap<>();

    private WeaponProfileRegistry() {}

    /** 注册代码默认 Profile（mod 初始化时调用）。 */
    public static void register(ResourceLocation weaponId, WeaponProfile codeDefault) {
        CODE_DEFAULTS.put(weaponId, codeDefault);
        EFFECTIVE.put(weaponId, codeDefault);
    }

    /** 获取运行时生效的 Profile（可能已被 JSON 覆写）。 */
    public static WeaponProfile getEffective(ResourceLocation weaponId) {
        return EFFECTIVE.get(weaponId);
    }

    /** 获取所有已注册的武器 ID。 */
    public static Set<ResourceLocation> getAllWeaponIds() {
        return Collections.unmodifiableSet(CODE_DEFAULTS.keySet());
    }

    /**
     * JSON 热重载入口。
     * @param jsonOverrides 从 JSON 文件加载的覆写数据（只包含被覆写的字段）
     */
    public static void reload(Map<ResourceLocation, WeaponProfile> jsonOverrides) {
        for (Map.Entry<ResourceLocation, WeaponProfile> entry : CODE_DEFAULTS.entrySet()) {
            ResourceLocation id = entry.getKey();
            WeaponProfile code = entry.getValue();
            WeaponProfile jsonOverride = jsonOverrides.get(id);
            EFFECTIVE.put(id, code.merge(jsonOverride));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  JSON 加载
    // ═══════════════════════════════════════════════════════════

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    /**
     * 从配置目录加载所有武器的 JSON 覆写文件。
     *
     * <p>期望目录结构：{@code configDir/<weapon_name>.json}</p>
     *
     * <p>JSON 格式：
     * <pre>{@code
     * {
     *   "levels": [
     *     { "stats": { "damage": 10.0, "speed": 2.4 }, "extra": { "maxSwords": 3 } },
     *     { "stats": { "damage": 14.0 } },
     *     {},
     *     {},
     *     { "stats": { "damage": 32.0 } }
     *   ]
     * }
     * }</pre>
     *
     * @param configDir 配置文件目录（如 config/yizxianmod/weapons/）
     * @return weaponId → 部分 Profile（仅包含覆写字段）
     */
    public static Map<ResourceLocation, WeaponProfile> loadFromJson(Path configDir, String namespace) {
        Map<ResourceLocation, WeaponProfile> overrides = new LinkedHashMap<>();
        if (!Files.isDirectory(configDir)) return overrides;

        try (Stream<Path> files = Files.list(configDir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                String fileName = path.getFileName().toString();
                String weaponName = fileName.substring(0, fileName.length() - 5); // 去掉 .json
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, weaponName);

                try {
                    String json = Files.readString(path);
                    WeaponProfile partial = parsePartial(id, json);
                    if (partial != null) {
                        overrides.put(id, partial);
                    }
                } catch (Exception e) {
                    // 日志：跳过损坏的配置文件
                    System.err.println("[WeaponProfileRegistry] Failed to parse " + path + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("[WeaponProfileRegistry] Failed to list config dir: " + e.getMessage());
        }
        return overrides;
    }

    /** 解析 JSON 字符串为部分 Profile（覆写用）。 */
    private static WeaponProfile parsePartial(ResourceLocation weaponId, String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (root == null || !root.has("levels")) return null;

        JsonArray levelsArr = root.getAsJsonArray("levels");
        List<WeaponLevelData> levelDataList = new ArrayList<>();

        for (int i = 0; i < levelsArr.size(); i++) {
            JsonObject levelObj = levelsArr.get(i).getAsJsonObject();

            // 解析 stats
            WeaponStats.Builder statsBuilder = new WeaponStats.Builder();
            if (levelObj.has("stats")) {
                JsonObject statsObj = levelObj.getAsJsonObject("stats");
                if (statsObj.has("damage")) statsBuilder.damage(statsObj.get("damage").getAsDouble());
                if (statsObj.has("speed")) statsBuilder.speed(statsObj.get("speed").getAsDouble());
                if (statsObj.has("knockback")) statsBuilder.knockback(statsObj.get("knockback").getAsDouble());
                if (statsObj.has("critRate")) statsBuilder.critRate(statsObj.get("critRate").getAsDouble());
                if (statsObj.has("attackRange")) statsBuilder.attackRange(statsObj.get("attackRange").getAsDouble());
                if (statsObj.has("specialEffectRate")) statsBuilder.specialEffectRate(statsObj.get("specialEffectRate").getAsDouble());
            }

            // 解析 extra
            Map<String, Double> extras = new LinkedHashMap<>();
            if (levelObj.has("extra")) {
                JsonObject extraObj = levelObj.getAsJsonObject("extra");
                for (Map.Entry<String, JsonElement> entry : extraObj.entrySet()) {
                    extras.put(entry.getKey(), entry.getValue().getAsDouble());
                }
            }

            // 部分数据：tier 为 null（merge 时保留原 tier）
            levelDataList.add(new WeaponLevelData(null, statsBuilder.build(),
                Collections.unmodifiableMap(extras)));
        }

        // dummy tiers 仅用于满足 levelCount 校验，merge 时不会使用
        QualityTier placeholder = new QualityTier(0, "", null, 5, null);
        List<QualityTier> placeholders = Collections.nCopies(levelDataList.size(), placeholder);
        return new WeaponProfile(weaponId, placeholders,
            levelDataList.toArray(new WeaponLevelData[0]));
    }
}
