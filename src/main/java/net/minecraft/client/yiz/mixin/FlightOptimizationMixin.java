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

    /** 绝对飞行权 + 1.2x 速度：每 tick 强制设置 */
    @Inject(method = "tick", at = @At("HEAD"))
    private void yizmodqzk$onTick(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (FlightAbilityRegistry.shouldHaveFlight(player)) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            player.getAbilities().setFlyingSpeed(0.06f);
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void yizmodqzk$onTravelTail(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.getAbilities().flying) return;
        if (!FlightOptimizationRegistry.shouldOptimize(player)) return;

        // 一旦停止移动输入，瞬间锁定水平速度为 0，保留 Y 轴（飞行上浮/下降）
        if (Math.abs(travelVector.x) < 0.001 && Math.abs(travelVector.z) < 0.001) {
            Vec3 currentMotion = player.getDeltaMovement();
            player.setDeltaMovement(0, currentMotion.y, 0);
        }
    }
}
