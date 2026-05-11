package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.core.event.EffectEvent;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 健康值修改效果事件
 * 桥接 EffectEventBus（效果系统内部）和 HealthModificationEvent（NeoForge EventBus）。
 *
 * 当效果系统通过 EffectEventBus 调度需要进行健康值修改时，
 * 创建此事件包装 HealthModificationEvent，在 EffectEventBus 上发布。
 */
public class HealthModificationEffectEvent extends EffectEvent {

    private final HealthModificationEvent healthEvent;
    private final EffectContext effectContext;

    public HealthModificationEffectEvent(
        ResourceLocation effectId,
        LivingEntity source,
        HealthModificationEvent healthEvent,
        EffectContext effectContext
    ) {
        super(effectId, source);
        this.healthEvent = healthEvent;
        this.effectContext = effectContext;
    }

    /**
     * 获取底层的健康值修改事件（用于 NeoForge EventBus 发布）。
     */
    public HealthModificationEvent getHealthEvent() {
        return healthEvent;
    }

    /**
     * 获取效果上下文。
     */
    public EffectContext getEffectContext() {
        return effectContext;
    }

    /**
     * 快捷添加修正器。
     */
    public void addModifier(HealthModifier modifier) {
        healthEvent.addModifier(modifier);
    }

    @Override
    public EventType getEventType() {
        return EventType.HEALTH_MODIFY;
    }

    @Override
    public void dispatch() {
        // 在 EffectEventBus 中分发此事件
    }
}
