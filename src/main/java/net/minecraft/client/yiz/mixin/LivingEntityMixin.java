package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.bridge.HealthDataBridge;
import net.minecraft.client.yiz.bridge.InvulnerableDataBridge;
import net.minecraft.client.yiz.tool.health.HealthModificationScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LivingEntity Mixin
 * 健康值 Delta 系统的 Mixin 数据定义层。
 *
 * <p>职责：
 * <ol>
 *   <li>定义 {@link #yizmodqzk$FE_GET_HEALTH_DATA} DataParameter（健康增量）</li>
 *   <li>在 {@code defineSynchedData} 中注册默认值 0F</li>
 *   <li>修改 {@code getHealth/isAlive/isDeadOrDying} 返回值（应用 delta 截断）</li>
 *   <li>tick 中 delta 衰减（每 100 tick 朝 0 方向移动 1 点）</li>
 *   <li>NBT 持久化 delta</li>
 *   <li>die 时清理 delta</li>
 *   <li>在 {@code setHealth} 中拦截治疗（禁疗检查）</li>
 *   <li>实现 {@link HealthDataBridge} 接口</li>
 * </ol>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements HealthDataBridge {

    // ==================== DataParameter 定义 ====================

    /** 健康增量 DataParameter。有效血量上限 = maxHealth + delta */
    @Unique
    private static final EntityDataAccessor<Float> yizmodqzk$FE_GET_HEALTH_DATA =
        SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);

    @Unique
    private static final int yizmodqzk$DELTA_DECAY_INTERVAL = 100;

    // ==================== HealthDataBridge 接口实现 ====================

    @Override
    public float yizmodqzk$getHealthDelta() {
        // 通过 SynchedEntityData 访问（已自动客户端同步）
        return ((LivingEntity) (Object) this).getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
    }

    @Override
    public void yizmodqzk$setHealthDelta(float delta) {
        ((LivingEntity) (Object) this).getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, delta);
    }

    // ==================== defineSynchedData ====================

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void yizmodqzk$onDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(yizmodqzk$FE_GET_HEALTH_DATA, 0F);
    }

    // ==================== getHealth / isAlive / isDeadOrDying 改写 ====================

    /**
     * 修改 getHealth() 返回值。
     * 优先级：无敌检查 > delta 截断
     *   - 玩家无敌 → 返回 max(1, maxHealth)
     *   - delta 非零 → 返回 min(原始血量, maxHealth + delta)
     */
    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyGetHealth(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;

        // 1. 玩家无敌检查（PlayerMixin 实现 InvulnerableDataBridge）
        if (self instanceof InvulnerableDataBridge iv && iv.yizmodqzk$isInvulnerable()) {
            cir.setReturnValue(Math.max(1.0F, self.getMaxHealth()));
            return;
        }

        // 2. delta 截断
        float delta = self.getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
        if (delta != 0) {
            float original = cir.getReturnValueF();
            cir.setReturnValue(Math.min(original, self.getMaxHealth() + delta));
        }
    }

    /**
     * 修改 isAlive() 返回值。
     * 当 delta 非零时：以修正后的血量判定
     */
    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyIsAlive(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        float delta = self.getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
        if (delta != 0) {
            cir.setReturnValue(self.getHealth() > 0);
        }
    }

    /**
     * 修改 isDeadOrDying() 返回值。
     * 当 delta 非零时：以修正后的血量判定
     */
    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyIsDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        float delta = self.getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
        if (delta != 0) {
            cir.setReturnValue(self.getHealth() <= 0);
        }
    }

    // ==================== tick / die 注入 ====================

    @Inject(method = "tick", at = @At("TAIL"))
    private void yizmodqzk$onTick(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) return;

        if (entity.tickCount % yizmodqzk$DELTA_DECAY_INTERVAL == 0 && !entity.isDeadOrDying()) {
            float delta = entity.getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
            if (delta <= -1) {
                entity.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, delta + 1.0F);
            } else if (delta >= 1) {
                entity.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, delta - 1.0F);
            } else if (delta != 0) {
                entity.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, 0F);
            }
        }

        HealthModificationScheduler.tick(entity);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void yizmodqzk$onDie(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        entity.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, 0F);
        HealthModificationScheduler.removeAll(entity);
    }

    // ==================== NBT 持久化 ====================

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void yizmodqzk$addAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        float delta = self.getEntityData().get(yizmodqzk$FE_GET_HEALTH_DATA);
        if (delta != 0) {
            tag.putFloat("yizmodqzk:health_delta", delta);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void yizmodqzk$readAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("yizmodqzk:health_delta", Tag.TAG_FLOAT)) {
            LivingEntity self = (LivingEntity) (Object) this;
            float delta = tag.getFloat("yizmodqzk:health_delta");
            // delta 只能为 ≤0
            self.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, Math.min(0, delta));
        }
    }

    // ==================== setHealth 禁疗拦截 ====================

    /**
     * 治疗拦截：在 setHealth() 的参数中注入禁疗检查。
     * 当治疗量大于 0 时，根据禁疗系数削减。
     *
     * <p>这是 SynchedEntityData 层拦截的替代方案——在 LivingEntity 层面做，
     * 避免在 SynchedEntityData.set() 中无法反向查找实体的困境。</p>
     *
     * <p>注意：实体构造期间也会调用 setHealth，此时属性可能尚未就绪。
     * 如果禁疗计算抛出异常，直接返回原始值，不拦截。</p>
     */
    @ModifyVariable(method = "setHealth", at = @At("HEAD"), argsOnly = true)
    private float yizmodqzk$modifyHealthForHealBan(float newHealth) {
        LivingEntity self = (LivingEntity) (Object) this;
        float current = self.getHealth();
        // 只拦截治疗（新值 > 当前值）
        if (newHealth <= current) {
            return newHealth;
        }

        // 实体构造保护：如果当前血量为 0（或默认值以下），说明实体尚未初始化完成，跳过禁疗
        if (current <= 0.5f) {
            return newHealth;
        }

        try {
            float banFactor = yizmodqzk$getEffectiveBanFactor(self);
            if (banFactor <= 0) {
                return newHealth;
            }

            if (banFactor >= 1.0f) {
                return current;
            }

            float healing = newHealth - current;
            return current + healing * (1.0f - banFactor);
        } catch (Exception e) {
            // 属性未就绪等异常情况：不拦截
            return newHealth;
        }
    }

    /**
     * 计算实体的综合禁疗系数（0.0~1.0）。
     *   - 临时禁疗：来自攻击者的眷恋属性（5 秒有效期）
     *   - 被动禁疗：目标自身的眷恋属性（永久）
     */
    @Unique
    private static float yizmodqzk$getEffectiveBanFactor(LivingEntity entity) {
        // 临时禁疗（来自攻击者施加）
        float tempBan = net.minecraft.client.yiz.tool.health.HealBanHandler.getBanFactor(entity);

        // 被动禁疗（目标自身的眷恋属性）
        // 使用 getAttribute() 而非 getAttributeValue()，避免实体没有此属性时抛出异常
        var attrInstance = entity.getAttribute(
            net.minecraft.client.yiz.attribute.ModAttributes.ATTACHMENT);
        float attrBan = 0;
        if (attrInstance != null) {
            attrBan = (float) Math.min(1.0, attrInstance.getValue() / 10.0);
        }

        return Math.max(tempBan, attrBan);
    }
}
