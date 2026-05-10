package net.minecraft.client.yiz.weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.attribute.ModifierStack;
import net.minecraft.client.yiz.attribute.WeaponStats;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.tool.helper.EffectContextHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for all weapons in the framework.
 * Defines the core lifecycle hooks and metadata.
 * 
 * Lifecycle: onInitialize → onEquip → onPreAttack → onCalculateDamage → onHit → onPostAttack → onUnequip
 */
public abstract class AbstractBaseWeapon {
    protected final ResourceLocation id;
    protected final String translationKey;
    protected final WeaponCategory category;
    
    // Base stats from data configuration
    protected WeaponStats baseStats;
    
    // Talents attached to this weapon
    protected final List<ResourceLocation> talentIds = new ArrayList<>();
    
    /**
     * Constructor for weapon instances.
     * Automatically registers to the weapon registry.
     */
    protected AbstractBaseWeapon(ResourceLocation id, String translationKey, WeaponCategory category) {
        this.id = id;
        this.translationKey = translationKey;
        this.category = category;
        
        // Auto-register on creation
        ModRegistries.registerWeapon(this);
    }
    
    // ==================== Lifecycle Hooks ====================
    
    /**
     * Called when the weapon is first initialized/loaded.
     * Default: No-op. Override for custom initialization.
     */
    public void onInitialize() {
        // Default: No-op
    }
    
    /**
     * Called when a player equips the weapon.
     * Default: No-op. Override for equip effects.
     */
    public void onEquip(LivingEntity holder, ItemStack stack) {
        // Default: No-op
    }
    
    /**
     * Called before an attack is executed.
     * Default: No-op. Override for pre-attack logic (e.g., charging, wind-up).
     */
    public void onPreAttack(LivingEntity attacker, Entity target) {
        // Default: No-op
    }
    
    /**
     * Calculate the final damage after applying all talent modifiers.
     * Default: Returns base attack damage from stats.
     */
    public DamageResult onCalculateDamage(LivingEntity attacker, Entity target) {
        double baseDamage = getBaseStats().getAttackDamage();
        
        // Apply modifier stack (additive → multiplicative → independent)
        double finalDamage = ModifierStack.calculate(
            baseDamage,
            gatherModifiers(ModifierType.ADDITIVE),
            gatherModifiers(ModifierType.MULTIPLICATIVE),
            gatherModifiers(ModifierType.INDEPENDENT)
        );
        
        return new DamageResult(finalDamage, baseDamage);
    }
    
    /**
     * Called when the weapon hits a target.
     * Default: Applies calculated damage. Override for special hit effects.
     */
    public void onHit(LivingEntity attacker, Entity target, DamageResult damage) {
        if (target instanceof LivingEntity livingTarget) {
            livingTarget.hurt(EffectContextHelper.getAttackDamageSource(attacker), (float) damage.getFinalValue());
        }
    }
    
    /**
     * Called after an attack is completed.
     * Default: No-op. Override for post-attack logic (e.g., cooldown, stamina drain).
     */
    public void onPostAttack(LivingEntity attacker, Entity target, DamageResult damage) {
        // Default: No-op
    }
    
    /**
     * Called when the weapon is unequipped.
     * Default: No-op. Override for cleanup effects.
     */
    public void onUnequip(LivingEntity holder, ItemStack stack) {
        // Default: No-op
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Gather all modifiers from talents for a specific type.
     */
    protected List<net.minecraft.client.yiz.attribute.AttributeModifier> gatherModifiers(ModifierType type) {
        List<net.minecraft.client.yiz.attribute.AttributeModifier> modifiers = new ArrayList<>();
        
        for (ResourceLocation talentId : talentIds) {
            ModRegistries.getTalent(talentId).ifPresent(talent -> {
                modifiers.addAll(talent.getModifiers(this, type));
            });
        }
        
        return modifiers;
    }
    
    // ==================== Getters ====================
    
    public ResourceLocation getId() {
        return id;
    }
    
    public String getTranslationKey() {
        return translationKey;
    }
    
    public WeaponCategory getCategory() {
        return category;
    }
    
    public WeaponStats getBaseStats() {
        return baseStats;
    }
    
    public void setBaseStats(WeaponStats stats) {
        this.baseStats = stats;
    }
    
    public List<ResourceLocation> getTalentIds() {
        return List.copyOf(talentIds);
    }
    
    public void addTalent(ResourceLocation talentId) {
        this.talentIds.add(talentId);
    }
    
    // ==================== Weapon Category Enum ====================
    
    public enum WeaponCategory {
        MELEE,
        RANGED,
        MAGIC
    }
    
    // ==================== Damage Result ====================
    
    /**
     * Holds the result of damage calculation.
     */
    public static class DamageResult {
        private final double finalValue;
        private final double baseValue;
        
        public DamageResult(double finalValue, double baseValue) {
            this.finalValue = finalValue;
            this.baseValue = baseValue;
        }
        
        public double getFinalValue() {
            return finalValue;
        }
        
        public double getBaseValue() {
            return baseValue;
        }
        
        public double getModifierBonus() {
            return finalValue - baseValue;
        }
    }
    
    /**
     * Modifier type for attribute calculation.
     */
    public enum ModifierType {
        ADDITIVE,
        MULTIPLICATIVE,
        INDEPENDENT
    }
}
