package net.minecraft.client.yiz.core.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.talent.AbstractTalent;
import net.minecraft.client.yiz.tizMod;
import net.minecraft.client.yiz.weapon.AbstractBaseWeapon;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Core registry system for weapons, talents, and effects.
 * Provides centralized registration and lookup functionality.
 */
public final class ModRegistries {
    private static final Map<ResourceLocation, AbstractBaseWeapon> WEAPON_REGISTRY = new HashMap<>();
    private static final Map<ResourceLocation, AbstractTalent> TALENT_REGISTRY = new HashMap<>();
    private static final Map<ResourceLocation, AbstractEffect> EFFECT_REGISTRY = new HashMap<>();

    private ModRegistries() {}

    // ==================== Weapon Registry ====================

    public static void registerWeapon(AbstractBaseWeapon weapon) {
        ResourceLocation id = weapon.getId();
        if (WEAPON_REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Weapon already registered: " + id);
        }
        WEAPON_REGISTRY.put(id, weapon);
    }

    public static Optional<AbstractBaseWeapon> getWeapon(ResourceLocation id) {
        return Optional.ofNullable(WEAPON_REGISTRY.get(id));
    }

    public static boolean hasWeapon(ResourceLocation id) {
        return WEAPON_REGISTRY.containsKey(id);
    }

    public static Map<ResourceLocation, AbstractBaseWeapon> getAllWeapons() {
        return Map.copyOf(WEAPON_REGISTRY);
    }

    // ==================== Talent Registry ====================

    public static void registerTalent(AbstractTalent talent) {
        ResourceLocation id = talent.getId();
        if (TALENT_REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Talent already registered: " + id);
        }
        TALENT_REGISTRY.put(id, talent);
    }

    public static Optional<AbstractTalent> getTalent(ResourceLocation id) {
        return Optional.ofNullable(TALENT_REGISTRY.get(id));
    }

    public static boolean hasTalent(ResourceLocation id) {
        return TALENT_REGISTRY.containsKey(id);
    }

    public static Map<ResourceLocation, AbstractTalent> getAllTalents() {
        return Map.copyOf(TALENT_REGISTRY);
    }

    // ==================== Effect Registry ====================

    public static void registerEffect(AbstractEffect effect) {
        ResourceLocation id = effect.getId();
        if (EFFECT_REGISTRY.containsKey(id)) {
            // 重复注册时跳过并警告
            tizMod.LOGGER.warn("Effect already registered: {}, skipping", id);
            return;
        }
        EFFECT_REGISTRY.put(id, effect);
        tizMod.LOGGER.debug("Registered effect: {}", id);
    }

    public static Optional<AbstractEffect> getEffect(ResourceLocation id) {
        return Optional.ofNullable(EFFECT_REGISTRY.get(id));
    }

    public static boolean hasEffect(ResourceLocation id) {
        return EFFECT_REGISTRY.containsKey(id);
    }

    public static Collection<AbstractEffect> getAllEffects() {
        return EFFECT_REGISTRY.values();
    }

    // ==================== Clear (for testing) ====================

    public static void clearAll() {
        WEAPON_REGISTRY.clear();
        TALENT_REGISTRY.clear();
        EFFECT_REGISTRY.clear();
    }
}
