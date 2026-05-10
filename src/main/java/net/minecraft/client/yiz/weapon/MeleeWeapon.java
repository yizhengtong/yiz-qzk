package net.minecraft.client.yiz.weapon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Abstract base class for all melee weapons.
 * Implements collision detection and hit box logic for close-range combat.
 */
public abstract class MeleeWeapon extends AbstractBaseWeapon {
    
    protected MeleeWeapon(ResourceLocation id, String translationKey) {
        super(id, translationKey, WeaponCategory.MELEE);
    }
    
    @Override
    public void onPreAttack(LivingEntity attacker, Entity target) {
        // Default: Melee weapons have no pre-attack logic
        // Override for charge attacks, combo systems, etc.
    }
    
    @Override
    public void onHit(LivingEntity attacker, Entity target, DamageResult damage) {
        // Apply default melee hit logic
        super.onHit(attacker, target, damage);
        
        // Melee-specific effects can be added here
        onMeleeHit(attacker, target, damage);
    }
    
    /**
     * Melee-specific hit logic.
     * Default: No-op. Override for effects like knockback, stun, etc.
     */
    protected void onMeleeHit(LivingEntity attacker, Entity target, DamageResult damage) {
        // Default: No-op
    }
    
    /**
     * Check if target is within melee range.
     * 
     * @param attacker The attacking entity
     * @param target The target entity
     * @return true if target is within attack range
     */
    public boolean isInRange(LivingEntity attacker, Entity target) {
        double distance = attacker.position().distanceTo(target.position());
        double maxRange = getBaseStats().getAttackRange();
        return distance <= maxRange;
    }
    
    /**
     * Calculate hit box for melee attack.
     * Creates an AABB in front of the attacker within attack range.
     * 
     * @param attacker The attacking entity
     * @return Attack hit box
     */
    public AABB calculateHitBox(LivingEntity attacker) {
        Vec3 pos = attacker.position();
        Vec3 look = attacker.getLookAngle().normalize();
        double range = getBaseStats().getAttackRange();
        
        return new AABB(
            pos.x - range, pos.y - 1, pos.z - range,
            pos.x + range, pos.y + 2, pos.z + range
        );
    }
    
    @Override
    public void onPostAttack(LivingEntity attacker, Entity target, DamageResult damage) {
        // Default: Melee weapons have no post-attack logic
        // Override for stamina drain, combo reset, etc.
    }
}
