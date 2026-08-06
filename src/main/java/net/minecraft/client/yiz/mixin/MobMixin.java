package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob Mixin — 通用攻击方消费（最初梦幻等攻方属性）。
 *
 * <p>拦截 {@link Mob#doHurtTarget}（mob 近战攻击）RETURN：攻击者（本 Mob）带最初梦幻等属性时，
 * 即使目标免疫/无敌（hurt 返回 false 不走 super.hurt，onHurtPre 不触发），也由攻击方直接应用伤害
 * （{@link EntityASMUtil#applyDreamDamage} 直接改目标真实血量）——「确保命中」。</p>
 *
 * <p>配合 Player.attack（AttackInterceptorMixin）与辖界者 hit（下游调同一 API），实现前置库通用攻击方消费。</p>
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void yizmodqzk$onDoHurtTarget(net.minecraft.world.entity.Entity target,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && target instanceof LivingEntity living) {
            EntityASMUtil.applyDreamDamage((LivingEntity) (Object) this, living);
        }
    }
}
