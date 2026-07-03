package net.minecraft.client.yiz.weapon;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 武器全等级配置 — 注册系统的核心数据载体。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * WeaponProfile profile = WeaponProfile.builder(weaponId)
 *     .tiers(QualityTier.DEFAULT_5)
 *     .level(1).stats(8.5, 2.2).next()
 *     .level(2).stats(11.5, 2.2).next()
 *     .level(3).stats(14.0, 2.2).next()
 *     .level(4).stats(18.0, 2.2).next()
 *     .level(5).stats(28.0, 2.2)
 *     .build();
 * }</pre>
 *
 * <h3>JSON 覆写</h3>
 * <p>通过 {@link #merge(WeaponProfile)} 将外部 JSON 数据合并到代码默认值中。</p>
 */
public final class WeaponProfile {

    private final ResourceLocation weaponId;
    private final List<QualityTier> tiers;
    private final WeaponLevelData[] levels; // index = level - 1

    WeaponProfile(ResourceLocation weaponId, List<QualityTier> tiers,
                  WeaponLevelData[] levels) {
        this.weaponId = weaponId;
        this.tiers = List.copyOf(tiers);
        this.levels = levels.clone();
    }

    // ── 访问器 ──

    public ResourceLocation weaponId() { return weaponId; }
    public int levelCount()             { return tiers.size(); }
    public List<QualityTier> tiers()    { return tiers; }

    /** 获取指定等级的完整数据。level 为 1-based。 */
    public WeaponLevelData forLevel(int level) {
        if (level < 1 || level > levels.length) {
            throw new IllegalArgumentException(
                "Level " + level + " out of range [1, " + levels.length + "] for " + weaponId);
        }
        return levels[level - 1];
    }

    /** 获取指定等级的品质信息。 */
    public QualityTier tier(int level) {
        return tiers.get(level - 1);
    }

    // ── JSON 覆写合并 ──

    /**
     * 将 JSON 覆写数据合并到当前代码默认值。
     * override 中非零值覆盖，零值保留原值；extra 参数叠加。
     */
    public WeaponProfile merge(WeaponProfile override) {
        if (override == null) return this;
        WeaponLevelData[] merged = new WeaponLevelData[levels.length];
        for (int i = 0; i < levels.length; i++) {
            WeaponLevelData base = levels[i];
            WeaponLevelData ov = (i < override.levels.length) ? override.levels[i] : null;
            QualityTier tier = (ov != null && ov.tier() != null) ? ov.tier() : base.tier();
            WeaponStats stats = (ov != null) ? base.stats().merge(ov.stats()) : base.stats();
            Map<String, Double> extras = new HashMap<>(base.extraParams());
            if (ov != null) extras.putAll(ov.extraParams());
            merged[i] = new WeaponLevelData(tier, stats, Collections.unmodifiableMap(extras));
        }
        return new WeaponProfile(weaponId, tiers, merged);
    }

    // ═══════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════

    public static Builder builder(ResourceLocation weaponId) {
        return new Builder(weaponId);
    }

    public static class Builder {
        private final ResourceLocation weaponId;
        private List<QualityTier> tiers = QualityTier.DEFAULT_5;
        private final List<WeaponLevelData> levels = new ArrayList<>();
        private int currentLevel = 0;            // 0 = 未开始
        private WeaponStats.Builder currentStats = new WeaponStats.Builder();
        private final Map<String, Double> currentExtras = new LinkedHashMap<>();

        private Builder(ResourceLocation weaponId) {
            this.weaponId = weaponId;
        }

        // ── Layer 1: 品质层级定义 ──

        /** 使用自定义品质层级列表（覆盖默认 5 级）。 */
        public Builder tiers(List<QualityTier> tiers) {
            this.tiers = List.copyOf(tiers);
            return this;
        }

        /** 设置品质层级数量（从 DEFAULT_5 截取或扩展）。 */
        public Builder tierCount(int count) {
            this.tiers = QualityTier.defaultsUpTo(count);
            return this;
        }

        // ── Layer 2: 逐级属性定义 ──

        /**
         * 开始配置指定等级。level 为 1-based。
         * 调用此方法会自动 flush 上一级。
         */
        public Builder level(int level) {
            flushCurrentLevel();
            this.currentLevel = level;
            this.currentStats = new WeaponStats.Builder();
            this.currentExtras.clear();
            return this;
        }

        /** 设置当前等级的伤害和攻速。 */
        public Builder stats(double damage, double speed) {
            currentStats.damage(damage).speed(speed);
            return this;
        }

        public Builder knockback(double v)      { currentStats.knockback(v); return this; }
        public Builder critRate(double v)       { currentStats.critRate(v); return this; }
        public Builder attackRange(double v)    { currentStats.attackRange(v); return this; }
        public Builder specialEffectRate(double v) { currentStats.specialEffectRate(v); return this; }

        /** 添加类型专用数值参数（如 maxSwords, trueDmg 等）。 */
        public Builder extra(String key, double value) {
            currentExtras.put(key, value);
            return this;
        }

        /** 快捷方法：flush 当前级并进入下一级。 */
        public Builder next() {
            int next = currentLevel + 1;
            // 如果还没调过 level()，从 1 开始
            if (currentLevel == 0) next = 1;
            return level(next);
        }

        /** 构建 WeaponProfile，自动 flush 最后一级并校验。 */
        public WeaponProfile build() {
            flushCurrentLevel();
            if (levels.size() != tiers.size()) {
                throw new IllegalStateException(
                    "WeaponProfile for " + weaponId + ": defined " + levels.size()
                    + " levels but tiers has " + tiers.size() + " entries");
            }
            return new WeaponProfile(weaponId, tiers,
                levels.toArray(new WeaponLevelData[0]));
        }

        private void flushCurrentLevel() {
            if (currentLevel > 0 && currentLevel <= tiers.size()) {
                // 确保列表容量足够（处理跳级定义的情况）
                while (levels.size() < currentLevel) {
                    levels.add(null); // 占位
                }
                int idx = currentLevel - 1;
                if (idx < levels.size()) {
                    levels.set(idx, new WeaponLevelData(
                        tiers.get(idx),
                        currentStats.build(),
                        Map.copyOf(currentExtras)
                    ));
                } else {
                    levels.add(new WeaponLevelData(
                        tiers.get(idx),
                        currentStats.build(),
                        Map.copyOf(currentExtras)
                    ));
                }
            }
        }
    }
}
