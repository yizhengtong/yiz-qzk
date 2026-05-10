package net.minecraft.client.yiz.effect.unlock;

import net.minecraft.client.yiz.core.event.EffectEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;

/**
 * 解锁事件
 * 当效果被解锁时触发，用于 EventBus 通知。
 */
public class UnlockEvent extends EffectEvent {

    private final ResourceLocation unlockedEffectId;
    private final String source;

    public UnlockEvent(LivingEntity entity, ResourceLocation unlockedEffectId, String source) {
        super(unlockedEffectId, entity);
        this.unlockedEffectId = unlockedEffectId;
        this.source = source;
    }

    public ResourceLocation getUnlockedEffectId() {
        return unlockedEffectId;
    }

    public String getUnlockSource() {
        return source;
    }

    @Override
    public EventType getEventType() {
        return EventType.UNLOCKED;
    }

    @Override
    public void dispatch() {
        // 由 EffectEventBus 调用
    }
}
