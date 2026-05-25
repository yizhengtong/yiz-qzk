package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;

/**
 * 境界阶段定义 — 不可变数据类
 * <p>
 * 每个境界有一个唯一 ID、排序序号、显示名和可选的属性叠加表。
 * 属性叠加表在突破时按境界累加——到谌我时筑命的加成也生效。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RealmStage buildDestiny = new RealmStage(
 *     ResourceLocation.fromNamespaceAndPath("yizxian", "build_destiny"),
 *     0, "筑命",
 *     Map.of("attack", 1.20, "max_health", 1.10)
 * );
 * RealmProgressionAPI.registerStage(buildDestiny);
 * }</pre>
 *
 * @param id                 唯一标识（建议用 modid:name 格式）
 * @param order              排序序号，0 为起始境界
 * @param displayName        显示名
 * @param attributeModifiers 本境界独有的属性叠加（key=属性名, value=倍率）
 */
public record RealmStage(
    ResourceLocation id,
    int order,
    String displayName,
    Map<String, Double> attributeModifiers
) {
    public RealmStage(ResourceLocation id, int order, String displayName, Map<String, Double> attributeModifiers) {
        this.id = id;
        this.order = order;
        this.displayName = displayName;
        this.attributeModifiers = attributeModifiers == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(attributeModifiers);
    }

    /** 无属性加成的境界 */
    public RealmStage(ResourceLocation id, int order, String displayName) {
        this(id, order, displayName, Collections.emptyMap());
    }
}
