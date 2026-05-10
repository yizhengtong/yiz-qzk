package net.minecraft.client.yiz.attribute;

import net.minecraft.client.yiz.weapon.AbstractBaseWeapon;

/**
 * Represents an attribute modifier that can be applied to weapons.
 * Used by talents to modify weapon stats.
 */
public class AttributeModifier {
    private final String id;
    private final double value;
    private final ModifierType type;
    
    /**
     * Create a new attribute modifier.
     * 
     * @param id Unique identifier for this modifier
     * @param value The modifier value (can be negative)
     * @param type The modifier type (ADDITIVE, MULTIPLICATIVE, INDEPENDENT)
     */
    public AttributeModifier(String id, double value, ModifierType type) {
        this.id = id;
        this.value = value;
        this.type = type;
    }
    
    public String getId() {
        return id;
    }
    
    public double getValue() {
        return value;
    }
    
    public ModifierType getType() {
        return type;
    }
    
    /**
     * Apply this modifier to a weapon.
     * Override for complex conditional modifiers.
     */
    public double applyTo(double baseValue) {
        return switch (type) {
            case ADDITIVE -> baseValue + value;
            case MULTIPLICATIVE -> baseValue * (1.0 + value);
            case INDEPENDENT -> baseValue * (1.0 + value);
        };
    }
    
    @Override
    public String toString() {
        return String.format("AttributeModifier{id='%s', value=%.2f, type=%s}", id, value, type);
    }
    
    /**
     * Modifier type enum.
     */
    public enum ModifierType {
        /** Additive: Flat value addition (e.g., +5 damage) */
        ADDITIVE,
        /** Multiplicative: Percentage multiplication (e.g., +20% damage) */
        MULTIPLICATIVE,
        /** Independent: Final separate multiplication zone */
        INDEPENDENT
    }
}
