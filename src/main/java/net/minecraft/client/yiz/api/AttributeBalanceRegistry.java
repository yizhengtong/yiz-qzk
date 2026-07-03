package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性修饰正负相等锁定
 * <p>
 * enableFor(entity) 记录所有属性当前值为底线，
 * 后续任何修改都不会让属性值低于记录值。
 * </p>
 *
 * <h3>内存管理</h3>
 * <p>通过监听实体移除事件自动清理已注册的实体属性记录，
 * 避免死实体持有 AttributeInstance 引用阻止 GC。</p>
 */
// 大白话: 属性平衡方法
public final class AttributeBalanceRegistry {

    private static final Map<AttributeInstance, Double> FLOORS = new ConcurrentHashMap<>();
    private static volatile boolean cleanupRegistered = false;

    private AttributeBalanceRegistry() {}

    /**
     * 为该实体的所有属性记录当前值作为底线
     */
    @SuppressWarnings("unchecked")
    public static void enableFor(LivingEntity entity) {
        registerCleanupIfNeeded();
        try {
            var field = entity.getAttributes().getClass().getDeclaredField("attributes");
            field.setAccessible(true);
            var map = (java.util.Map<?, AttributeInstance>) field.get(entity.getAttributes());
            for (AttributeInstance inst : map.values()) {
                FLOORS.putIfAbsent(inst, inst.getValue());
            }
        } catch (Exception e) {
            net.minecraft.client.yiz.tizMod.LOGGER.warn(
                "[AttributeBalanceRegistry] enableFor failed for {}: {}",
                entity.getName().getString(), e.toString());
        }
    }

    /**
     * 每 tick 强制恢复：若当前值低于底线，直接设回底线
     */
    @SuppressWarnings("unchecked")
    public static void enforceFloors(LivingEntity entity) {
        try {
            var field = entity.getAttributes().getClass().getDeclaredField("attributes");
            field.setAccessible(true);
            var map = (java.util.Map<?, AttributeInstance>) field.get(entity.getAttributes());
            for (AttributeInstance inst : map.values()) {
                Double floor = FLOORS.get(inst);
                if (floor != null && inst.getValue() < floor) {
                    inst.setBaseValue(floor);
                }
            }
        } catch (Exception e) {
            net.minecraft.client.yiz.tizMod.LOGGER.warn(
                "[AttributeBalanceRegistry] enforceFloors failed for {}: {}",
                entity.getName().getString(), e.toString());
        }
    }

    /**
     * 注册实体移除事件监听器，自动清理死实体的属性记录。
     * 惰性初始化，首次 enableFor 调用时注册。
     */
    private static void registerCleanupIfNeeded() {
        if (cleanupRegistered) return;
        cleanupRegistered = true;
        NeoForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
                cleanupEntity(event.getEntity());
            }
            @SubscribeEvent
            public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
                cleanupEntity(event.getEntity());
            }
        });
    }

    private static void cleanupEntity(LivingEntity entity) {
        try {
            var field = entity.getAttributes().getClass().getDeclaredField("attributes");
            field.setAccessible(true);
            var map = (java.util.Map<?, AttributeInstance>) field.get(entity.getAttributes());
            for (AttributeInstance inst : map.values()) {
                FLOORS.remove(inst);
            }
        } catch (Exception ignored) {}
    }
}
