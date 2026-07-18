package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.bridge.InvulnerableDataBridge;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Player Mixin
 * 实现玩家无敌模式。
 *
 * <p>定义 {@link #yizmodqzk$FE_INVULNERABLE_DATA} DataParameter，
 * 开启时：
 * <ul>
 *   <li>{@code getHealth()} 恒返回 {@code max(1, maxHealth)}（见 {@link LivingEntityMixin}）</li>
 *   <li>{@code hurt()} 被取消</li>
 * </ul>
 *
 * <p>注意：{@code getHealth()} 在 Player 中没有覆写（继承自 LivingEntity），
 * 因此无敌检查放在 {@link LivingEntityMixin#yizmodqzk$modifyGetHealth} 中，
 * 通过 {@link InvulnerableDataBridge} 接口访问。
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements InvulnerableDataBridge {

    @Unique
    private static final EntityDataAccessor<Boolean> yizmodqzk$FE_INVULNERABLE_DATA =
        SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);

    // ==================== InvulnerableDataBridge ====================

    @Override
    public boolean yizmodqzk$isInvulnerable() {
        return ((Player) (Object) this).getEntityData().get(yizmodqzk$FE_INVULNERABLE_DATA);
    }

    @Override
    public void yizmodqzk$setInvulnerable(boolean invul) {
        ((Player) (Object) this).getEntityData().set(yizmodqzk$FE_INVULNERABLE_DATA, invul);
    }

    // ==================== defineSynchedData ====================

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void yizmodqzk$onDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(yizmodqzk$FE_INVULNERABLE_DATA, false);
    }

    // ==================== 攻击冷却缩减 ====================

    /**
     * 修改攻击冷却延迟：原值 × (1 - 冷却缩减％/100)。
     * 100%=0 延迟(立即重置)，50%=一半延迟。
     */
    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$modifyAttackDelay(CallbackInfoReturnable<Float> cir) {
        Player self = (Player)(Object)this;
        var inst = self.getAttribute(
            net.minecraft.client.yiz.attribute.YizAttributes.COOLDOWN_REDUCTION);
        if (inst == null) return;
        double reduction = inst.getValue();
        if (reduction <= 0) return;
        float original = cir.getReturnValue();
        if (reduction >= 100.0) {
            cir.setReturnValue(0f);
            return;
        }
        cir.setReturnValue((float)(original * (1.0 - reduction / 100.0)));
    }

    // ==================== hurt 取消 ====================

    /**
     * 无敌时取消伤害。
     * Player 覆写了 hurt()，因此此注入可以找到目标方法。
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onHurt(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getEntityData().get(yizmodqzk$FE_INVULNERABLE_DATA)) {
            cir.setReturnValue(false); // 取消伤害
        }
    }
}
