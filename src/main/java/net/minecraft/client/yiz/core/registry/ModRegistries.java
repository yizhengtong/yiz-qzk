package net.minecraft.client.yiz.core.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.tizMod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core registry system for effects.
 * Provides centralized registration and lookup functionality.
 */
public final class ModRegistries {
    private static final Map<ResourceLocation, AbstractEffect> EFFECT_REGISTRY = new ConcurrentHashMap<>();

    /**
     * 预排序的 EntityPerception 效果缓存。
     * 效果只在模组加载阶段注册，因此注册后不会变更，只需在注册时维护。
     * 排序规则：稀有度降序 → 等级降序。
     */
    private static final List<AbstractEffect> ENTITY_PERCEPTION_EFFECTS = new ArrayList<>();
    private static final Comparator<AbstractEffect> TALENT_COMPARATOR = Comparator
        .comparingInt((AbstractEffect e) -> e.getRarity().ordinal())
        .thenComparing(Comparator.comparingInt(AbstractEffect::getLevel).reversed());

    private ModRegistries() {}

    // ==================== Effect Registry ====================

    public static void registerEffect(AbstractEffect effect) {
        ResourceLocation id = effect.getId();
        if (EFFECT_REGISTRY.containsKey(id)) {
            tizMod.LOGGER.warn("Effect already registered: {}, skipping", id);
            return;
        }
        EFFECT_REGISTRY.put(id, effect);
        tizMod.LOGGER.debug("Registered effect: {}", id);

        // 维护 EntityPerception 缓存（同步保护，防止 EffectDataLoader 后台线程并发写入）
        boolean isEntityPerception = effect.getPerceptionModes().stream()
            .anyMatch(m -> m instanceof EntityPerception);
        if (isEntityPerception) {
            synchronized (ENTITY_PERCEPTION_EFFECTS) {
                ENTITY_PERCEPTION_EFFECTS.add(effect);
                ENTITY_PERCEPTION_EFFECTS.sort(TALENT_COMPARATOR);
            }
        }
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

    /** 获取所有 EntityPerception 类型的效果（预排序、只读）。用于天赋面板渲染。 */
    public static List<AbstractEffect> getEntityPerceptionEffects() {
        return List.copyOf(ENTITY_PERCEPTION_EFFECTS);
    }

}
