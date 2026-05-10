package net.minecraft.client.yiz.talent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.attribute.AttributeModifier;
import net.minecraft.client.yiz.weapon.AbstractBaseWeapon;

import java.util.List;
import java.util.ArrayList;

/**
 * Abstract base class for all talents in the framework.
 * Talents are decoupled from weapons and react to lifecycle events.
 */
public abstract class AbstractTalent {
    protected final ResourceLocation id;
    protected final String translationKey;
    protected final TalentTrigger trigger;
    protected final TalentScope scope;
    protected final int priority;
    
    /**
     * Constructor for talent instances.
     * Automatically registers to the talent registry.
     * 
     * @param id Unique talent identifier
     * @param translationKey Translation key for display
     * @param trigger When this talent activates
     * @param scope Where this talent can be equipped (GLOBAL/EXCLUSIVE/CATEGORY)
     * @param priority Execution order (higher = first)
     */
    protected AbstractTalent(ResourceLocation id, String translationKey, 
                           TalentTrigger trigger, TalentScope scope, int priority) {
        this.id = id;
        this.translationKey = translationKey;
        this.trigger = trigger;
        this.scope = scope;
        this.priority = priority;
        
        // Auto-register on creation
        net.minecraft.client.yiz.core.registry.ModRegistries.registerTalent(this);
    }
    
    // ==================== Core Methods ====================
    
    /**
     * Check if this talent can be applied to the given weapon.
     * Used by the framework to validate talent assignments.
     * 
     * @param weapon The weapon to check
     * @return true if this talent can be equipped
     */
    public boolean canApplyTo(AbstractBaseWeapon weapon) {
        return switch (scope) {
            case GLOBAL -> true;
            case EXCLUSIVE -> checkExclusiveAccess(weapon);
            case CATEGORY -> weapon.getCategory() == getRequiredCategory();
        };
    }
    
    /**
     * Check exclusive access for EXCLUSIVE scope talents.
     * Default: false. Override to define which weapons can use this talent.
     */
    protected boolean checkExclusiveAccess(AbstractBaseWeapon weapon) {
        return false;
    }
    
    /**
     * Get the required category for CATEGORY scope talents.
     * Default: MELEE. Override if talent is for RANGED/MAGIC weapons.
     */
    protected AbstractBaseWeapon.WeaponCategory getRequiredCategory() {
        return AbstractBaseWeapon.WeaponCategory.MELEE;
    }
    
    // ==================== Trigger & Action ====================
    
    /**
     * Get the trigger condition for this talent.
     */
    public TalentTrigger getTrigger() {
        return trigger;
    }
    
    /**
     * Execute the talent's effect when triggered.
     * 
     * @param weapon The weapon that triggered this talent
     * @param attacker The entity attacking
     * @param target The entity being attacked
     */
    public abstract void execute(AbstractBaseWeapon weapon, 
                                net.minecraft.world.entity.LivingEntity attacker, 
                                net.minecraft.world.entity.Entity target);
    
    // ==================== Modifier System ====================
    
    /**
     * Get all modifiers this talent provides for a specific type.
     * Override to provide stat bonuses (damage, speed, etc.)
     * 
     * @param weapon The weapon to modify
     * @param type The modifier type to gather
     * @return List of modifiers (empty if none)
     */
    public List<AttributeModifier> getModifiers(AbstractBaseWeapon weapon, 
                                               AbstractBaseWeapon.ModifierType type) {
        // Default: No modifiers. Override in subclasses.
        return List.of();
    }
    
    // ==================== Getters ====================
    
    public ResourceLocation getId() {
        return id;
    }
    
    public String getTranslationKey() {
        return translationKey;
    }
    
    public TalentScope getScope() {
        return scope;
    }
    
    public int getPriority() {
        return priority;
    }
    
    // ==================== Enums ====================
    
    /**
     * When a talent activates.
     */
    public enum TalentTrigger {
        ON_PRE_ATTACK,      // Before attack
        ON_CALCULATE_DAMAGE, // During damage calculation
        ON_HIT,             // When hitting target
        ON_POST_ATTACK,     // After attack
        ON_KILL,            // On enemy kill
        ON_EQUIP,           // When equipped
        ON_UNEQUIP          // When unequipped
    }
    
    /**
     * Where this talent can be equipped.
     */
    public enum TalentScope {
        /** Available to all weapons */
        GLOBAL,
        /** Available only to specific weapons (checkExclusiveAccess) */
        EXCLUSIVE,
        /** Available only to a weapon category (Melee/Ranged/Magic) */
        CATEGORY
    }
}
