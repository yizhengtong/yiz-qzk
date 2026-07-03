package net.minecraft.client.yiz.weapon;

import net.minecraft.world.item.Rarity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 品质层级记录 — 所有分级武器共用的品质定义。
 *
 * <p>替代散落在各武器子类中的 NAME_PREFIX / RARITY 硬编码数组。</p>
 *
 * <h3>默认 5 级品质</h3>
 * <table>
 * <tr><th>Level</th><th>品质</th><th>光效</th><th>稀有度</th></tr>
 * <tr><td>1</td><td>平凡</td><td>灰白 0.4α</td><td>COMMON</td></tr>
 * <tr><td>2</td><td>优秀</td><td>翠绿 0.5α</td><td>UNCOMMON</td></tr>
 * <tr><td>3</td><td>精良</td><td>冰蓝 0.5α</td><td>RARE</td></tr>
 * <tr><td>4</td><td>史诗</td><td>紫罗兰 0.6α</td><td>EPIC</td></tr>
 * <tr><td>5</td><td>传说</td><td>动画色板</td><td>EPIC</td></tr>
 * </table>
 */
public record QualityTier(
    int level,
    String prefix,
    @Nullable Vector4f glowColor,
    int glowType,
    Rarity rarity
) {
    // ═══════════════════════════════════════════════════════════
    //  默认 5 级品质（所有武器共用）
    // ═══════════════════════════════════════════════════════════

    public static final List<QualityTier> DEFAULT_5 = List.of(
        new QualityTier(1, "平凡", new Vector4f(0.80f, 0.80f, 0.80f, 0.4f), 0, Rarity.COMMON),
        new QualityTier(2, "优秀", new Vector4f(0.30f, 0.90f, 0.40f, 0.5f), 0, Rarity.UNCOMMON),
        new QualityTier(3, "精良", new Vector4f(0.30f, 0.60f, 1.00f, 0.5f), 0, Rarity.RARE),
        new QualityTier(4, "史诗", new Vector4f(0.70f, 0.30f, 1.00f, 0.6f), 0, Rarity.EPIC),
        new QualityTier(5, "传说", null, 5, Rarity.EPIC)
    );

    // ═══════════════════════════════════════════════════════════
    //  工厂方法
    // ═══════════════════════════════════════════════════════════

    /** 用可变参数快速构建自定义品质列表。 */
    public static List<QualityTier> ofEntries(QualityTier... tiers) {
        return List.of(tiers);
    }

    /**
     * 从品质列表中按 level 取光效色。
     * @param tiers  品质列表
     * @param level  1-based 等级
     * @return 光效色，传说级（null）表示动画色板
     */
    @Nullable
    public static Vector4f glowColorForLevel(List<QualityTier> tiers, int level) {
        if (level < 1 || level > tiers.size()) return null;
        return tiers.get(level - 1).glowColor();
    }

    /**
     * 构建自定义品质列表 — 保留默认前 N 级的命名和稀有度映射。
     * @param count 需要的品质数量
     * @return 前 count 级从 DEFAULT_5 取，超出则自动生成通用名称
     */
    public static List<QualityTier> defaultsUpTo(int count) {
        if (count <= DEFAULT_5.size()) {
            return List.copyOf(DEFAULT_5.subList(0, count));
        }
        List<QualityTier> extended = new ArrayList<>(DEFAULT_5);
        for (int i = DEFAULT_5.size() + 1; i <= count; i++) {
            extended.add(new QualityTier(i, "Tier " + i, null, 5, Rarity.EPIC));
        }
        return List.copyOf(extended);
    }
}
