package net.minecraft.client.yiz.core.event;

import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.tool.health.HealthModificationManager;
import net.minecraft.client.yiz.tool.health.HealthModificationTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.yiz.tizMod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 效果事件总线
 * 分发事件到所有监听器，以及调度效果上下文。
 * 不替代 NeoForge EventBus，而是框架内部的效果调度层。
 */
public final class EffectEventBus {

    private static final Logger LOGGER = tizMod.LOGGER;
    private static final List<EffectListener> listeners = new ArrayList<>();
    private static final Map<ResourceLocation, Long> dispatchCount = new ConcurrentHashMap<>();

    private EffectEventBus() {}

    // ==================== 监听器管理 ====================

    /**
     * 注册事件监听器。
     */
    public static void register(EffectListener listener) {
        listeners.add(listener);
        LOGGER.debug("Effect listener registered: {}", listener.getClass().getSimpleName());
    }

    /**
     * 移除事件监听器。
     */
    public static void unregister(EffectListener listener) {
        listeners.remove(listener);
    }

    // ==================== 事件分发 ====================

    /**
     * 分发事件到所有监听器。
     */
    public static void post(EffectEvent event) {
        for (EffectListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error("Effect event dispatch error for listener {}: {}",
                    listener.getClass().getSimpleName(), e.getMessage());
            }
        }

        // 统计调度次数
        dispatchCount.merge(event.getEffectId(), 1L, Long::sum);
    }

    // ==================== 核心调度方法 ====================

    /**
     * 分发上下文到效果系统。
     * 遍历所有已注册效果，检查感知、解锁、生效条件，执行效果。
     * 执行完毕后，检查并触发被动健康值修改。
     */
    public static void dispatchContext(EffectContext context) {
        if (context == null || context.entity() == null) {
            return;
        }

        LivingEntity entity = context.entity();

        // 遍历所有已注册效果
        for (AbstractEffect effect : ModRegistries.getAllEffects()) {
            try {
                // 创建带效果引用的上下文
                EffectContext effectContext = context.withEffect(effect);

                // 1. 检查感知方式
                if (!effect.checkPerception(entity, effectContext)) {
                    continue;
                }

                // 2. 检查解锁状态
                if (!effect.isUnlocked(entity)) {
                    continue;
                }

                // 3. 检查生效条件
                if (!effect.getActivationCondition().shouldActivate(effectContext)) {
                    continue;
                }

                // 4. 执行效果（效果内部可自行调用健康值修改）
                effect.execute(effectContext);

            } catch (Exception e) {
                LOGGER.error("Error dispatching effect {}: {}", effect.getId(), e.getMessage());
            }
        }
    }

    /**
     * 分发上下文并检查被动健康值修改。
     * 在 dispatchContext 的基础上，额外检查是否有 HealthModificationTrigger
     * 需要触发。用于 EffectEventBus 驱动周期性健康值修改的场景。
     *
     * @param context  效果上下文
     * @param trigger  可选触发器（null 则等同于 dispatchContext）
     */
    public static void dispatchContextWithHealthMod(
        EffectContext context,
        HealthModificationTrigger trigger
    ) {
        if (context == null || context.entity() == null) {
            return;
        }

        // 先执行标准分发
        dispatchContext(context);

        // 如果有触发器，检查是否应该触发健康值修改
        if (trigger != null && trigger.shouldTrigger(context.entity(), context)) {
            HealthModificationManager.triggerModification(context.entity(), context);
        }
    }

    /**
     * 获取特定效果的调度次数。
     */
    public static long getDispatchCount(ResourceLocation effectId) {
        return dispatchCount.getOrDefault(effectId, 0L);
    }

    /**
     * 清除所有监听器（用于测试）。
     */
    public static void clearListeners() {
        listeners.clear();
        dispatchCount.clear();
    }

    /**
     * 效果事件监听器接口。
     */
    @FunctionalInterface
    public interface EffectListener {
        void onEvent(EffectEvent event);
    }
}
