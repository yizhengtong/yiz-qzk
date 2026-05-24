package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.api.CounterAttackRegistry;
import net.minecraft.client.yiz.api.DamageReductionRegistry;
import net.minecraft.client.yiz.api.KnockbackImmunityRegistry;
import net.minecraft.client.yiz.api.ProjectileImmunityRegistry;
import net.minecraft.client.yiz.api.UndyingRegistry;
import net.minecraft.client.yiz.bridge.HealthDataBridge;
import net.minecraft.client.yiz.bridge.InvulnerableDataBridge;
import net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.yiz.tool.health.HealBanConfig;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.client.yiz.tool.health.HealthModificationScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.yiz.tizMod;
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

        // Heal ban tick 级强制（每 10 tick 检测各 Float 通道是否有未经授权的增长）
        if (entity.tickCount % 10 == 0) {
            HealBanHandler.enforceTick(entity);
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onDie(net.minecraft.world.damagesource.DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 保护态：阻止死亡
        if (net.minecraft.client.yiz.core.PlayerClassSwapper.isProtectedByUuid(entity.getStringUUID())) {
            ci.cancel();
            return;
        }

        entity.getEntityData().set(yizmodqzk$FE_GET_HEALTH_DATA, 0F);
        HealthModificationScheduler.removeAll(entity);
        HealBanConfig.remove(entity);
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

    // ==================== hurt() 物品属性伤害修正 ====================

    /**
     * 在 hurt() 入口修改原始伤害值，应用物品的 %伤害增幅 和 %伤害减免。
     *
     * <p>%伤害增幅：来自攻击者（damage source entity）主手+副手物品汇总，乘法放大原始伤害。</p>
     * <p>%伤害减免：来自目标（this）主手+副手物品汇总，乘法削减原始伤害。</p>
     * <p>两者在护甲/附魔减伤之前应用，确保与原版减伤体系自然叠加。</p>
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float yizmodqzk$modifyHurtAmount(float amount, DamageSource source) {
        if (amount <= 0) return amount;
        LivingEntity self = (LivingEntity) (Object) this;

        // %伤害增幅 — 攻击者物品
        if (source.getEntity() instanceof LivingEntity attacker) {
            double amp = ItemAttributeHandler.getTotalDamageAmplification(attacker);
            if (amp != 0) {
                amount *= (1.0F + (float) amp);
            }
        }

        // %伤害减免 — 目标物品
        double red = ItemAttributeHandler.getTotalDamageReduction(self);
        if (red != 0) {
            amount *= (1.0F - (float) red);
        }

        return Math.max(0, amount);
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
        // 如果 ASM 已在 heal() 中处理过禁疗，跳过
        if (EntityASMUtil.consumeHealBanFlag()) {
            return newHealth;
        }

        LivingEntity self = (LivingEntity) (Object) this;

        // === 保护态生命值纠正（ASM 原逻辑移入 Mixin）：确保血量 ≥1, 非 NaN ===
        if (net.minecraft.client.yiz.core.PlayerClassSwapper.isProtectedByUuid(self.getStringUUID())) {
            return EntityASMUtil.clampProtectedHealth(newHealth);
        }

        float current = self.getHealth();

        // === 伤害减免（Agent 优先，Mixin 兜底） ===
        if (newHealth < current) {
            // Agent 已处理则跳过，避免重复减免
            if (DamageReductionRegistry.consumeReductionApplied()) {
                return newHealth;
            }
            return DamageReductionRegistry.applyBeforeSetHealth(self, newHealth);
        }

        if (newHealth <= current) return newHealth;
        if (current <= 0.5f) return newHealth;

        try {
            float healing = newHealth - current;

            // 1. 检查 API 禁疗配置（百分比 + 固定值）
            var config = HealBanConfig.get(self);
            if (config != null) {
                healing = config.apply(healing);
            }

            // 2. 检查临时禁疗（来自攻击者主动施加）
            float tempBan = net.minecraft.client.yiz.tool.health.HealBanHandler.getBanFactor(self);
            if (tempBan > 0) {
                healing *= (1.0f - Math.min(1.0f, tempBan));
            }

            return current + Math.max(0, healing);
        } catch (Exception e) {
            return newHealth;
        }
    }

    /**
     * setHealth 完成后更新禁疗跟踪基线。
     * 确保 ASM/事件/混合注入三层处理后，tick 级强制不会重复禁疗。
     */
    @Inject(method = "setHealth", at = @At("RETURN"))
    private void yizmodqzk$onSetHealth(CallbackInfo ci) {
        HealBanHandler.updateBaseline((LivingEntity) (Object) this);
    }

    // ==================== 投射物免疫 ====================

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onHurtProjectileImmune(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (ProjectileImmunityRegistry.isImmune(self)) {
                cir.setReturnValue(false);
            }
        }
    }

    // ==================== 复活系统 ====================

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$onCheckTotemDeathProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 图腾已复活
        LivingEntity self = (LivingEntity) (Object) this;
        float targetHealth = UndyingRegistry.tryRevive(self, source);
        if (targetHealth > 0) {
            cir.setReturnValue(true); // 阻止死亡
        }
    }

    // ==================== 回击系统 ====================

    @Inject(method = "hurt", at = @At("RETURN"))
    private void yizmodqzk$onHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof Player player)) return;

        Entity srcEntity = source.getEntity();
        tizMod.LOGGER.debug("[CounterAttack] DBG: player={} sourceEntity={} amount={}",
                player.getName().getString(),
                srcEntity != null ? srcEntity.getName().getString() : "null",
                amount);

        if (!(srcEntity instanceof LivingEntity attacker)) return;
        if (attacker == player) return;

        tizMod.LOGGER.debug("[CounterAttack] FIRE: {} -> {}", player.getName().getString(), attacker.getName().getString());
        CounterAttackRegistry.tryCounterAttack(player, attacker);
    }

    // ==================== 击退免疫 ====================

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onKnockback(double d0, double d1, double d2, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (KnockbackImmunityRegistry.isImmune(self)) {
            ci.cancel();
        }
    }

    // ==================== remove 保护态拦截 ====================

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onRemove(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (net.minecraft.client.yiz.core.PlayerClassSwapper.isProtectedByUuid(self.getStringUUID())) {
            ci.cancel();
        }
    }
}
