package net.minecraft.client.yiz.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.minecraft.client.yiz.api.NoCollisionRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 碰撞免疫：参考旁观者模式 isSpectator → 跳过 push
 */
@Mixin(Entity.class)
public class NoCollisionMixin {

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onPush(Entity other, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof LivingEntity le && NoCollisionRegistry.isImmune(le)) {
            ci.cancel();
            return;
        }
        if (other instanceof LivingEntity le && NoCollisionRegistry.isImmune(le)) {
            ci.cancel();
        }
    }

    @Inject(method = "canCollideWith", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$onCanCollideWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            Entity self = (Entity) (Object) this;
            if (self instanceof LivingEntity le && NoCollisionRegistry.isImmune(le)) {
                cir.setReturnValue(false);
                return;
            }
            if (other instanceof LivingEntity le && NoCollisionRegistry.isImmune(le)) {
                cir.setReturnValue(false);
            }
        }
    }
}
