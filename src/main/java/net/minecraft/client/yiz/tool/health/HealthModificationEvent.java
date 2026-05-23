package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 健康值修改事件
 * 携带修改上下文，收集所有修正器。
 * 通过 NeoForge EventBus 发布。
 */
public class HealthModificationEvent extends Event {

    private final LivingEntity targetEntity;
    private final EffectContext context;
    private final List<HealthModifier> modifiers = new ArrayList<>();
    private boolean canceled = false;
    private String cancelReason = "";

    public HealthModificationEvent(LivingEntity targetEntity, EffectContext context) {
        this.targetEntity = targetEntity;
        this.context = context;
    }

    /**
     * 添加修正器（去重：相同 ID 只添加一次）。
     */
    public void addModifier(HealthModifier modifier) {
        if (modifiers.stream().noneMatch(m -> m.getId().equals(modifier.getId()))) {
            modifiers.add(modifier);
        }
    }

    /**
     * 批量添加修正器。
     */
    public void addModifiers(List<HealthModifier> modifiers) {
        for (HealthModifier modifier : modifiers) {
            addModifier(modifier);
        }
    }

    public List<HealthModifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    public LivingEntity getTargetEntity() {
        return targetEntity;
    }

    public EffectContext getContext() {
        return context;
    }

    public void cancel(String reason) {
        this.canceled = true;
        this.cancelReason = reason;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
