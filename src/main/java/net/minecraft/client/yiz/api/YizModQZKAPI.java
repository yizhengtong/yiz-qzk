package net.minecraft.client.yiz.api;

import net.minecraft.client.yiz.core.event.EffectEventBus;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * YizMod QZK 公开 API
 * 第三方模组通过此接口与前置库交互。
 */
public final class YizModQZKAPI {

    private YizModQZKAPI() {}

    // ==================== 效果注册 ====================

    /**
     * 注册效果。
     */
    public static void registerEffect(AbstractEffect effect) {
        ModRegistries.registerEffect(effect);
    }

    /**
     * 根据 ID 获取效果。
     */
    public static Optional<AbstractEffect> getEffect(ResourceLocation id) {
        return ModRegistries.getEffect(id);
    }

    // ==================== 解锁管理 ====================

    /**
     * 为实体解锁效果。
     */
    public static void unlockEffect(LivingEntity entity, ResourceLocation effectId) {
        UnlockManager.unlock(entity, effectId);
    }

    /**
     * 检查实体是否已解锁效果。
     */
    public static boolean isEffectUnlocked(LivingEntity entity, ResourceLocation effectId) {
        return UnlockManager.isUnlocked(entity, effectId);
    }

    // ==================== 效果查询 ====================

    /**
     * 分发效果上下文（触发效果系统）。
     */
    public static void dispatchContext(EffectContext context) {
        EffectEventBus.dispatchContext(context);
    }

    // ==================== 注册表查询 ====================

    /**
     * 获取所有注册的效果。
     */
    public static List<AbstractEffect> getAllEffects() {
        return List.copyOf(ModRegistries.getAllEffects());
    }

    // ==================== 快捷方法 ====================

    /**
     * 直接为物品附加效果（写入 NBT）。
     */
    public static void attachEffectToItem(ItemStack stack, AbstractEffect effect) {
        net.minecraft.client.yiz.core.data.EffectNBTHandler.addEffectToItem(stack, effect);
    }

    /**
     * 获取物品上的所有效果。
     */
    public static List<AbstractEffect> getItemEffects(ItemStack stack) {
        return net.minecraft.client.yiz.core.data.EffectNBTHandler.getItemEffects(stack);
    }
}
