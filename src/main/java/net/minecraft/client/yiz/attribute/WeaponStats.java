package net.minecraft.client.yiz.attribute;

/**
 * Holds the base stats for a weapon.
 * Populated from JSON configuration during data loading.
 */
public class WeaponStats {
    private double attackDamage;
    private double attackSpeed;
    private double attackRange;
    private double criticalChance;
    private double durability;

    public WeaponStats() {
        // Default values for JSON deserialization
        this.attackDamage = 1.0;
        this.attackSpeed = 1.6;
        this.attackRange = 3.0;
        this.criticalChance = 0.05;
        this.durability = 100.0;
    }

    public WeaponStats(double attackDamage, double attackSpeed, double attackRange) {
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
        this.attackRange = attackRange;
        this.criticalChance = 0.05;
        this.durability = 100.0;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public void setAttackDamage(double attackDamage) {
        this.attackDamage = attackDamage;
    }

    public double getAttackSpeed() {
        return attackSpeed;
    }

    public void setAttackSpeed(double attackSpeed) {
        this.attackSpeed = attackSpeed;
    }

    public double getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(double attackRange) {
        this.attackRange = attackRange;
    }

    public double getCriticalChance() {
        return criticalChance;
    }

    public void setCriticalChance(double criticalChance) {
        this.criticalChance = criticalChance;
    }

    public double getDurability() {
        return durability;
    }

    public void setDurability(double durability) {
        this.durability = durability;
    }

    @Override
    public String toString() {
        return String.format("WeaponStats{damage=%.1f, speed=%.2f, range=%.1f}", 
            attackDamage, attackSpeed, attackRange);
    }
}
