package net.minecraft.client.yiz.attribute;

import java.util.List;

/**
 * Multi-zone attribute calculation engine.
 * Formula: Final = (Base + ΣAdditive) × Π(1 + Multiplicative) × ΠIndependent
 * 
 * Supports additive, multiplicative, and independent modifier zones
 * to prevent uncontrolled stat inflation.
 */
public class ModifierStack {
    
    /**
     * Calculate final value using multi-zone formula.
     * 
     * @param baseValue The base stat value
     * @param additiveModifiers Additive modifiers (flat bonuses)
     * @param multiplicativeModifiers Multiplicative modifiers (percentage bonuses)
     * @param independentModifiers Independent multipliers (separate zone)
     * @return Final calculated value
     */
    public static double calculate(
        double baseValue,
        List<AttributeModifier> additiveModifiers,
        List<AttributeModifier> multiplicativeModifiers,
        List<AttributeModifier> independentModifiers
    ) {
        double current = baseValue;
        
        // Zone 1: Additive (flat addition)
        double additiveBonus = 0;
        for (AttributeModifier mod : additiveModifiers) {
            additiveBonus += mod.getValue();
        }
        current += additiveBonus;
        
        // Zone 2: Multiplicative (percentage multiplication)
        double multiplicativeMultiplier = 1.0;
        for (AttributeModifier mod : multiplicativeModifiers) {
            multiplicativeMultiplier *= (1.0 + mod.getValue());
        }
        current *= multiplicativeMultiplier;
        
        // Zone 3: Independent (final separate multiplication)
        double independentMultiplier = 1.0;
        for (AttributeModifier mod : independentModifiers) {
            independentMultiplier *= (1.0 + mod.getValue());
        }
        current *= independentMultiplier;
        
        return current;
    }
    
    /**
     * Calculate with soft cap to prevent extreme inflation.
     * Values exceeding the soft cap receive diminishing returns.
     * 
     * @param baseValue The base stat value
     * @param additiveModifiers Additive modifiers
     * @param multiplicativeModifiers Multiplicative modifiers
     * @param independentModifiers Independent modifiers
     * @param softCapThreshold The threshold where diminishing returns begin
     * @param diminishingFactor Reduction factor for values exceeding soft cap (0.0-1.0)
     * @return Final calculated value with soft cap applied
     */
    public static double calculateWithSoftCap(
        double baseValue,
        List<AttributeModifier> additiveModifiers,
        List<AttributeModifier> multiplicativeModifiers,
        List<AttributeModifier> independentModifiers,
        double softCapThreshold,
        double diminishingFactor
    ) {
        double result = calculate(baseValue, additiveModifiers, multiplicativeModifiers, independentModifiers);
        
        // Apply soft cap
        if (result > softCapThreshold) {
            double excess = result - softCapThreshold;
            result = softCapThreshold + (excess * diminishingFactor);
        }
        
        return result;
    }
}
