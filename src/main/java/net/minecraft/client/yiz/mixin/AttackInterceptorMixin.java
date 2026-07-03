package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.core.AttackTargetLock;
import net.minecraft.client.yiz.api.DamageAttributeRegistry;
import net.minecraft.client.yiz.api.HealBanAttributeRegistry;
import net.minecraft.client.yiz.api.SpecialDamageAttributeRegistry;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.tool.health.HealBanConfig;
import net.minecraft.client.yiz.tool.damage.AttackContext;
import net.minecraft.client.yiz.tool.damage.DamageResult;
import net.minecraft.client.yiz.tool.damage.DamageTag;
import net.minecraft.client.yiz.tool.damage.DirectAttackExecutor;
import net.minecraft.client.yiz.tool.health.DirectHealthModExecutor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 攻击拦截 Mixin
 * 在 Player.attack() 方法开头注入，检查是否含有强制执行标签。
 * 若检测到真实伤害/穿甲/健康值修改标签，则强制执行并取消原方法。
 */
@Mixin(Player.class)
public abstract class AttackInterceptorMixin {

    @Unique
    private static final Logger yizmodqzk$LOGGER = LoggerFactory.getLogger("YizModQZK");

    /**
     * 标记 HEAD 注入已拦截本次攻击的玩家 UUID。
     * 使用 ThreadLocal 避免并发问题。
     */
    @Unique
    private static final ThreadLocal<Boolean> yizmodqzk$attackIntercepted = ThreadLocal.withInitial(() -> false);

    /**
     * 在 attack() 方法开头注入。
     * 拦截试图攻击的实体，并检查是否含有强制执行标签。
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onAttackStart(Entity target, CallbackInfo ci) {
        Player attacker = (Player) (Object) this;
        yizmodqzk$attackIntercepted.set(false);

        // 锁定原始攻击目标（最早时机，供下游模组通过 API 查询）
        AttackTargetLock.captureOnAttack(attacker, target);

        // 非生物目标不处理
        if (!(target instanceof LivingEntity)) return;

        // 1. 获取攻击上下文
        AttackContext context = AttackContext.create(attacker, target);

        // 2. 检测是否包含强制执行标签
        boolean hasTrueDamage = context.hasEnforcementTag(DamageTag.TRUE_DAMAGE);
        boolean hasArmorPiercing = context.hasEnforcementTag(DamageTag.ARMOR_PIERCING);
        boolean hasDirectHealthMod = context.hasEnforcementTag(DamageTag.DIRECT_HEALTH_MOD);

        // 2a. DIRECT_HEALTH_MOD 优先：走健康值修改管理器管道
        if (hasDirectHealthMod) {
            EffectContext effectContext = EffectContext.create(attacker, target);
            DirectHealthModExecutor.executeDirectHealthMod(attacker, target, effectContext);
            yizmodqzk$attackIntercepted.set(true);
            ci.cancel();
            yizmodqzk$LOGGER.debug("Direct health modification intercepted");
            return;
        }

        if (hasTrueDamage || hasArmorPiercing) {
            // 创建效果上下文
            EffectContext effectContext = EffectContext.create(attacker, target);

            // 读取武器实际攻击力（含物品属性修饰符、药水效果等）
            // 真伤/破甲以武器自身伤害为基础，不设默认附加值。
            // 如需额外附加伤害，由 DamageTagProvider 效果在 execute() 中
            // 通过 DamageResult.multiply() / .add() 显式叠加。
            float weaponDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);

            DamageResult damage = new DamageResult(
                weaponDamage,
                weaponDamage,
                attacker.damageSources().playerAttack(attacker).typeHolder().unwrapKey().map(ResourceKey::location).orElse(null)
            );

            // 添加标签
            if (hasTrueDamage) {
                damage = damage.withTag(DamageTag.TRUE_DAMAGE);
            }
            if (hasArmorPiercing) {
                damage = damage.withTag(DamageTag.ARMOR_PIERCING);
            }

            // 3. 强制执行攻击
            DirectAttackExecutor.executeForcedAttack(attacker, target, context, damage);

            // 4. 取消原版 attack 方法
            yizmodqzk$attackIntercepted.set(true);
            ci.cancel();
            yizmodqzk$LOGGER.debug("Forced attack intercepted: trueDamage={}, armorPiercing={}",
                hasTrueDamage, hasArmorPiercing);
        }
    }

    /**
     * 在 hurt() 方法开头注入。
     * 用于对 damageSource 进行修改或记录。
     * hurt() 返回 boolean，因此使用 CallbackInfoReturnable。
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 可选：在此处理伤害前的逻辑
        // 当前为空实现，预留扩展点
    }

    /**
     * 在 attack() 返回时注入（vanilla 攻击完成后）。
     * <p>
     * 扫描攻击者身上所有已注册的伤害/禁疗属性，自动附加效果。
     * </p>
     * <ul>
     *   <li>伤害属性 → 额外真实伤害（三層系统）</li>
     *   <li>百分比禁疗属性 → 目标获得百分比治疗削减</li>
     *   <li>固定值禁疗属性 → 目标获得固定值治疗削减</li>
     * </ul>
     */
    @Inject(method = "attack", at = @At("RETURN"))
    private void yizmodqzk$onAttackReturn(Entity target, CallbackInfo ci) {
        // 如果 HEAD 已经拦截了本次攻击（强制执行），跳过属性伤害
        if (yizmodqzk$attackIntercepted.get()) {
            yizmodqzk$attackIntercepted.remove();
            return;
        }

        if (!(target instanceof LivingEntity livingTarget)) return;

        Player attacker = (Player) (Object) this;

        // 1. 伤害属性 → 额外真实伤害
        float attrDamage = DamageAttributeRegistry.getTotalValue(attacker);
        if (attrDamage > 0) {
            YizModQZKAPI.damage(livingTarget, attrDamage, attacker);
        }

        // 2. 禁疗属性 → 为目标施加禁疗
        float banPercent = HealBanAttributeRegistry.getPercentTotal(attacker);
        float banFixed = HealBanAttributeRegistry.getFixedTotal(attacker);
        if (banPercent > 0 || banFixed > 0) {
            HealBanConfig.set(livingTarget, banPercent, banFixed);
        }

        // 3. 特殊伤害属性 → 真实伤害 / 破甲 / 破无敌帧
        float trueDmg = SpecialDamageAttributeRegistry.getTrueDamageTotal(attacker);
        if (trueDmg > 0) {
            YizModQZKAPI.trueDamage(livingTarget, trueDmg, attacker);
        }
        float apDmg = SpecialDamageAttributeRegistry.getArmorPiercingTotal(attacker);
        if (apDmg > 0) {
            if (SpecialDamageAttributeRegistry.hasPierceInvulnerability(attacker)) {
                YizModQZKAPI.armorPiercingAndPierceInvulnerabilityDamage(livingTarget, apDmg, attacker);
            } else {
                YizModQZKAPI.armorPiercingDamage(livingTarget, apDmg, attacker);
            }
        }
    }
}
