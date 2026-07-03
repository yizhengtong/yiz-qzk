package net.minecraft.client.yiz.weapon;

/**
 * 不可变战斗属性记录 — 所有武器类型共有的数值维度。
 *
 * <p>支持 {@link #merge(WeaponStats)} 用于 JSON 覆写合并：
 * override 中非零值覆盖，零值保留原值。</p>
 */
public record WeaponStats(
    double damage,
    double speed,
    double knockback,
    double critRate,
    double attackRange,
    double specialEffectRate
) {
    /** 全零基准值。 */
    public static final WeaponStats ZERO = new WeaponStats(0, 1.0, 0, 0, 0, 0);

    /** 只设置伤害和攻速的快捷构造。 */
    public static WeaponStats of(double damage, double speed) {
        return new WeaponStats(damage, speed, 0, 0, 0, 0);
    }

    /**
     * JSON 覆写合并：override 中的非零值覆盖，零值保留原值。
     * speed 用 != 1.0 判断（因为默认速度是 1.0）。
     */
    public WeaponStats merge(WeaponStats override) {
        if (override == null) return this;
        return new WeaponStats(
            override.damage != 0 ? override.damage : this.damage,
            override.speed != 1.0 ? override.speed : this.speed,
            override.knockback != 0 ? override.knockback : this.knockback,
            override.critRate != 0 ? override.critRate : this.critRate,
            override.attackRange != 0 ? override.attackRange : this.attackRange,
            override.specialEffectRate != 0 ? override.specialEffectRate : this.specialEffectRate
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  Builder
    // ═══════════════════════════════════════════════════════════

    public static class Builder {
        private double damage;
        private double speed = 1.0;
        private double knockback;
        private double critRate;
        private double attackRange;
        private double specialEffectRate;

        public Builder damage(double v)         { this.damage = v; return this; }
        public Builder speed(double v)          { this.speed = v; return this; }
        public Builder knockback(double v)      { this.knockback = v; return this; }
        public Builder critRate(double v)       { this.critRate = v; return this; }
        public Builder attackRange(double v)    { this.attackRange = v; return this; }
        public Builder specialEffectRate(double v) { this.specialEffectRate = v; return this; }

        public WeaponStats build() {
            return new WeaponStats(damage, speed, knockback, critRate, attackRange, specialEffectRate);
        }
    }
}
