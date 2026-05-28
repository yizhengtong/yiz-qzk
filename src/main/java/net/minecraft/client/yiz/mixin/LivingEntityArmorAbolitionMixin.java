package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 拦截 {@link LivingEntity#getArmorValue()}。
 * <p>
 * 当 {@link AbolitionStateManager#isArmorAbolished()} 为 true 时返回 0，
 * 使所有玩家穿戴的护甲不提供任何防御。
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityArmorAbolitionMixin {

    @Inject(method = "getArmorValue", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$onGetArmorValue(CallbackInfoReturnable<Integer> cir) {
        if (AbolitionStateManager.isArmorAbolished()) {
            cir.setReturnValue(0);
        }
    }
}
