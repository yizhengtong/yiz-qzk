package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.api.DamageReductionRegistry;
import net.minecraft.client.yiz.api.DamageValueModifierRegistry;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.api.KnockbackImmunityRegistry;
import net.minecraft.client.yiz.api.ProjectileImmunityRegistry;
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
        // tick 计数器 +1（供法球系统读取）
        EntityASMUtil.incrementTickCount(entity);
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

    // ==================== hurt() 伤害修正 ====================

    /**
     * 在 hurt() 入口修改原始伤害值。
     * <p>伤害增幅/减免已迁移至 NeoForge 属性系统（generic_damage / damage_reduction），
     * 由 Phase C 属性注册后通过实体属性值驱动。</p>
     */
    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float yizmodqzk$modifyHurtAmount(float amount, DamageSource source) {
        if (amount <= 0) return amount;
        LivingEntity self = (LivingEntity) (Object) this;

        // === DamageValueModifierRegistry — 自定义伤害数值修改（前置处理） ===
        amount = DamageValueModifierRegistry.apply(self, source, amount);
        if (amount <= 0) return 0;

        // === 攻击者伤害增幅（按堆叠模式：MULTIPLY 逐项乘，ADD 求和后一次乘）===
        if (source.getEntity() instanceof LivingEntity attacker) {
            // 攻击计数器 +1（供法球系统读取）
            EntityASMUtil.incrementAttackCount(attacker);

            double distSq = attacker.distanceToSqr(self);
            float addSum = 0f;

            for (var holder : new net.minecraft.core.Holder[]{
                net.minecraft.client.yiz.attribute.YizAttributes.GENERIC_DAMAGE,
                (distSq <= 100.0)
                    ? net.minecraft.client.yiz.attribute.YizAttributes.MELEE_DAMAGE
                    : net.minecraft.client.yiz.attribute.YizAttributes.RANGED_DAMAGE
            }) {
                var inst = attacker.getAttribute(holder);
                if (inst == null) continue;
                double amp = inst.getValue();
                if (amp <= 0) continue;

                if (net.minecraft.client.yiz.attribute.YizAttributes.getStackMode(holder)
                        == net.minecraft.client.yiz.attribute.YizAttributes.StackMode.ADD) {
                    addSum += (float) amp;
                } else {
                    amount *= (1.0F + (float) amp);
                }
            }
            if (addSum > 0) amount *= (1.0F + addSum);

            // 护甲穿透：存下攻击者的百分比+固定穿透值，供目标 getArmorValue() 注入扣减
            var penPctInst = attacker.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.ARMOR_PENETRATION);
            var penFlatInst = attacker.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.ARMOR_PENETRATION_FLAT);
            float penPct = penPctInst != null ? (float) penPctInst.getValue() : 0f;
            float penFlat = penFlatInst != null ? (float) penFlatInst.getValue() : 0f;
            if (penPct > 0 || penFlat > 0) {
                net.minecraft.client.yiz.tool.health.EntityASMUtil.setArmorPenetration(penPct, penFlat);
            }
        }

        // 注：MAGIC_DAMAGE / SUMMON_DAMAGE 保留给后续魔法武器/召唤武器系统，暂不在此处消费。

        // === 熔岩/火焰防护（目标方属性）===
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            // 熔岩免疫：有任意免疫属性值 → 完全免疫火焰伤害（类似防火药水）
            var immPct = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.LAVA_IMMUNE_TIME);
            var immFlat = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.LAVA_IMMUNE_TIME_FLAT);
            if ((immPct != null && immPct.getValue() > 0)
                || (immFlat != null && immFlat.getValue() > 0)) {
                return 0;
            }
            // 熔岩减伤：先百分比再固定
            var lavaRedPct = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.LAVA_DAMAGE_REDUCTION);
            if (lavaRedPct != null) {
                double red = lavaRedPct.getValue();
                if (red > 0) amount *= (float) (1.0 - Math.min(1.0, red / 100.0));
            }
            var lavaRedFlat = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.LAVA_DAMAGE_REDUCTION_FLAT);
            if (lavaRedFlat != null) {
                double flat = lavaRedFlat.getValue();
                if (flat > 0) amount = Math.max(0, amount - (float) flat);
            }
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

        // 清除本次 hurt 的护甲穿透（getArmorValue 注入已使用）
        net.minecraft.client.yiz.tool.health.EntityASMUtil.clearArmorPenetration();

        // 减伤 / 格挡（扣血方向）
        if (newHealth < current) {
            // 原生减伤 先处理完整伤害量
            var reductionInst = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_REDUCTION);
            if (reductionInst != null) {
                double reduction = reductionInst.getValue();
                if (reduction > 0) {
                    float damage = current - newHealth;
                    damage *= (float) (1.0 - Math.min(1.0, reduction / 100.0));
                    newHealth = current - damage;
                }
            }
            // 格挡 在注册表之前，避免注册表 clamp 破坏致死信号
            var blockInst = self.getAttribute(
                net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_BLOCK);
            if (blockInst != null) {
                double block = blockInst.getValue();
                if (block > 0) {
                    float damage = current - newHealth;
                    damage = Math.max(0, damage - (float) block);
                    newHealth = current - damage;
                }
            }
            // 注册表减伤（饰品 EffectTag，yizxian 注册）
            newHealth = DamageReductionRegistry.applyBeforeSetHealth(self, newHealth);
            return newHealth;
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
            var inst = self.getAttribute(YizAttributes.PROJECTILE_IMMUNITY);
            if (inst != null) {
                double v = inst.getValue();
                if (v >= 100.0 || (v > 0 && Math.random() < v / 100.0)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }

    // ==================== 复活系统（属性驱动，每条命独立次数）====================

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$onCheckTotemDeathProtection(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 图腾已复活
        LivingEntity self = (LivingEntity) (Object) this;

        var undyingInst = self.getAttribute(YizAttributes.UNDYING);
        if (undyingInst == null) return;
        int max = (int) undyingInst.getValue();
        if (max <= 0) return;

        java.util.UUID uuid = self.getUUID();
        int remaining = EntityASMUtil.getUndyingCharges(uuid);
        if (remaining < 0) { // 首次使用，用属性值初始化
            remaining = max;
            EntityASMUtil.resetUndyingCharges(uuid, max);
        }
        if (remaining <= 0) return;

        EntityASMUtil.consumeUndyingCharge(uuid);
        self.setHealth(self.getMaxHealth());
        cir.setReturnValue(true);
    }

    // ==================== 反击系统（属性驱动） ====================

    @Inject(method = "hurt", at = @At("RETURN"))
    private void yizmodqzk$onHurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide()) return;
        if (!(self instanceof Player player)) return;

        Entity srcEntity = source.getEntity();
        if (!(srcEntity instanceof LivingEntity attacker)) return;
        if (attacker == player) return;

        // === 反击：受击 → 反击率判定 → 反击值×反击数 ===
        // ON_HURT 作为独立计数器存在（EntityASMUtil），不在此处门控反击系统
        double rate = player.getAttributeValue(YizAttributes.COUNTER_RATE);
        if (rate <= 0) return;

        if (Math.random() >= rate / 100.0) return;

        double value = player.getAttributeValue(YizAttributes.COUNTER_VALUE);
        if (value <= 0) value = 50.0; // 默认 50%
        double count = player.getAttributeValue(YizAttributes.COUNTER_COUNT);
        if (count < 1) count = 1;

        double playerAtk = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float counterDmg = (float) (playerAtk * value / 100.0);

        for (int i = 0; i < (int) count; i++) {
            attacker.hurt(player.damageSources().mobAttack(player), counterDmg);
        }
    }

    // ==================== 击退免疫 ====================

    // ==================== 击退免疫（属性驱动） ====================

    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onKnockback(double d0, double d1, double d2, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        var inst = self.getAttribute(YizAttributes.KNOCKBACK_IMMUNITY);
        if (inst != null) {
            double v = inst.getValue();
            if (v >= 100.0 || (v > 0 && Math.random() < v / 100.0)) {
                ci.cancel();
            }
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


    // ==================== 水下呼吸 ====================

    @Inject(method = "decreaseAirSupply", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyAirDecrease(int currentAir, CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        var pctInst = self.getAttribute(YizAttributes.WATER_BREATH_TIME);
        var flatInst = self.getAttribute(YizAttributes.WATER_BREATH_TIME_FLAT);
        double pct = pctInst != null ? pctInst.getValue() : 0;
        double flat = flatInst != null ? flatInst.getValue() : 0;
        if (pct <= 0 && flat <= 0) return;
        if (pct >= 100) { cir.setReturnValue(currentAir); return; } // 100% = 无限呼吸
        int maxAir = self.getMaxAirSupply();
        int effectiveMax = maxAir + (int) flat;
        if (pct > 0) effectiveMax = (int)(effectiveMax * (1.0 + pct / 100.0));
        int decreased = cir.getReturnValue();
        // 按有效上限比例减少扣气量
        double ratio = (double) maxAir / (double) Math.max(1, effectiveMax);
        cir.setReturnValue(currentAir - Math.max(0, (int)((currentAir - decreased) * ratio)));
    }

    // ==================== 步高 ====================

    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyStepHeight(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        float extra = 0f;
        // 原生属性值（饰品槽由 EquipmentAttributeSync 汇入，主手/副手/盔甲由原版自动汇入）
        var inst = self.getAttribute(YizAttributes.JUMP_SPEED);
        if (inst != null) extra += (float) inst.getValue();
        // 手动扫描装备槽（兜底：部分情况下原版可能不自动应用 custom attribute）
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            var stack = self.getItemBySlot(slot);
            var mods = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
            for (var entry : mods.modifiers()) {
                if (entry.attribute() != null && entry.attribute().is(YizAttributes.JUMP_SPEED)
                    && entry.modifier().operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                    extra += (float) entry.modifier().amount();
                }
            }
        }
        if (extra > 0) cir.setReturnValue(cir.getReturnValue() + extra);
    }

    // ==================== 护甲穿透 ====================

    @Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$applyArmorPenetration(CallbackInfoReturnable<Integer> cir) {
        float penPct = EntityASMUtil.peekArmorPenPct();
        float penFlat = EntityASMUtil.peekArmorPenFlat();
        if (penPct > 0 || penFlat > 0) {
            int armor = cir.getReturnValue();
            // 先百分比穿透
            if (penPct > 0) {
                armor = armor - (int)(armor * penPct / 100f);
            }
            // 再固定穿透
            if (penFlat > 0) {
                armor = armor - (int) penFlat;
            }
            cir.setReturnValue(Math.max(0, armor));
        }
    }
}
