package net.minecraft.client.yiz.core.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.time.Instant;

/**
 * 效果事件基类
 * 定义事件的公共数据结构和类型枚举。
 */
public abstract class EffectEvent {
    protected final ResourceLocation effectId;
    protected final LivingEntity source;
    protected final Instant timestamp;
    protected boolean canceled = false;
    protected String cancelReason = "";

    protected EffectEvent(ResourceLocation effectId, LivingEntity source) {
        this.effectId = effectId;
        this.source = source;
        this.timestamp = Instant.now();
    }

    public ResourceLocation getEffectId() {
        return effectId;
    }

    public LivingEntity getSource() {
        return source;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void cancel(String reason) {
        this.canceled = true;
        this.cancelReason = reason;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public abstract EventType getEventType();

    public abstract void dispatch();

    public enum EventType {
        PRE_ATTACK,
        CALCULATE_DAMAGE,
        HIT,
        POST_ATTACK,
        KILL,
        ACTIVATED,
        DEACTIVATED,
        UNLOCKED
    }
}
