package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;

/**
 * 伤害应用事件（通知入口）
 * <p>
 * 在 {@link YizModQZKAPI#damage} 或 {@link YizModQZKAPI#percentDamage}
 * 即将应用伤害到目标实体前触发。
 * 其他模组可监听此事件以修改伤害值、取消伤害、或记录日志。
 * </p>
 * <p>
 * 调用 {@link #cancel(String)} 阻止本次伤害应用。
 * </p>
 */
public class DamageEvent extends Event {

    private final LivingEntity target;
    private float amount;
    private final DamageType type;
    private final Entity source;
    private boolean canceled = false;
    private String cancelReason = "";

    public DamageEvent(LivingEntity target, float amount, DamageType type, Entity source) {
        this.target = target;
        this.amount = amount;
        this.type = type;
        this.source = source;
    }

    /** 取消本次伤害 */
    public void cancel(String reason) {
        this.canceled = true;
        this.cancelReason = reason;
    }

    public boolean isCanceled() { return canceled; }

    // ==================== Getters / Setters ====================

    /** 伤害承受方 */
    public LivingEntity getTarget() { return target; }

    /** 伤害数值（可修改） */
    public float getAmount() { return amount; }

    /** 修改伤害数值 */
    public void setAmount(float amount) { this.amount = amount; }

    /** 伤害类型 */
    public DamageType getType() { return type; }

    /** 伤害来源（攻击者），可能为空 */
    public Entity getSource() { return source; }

    /** 取消原因 */
    public String getCancelReason() { return cancelReason; }
}
