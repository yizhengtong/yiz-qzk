package net.minecraft.client.yiz.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.minecraft.client.yiz.api.FlightAbilityRegistry;
import net.minecraft.client.yiz.api.FlightOptimizationRegistry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 飞行权限 + 惯性优化
 * - mayFly() 强制返回 true（星光体绝对飞行权）
 * - travel() TAIL 无输入时清零水平速度
 */
@Mixin(Player.class)
public abstract class FlightOptimizationMixin {

    /** 绝对飞行权：每 tick 强制设置 mayfly = true */
    @Inject(method = "tick", at = @At("HEAD"))
    private void yizmodqzk$onTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (FlightAbilityRegistry.shouldHaveFlight(player)) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void yizmodqzk$onTravelTail(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.getAbilities().flying) return;
        if (!FlightOptimizationRegistry.shouldOptimize(player)) return;

        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
    }
}
