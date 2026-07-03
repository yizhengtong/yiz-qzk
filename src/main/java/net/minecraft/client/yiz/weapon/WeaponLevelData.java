package net.minecraft.client.yiz.weapon;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * 单级武器数据捆绑包 — 品质 + 战斗属性 + 类型专用额外参数。
 *
 * <p>由 {@link WeaponProfile} 逐级持有，通过 {@link WeaponProfile#forLevel(int)} 获取。</p>
 * <p>tier 可为 null — 仅在 JSON 覆写解析的部分 Profile 中出现，表示不覆写品质信息。</p>
 */
public record WeaponLevelData(
    @Nullable QualityTier tier,
    WeaponStats stats,
    Map<String, Double> extraParams
) {
    /** 空 Map 的便利构造。 */
    public WeaponLevelData(QualityTier tier, WeaponStats stats) {
        this(tier, stats, Collections.emptyMap());
    }

    /** 获取 extra 中的 double 值，缺省返回 0。 */
    public double getExtra(String key) {
        return extraParams.getOrDefault(key, 0.0);
    }

    /** 获取 extra 中的 int 值（四舍五入），缺省返回 0。 */
    public int getExtraInt(String key) {
        return (int) Math.round(getExtra(key));
    }
}
