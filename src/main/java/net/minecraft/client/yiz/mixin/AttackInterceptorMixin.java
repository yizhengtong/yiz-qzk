package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.tool.damage.AttackContext;
import net.minecraft.client.yiz.tool.damage.DamageResult;
import net.minecraft.client.yiz.tool.damage.DamageTag;
import net.minecraft.client.yiz.tool.damage.DirectAttackExecutor;
import net.minecraft.client.yiz.tool.health.DirectHealthModExecutor;
import net.minecraft.world.damagesource.DamageSource;
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
     * 在 attack() 方法开头注入。
     * 拦截试图攻击的实体，并检查是否含有强制执行标签。
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onAttackStart(Entity target, CallbackInfo ci) {
        Player attacker = (Player) (Object) this;

        // 非生物目标不处理
        if (!(target instanceof LivingEntity)) return;

        // 1. 获取攻击上下文
        AttackContext context = AttackContext.create(attacker, target);

        // 2. 检测是否包含强制执行标签
        boolean hasTrueDamage = context.hasEnforcementTag(DamageTag.TRUE_DAMAGE);
        boolean hasArmorPiercing = context.hasEnforcementTag(DamageTag.ARMOR_PIERCING);

        if (hasTrueDamage || hasArmorPiercing) {
            // 创建效果上下文
            EffectContext effectContext = EffectContext.create(attacker, target);

            // 创建伤害结果
            DamageResult damage = new DamageResult(
                10.0, // 默认伤害值，应由具体效果提供
                10.0,
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
            ci.cancel();
            yizmodqzk$LOGGER.debug("Forced attack intercepted: trueDamage={}, armorPiercing={}",
                hasTrueDamage, hasArmorPiercing);
        }
    }

    /**
     * 在 hurt() 方法开头注入。
     * 用于对 damageSource 进行修改或记录。
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onHurt(DamageSource source, float amount, CallbackInfo ci) {
        // 可选：在此处理伤害前的逻辑
        // 当前为空实现，预留扩展点
    }
}
