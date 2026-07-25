package net.minecraft.client.yiz.weapon;

import net.minecraft.resources.ResourceLocation;

/**
 * 近战武器属性标准化倍率生成器。
 *
 * <p>只需提供 Lv1（平凡）基础面板，自动按预设倍率生成全部 5 级 WeaponProfile。</p>
 *
 * <h3>倍率表</h3>
 * <table>
 * <tr><th>属性</th><th>Lv1 平凡</th><th>Lv2 优秀</th><th>Lv3 精良</th><th>Lv4 史诗</th><th>Lv5 传说</th></tr>
 * <tr><td>攻击力</td><td>1.0</td><td>1.5</td><td>2.0</td><td>3.0</td><td>5.0</td></tr>
 * <tr><td>攻击速度</td><td>1.0</td><td>1.1</td><td>1.2</td><td>1.3</td><td>1.5</td></tr>
 * <tr><td>暴击几率</td><td>1.0</td><td>1.25</td><td>1.75</td><td>2.0</td><td>2.5</td></tr>
 * <tr><td>暴伤效果</td><td>1.0</td><td>1.5</td><td>1.75</td><td>2.0</td><td>3.0</td></tr>
 * <tr><td>吸血效果</td><td>1.0</td><td>1.2</td><td>1.4</td><td>1.6</td><td>2.0</td></tr>
 * <tr><td>伤害半径</td><td>1.0</td><td>1.1</td><td>1.3</td><td>1.5</td><td>2.0</td></tr>
 * <tr><td>溅射伤害</td><td>1.0</td><td>1.2</td><td>1.4</td><td>1.6</td><td>2.0</td></tr>
 * <tr><td>溅射衰减</td><td>1.0</td><td>1.2</td><td>1.4</td><td>1.6</td><td>2.0</td></tr>
 * <tr><td>实体交互距离</td><td>1.0</td><td>1.2</td><td>1.3</td><td>1.4</td><td>1.5</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * WeaponProfile profile = MeleeWeaponScaling.buildProfile(weaponId,
 *     new MeleeWeaponScaling.BaseStats(
 *         5.0,    // 攻击力 Lv1
 *         2.5,    // 攻击速度 Lv1
 *         5,      // 暴击率 Lv1
 *         0,      // 暴伤 Lv1
 *         0,      // 吸血 Lv1
 *         0,      // 溅射半径 Lv1
 *         0,      // 溅射伤害 Lv1
 *         0,      // 溅射衰减 Lv1
 *         0       // 实体交互距离 Lv1
 *     ));
 * }</pre>
 */
public final class MeleeWeaponScaling {

    private MeleeWeaponScaling() {}

    /** 默认品质级数 */
    public static final int TIER_COUNT = 5;

    // ═══════════════════════════════════════════════════════════
    //  倍率表 (index 0 = Lv1 平凡, index 4 = Lv5 传说)
    // ═══════════════════════════════════════════════════════════

    private static final double[] DAMAGE        = {1.0, 1.1, 1.2, 1.4, 1.6};
    private static final double[] SPEED         = {1.0, 1.1, 1.2, 1.3, 1.5};
    private static final double[] CRIT_RATE     = {1.0, 1.25, 1.75, 2.0, 2.5};
    private static final double[] CRIT_DMG      = {1.0, 1.5, 1.75, 2.0, 3.0};
    private static final double[] LIFE_STEAL    = {1.0, 1.2, 1.4, 1.6, 2.0};
    private static final double[] SPLASH_RADIUS = {1.0, 1.1, 1.3, 1.5, 2.0};
    private static final double[] SPLASH_DMG    = {1.0, 1.2, 1.4, 1.6, 2.0};
    private static final double[] SPLASH_FALLOFF= {1.0, 1.2, 1.4, 1.6, 2.0};
    /** 实体交互距离 Lv1→Lv5 倍率 */
    private static final double[] INTERACTION_RANGE_PCT = {1.0, 1.2, 1.3, 1.4, 1.5};

    // ═══════════════════════════════════════════════════════════
    //  BaseStats
    // ═══════════════════════════════════════════════════════════

    /**
     * 近战武器 Lv1 平凡基础面板。
     * <p>所有字段值 > 0 的属性才会按倍率缩放；= 0 的属性所有等级保持 0。</p>
     */
    public record BaseStats(
        double damage,
        double speed,
        double critRate,
        double critDmg,
        double lifeSteal,
        double splashRadius,
        double splashDmg,
        double splashFalloff,
        double interactionRange  // 实体交互距离 Lv1（=0 时不修改默认交互距离）
    ) {
        public static final BaseStats ZERO = new BaseStats(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    // ═══════════════════════════════════════════════════════════
    //  Profile 生成
    // ═══════════════════════════════════════════════════════════

    /**
     * 从 Lv1 基础面板生成全部 5 级 WeaponProfile。
     * <p>damage 和 speed 写入 {@link WeaponStats}，其余属性写入 {@code extra()}。</p>
     */
    public static WeaponProfile buildProfile(ResourceLocation weaponId, BaseStats base) {
        return buildProfileWithSpeeds(weaponId, base, null);
    }

    /**
     * 带自定义攻击速度的 Profile 生成。speedOverrides 为各等级精确攻击速度值，
     * 传入 {@code null} 则使用标准倍率 {@link #SPEED} 缩放。
     */
    public static WeaponProfile buildProfileWithSpeeds(ResourceLocation weaponId, BaseStats base,
                                                        double[] speedOverrides) {
        WeaponProfile.Builder b = WeaponProfile.builder(weaponId);
        for (int lv = 0; lv < TIER_COUNT; lv++) {
            int lvl = lv + 1;
            double speed = (speedOverrides != null && speedOverrides.length > lv)
                ? speedOverrides[lv]
                : base.speed * SPEED[lv];
            b.level(lvl)
                .stats(base.damage * DAMAGE[lv], speed)
                .critRate(base.critRate * CRIT_RATE[lv])
                .extra("critDmg",        base.critDmg        * CRIT_DMG[lv])
                .extra("lifeSteal",      base.lifeSteal      * LIFE_STEAL[lv])
                .extra("splashRadius",   base.splashRadius   * SPLASH_RADIUS[lv])
                .extra("splashDmg",      base.splashDmg      * SPLASH_DMG[lv])
                .extra("splashFalloff",  base.splashFalloff  * SPLASH_FALLOFF[lv])
                .extra("entityInteraction", base.interactionRange * INTERACTION_RANGE_PCT[lv]);
            if (lv < TIER_COUNT - 1) b.next();
        }
        return b.build();
    }

    /**
     * 查询指定属性在指定等级的倍率。
     * @param attribute 属性名（如 "damage", "speed", "critRate" 等）
     * @param level 品质等级 1~5
     * @return 该等级相对 Lv1 的倍率
     */
    public static double multiplierFor(String attribute, int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= TIER_COUNT) return 1.0;
        return switch (attribute) {
            case "damage"       -> DAMAGE[idx];
            case "speed"        -> SPEED[idx];
            case "critRate"     -> CRIT_RATE[idx];
            case "critDmg"      -> CRIT_DMG[idx];
            case "lifeSteal"    -> LIFE_STEAL[idx];
            case "splashRadius" -> SPLASH_RADIUS[idx];
            case "splashDmg"    -> SPLASH_DMG[idx];
            case "splashFalloff"-> SPLASH_FALLOFF[idx];
            case "entityInteraction" -> INTERACTION_RANGE_PCT[idx];
            default -> 1.0;
        };
    }
}
