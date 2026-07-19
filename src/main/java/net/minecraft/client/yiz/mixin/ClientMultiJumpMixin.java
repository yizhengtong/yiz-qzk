package net.minecraft.client.yiz.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.handler.MultiJumpTracker;
import net.minecraft.client.yiz.network.C2SMultiJumpPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

/**
 * 多段跳客户端分发 Mixin — 消费 {@code YizAttributes.JUMP_COUNT}（仅本地玩家）。
 *
 * <p>注入 {@link LocalPlayer#aiStep} HEAD：每次物理按下跳跃键（{@code keyJump.consumeClick()}），
 * 若玩家在空中且还有多段跳次数 → 消耗一次，用 JUMP_HEIGHT 反解 Y 初速设置 deltaMovement，
 * 发 C2S 请求服务端权威消耗。</p>
 *
 * <h3>手感：点按一次跳一次</h3>
 * <p>{@code consumeClick()} 每次物理按下消费一次，MC 1.21 按住期间不累加 clickCount，所以按住
 * 不会持续触发。落地起跳由原版 jumpFromGround 处理，本 Mixin 只处理"空中再跳"。</p>
 *
 * <h3>内置 CD（防长按）</h3>
 * <p>每次多段跳后进入 {@link MultiJumpTracker#JUMP_COOLDOWN_TICKS}（5 tick）冷却。
 * 纯客户端手感限制，服务端次数有限（落地恢复）是最终兜底。</p>
 *
 * <p>语义对齐 yizxian {@code MixinLocalPlayerExtraJump}（阶段3B 将删除 yizxian 版本），
 * 但去掉心之翅展翅优先级分支（心之翅阶段3C 直接删）。</p>
 */
@Mixin(LocalPlayer.class)
public abstract class ClientMultiJumpMixin {

    /** 多段跳内置 CD（客户端本地，按 LocalPlayer 实例记录）。 */
    @Unique
    private static final WeakHashMap<LocalPlayer, Integer> JUMP_CD = new WeakHashMap<>();

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void yizmodqzk$onJumpClick(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        // 先递减本 tick 的多段跳 CD（无论是否点击）
        int cd = JUMP_CD.getOrDefault(self, 0);
        if (cd > 0) JUMP_CD.put(self, cd - 1);

        // 点按一次 = 一次跳跃意图。MC 1.21 按住期间不会持续累加 clickCount
        if (!Minecraft.getInstance().options.keyJump.consumeClick()) return;

        // 落地按下跳跃键由原版走 jumpFromGround 起跳；本 Mixin 只处理"空中再跳"
        if (self.onGround()) return;
        // 已在飞行（鞘翅滑翔中）不再触发
        if (self.isFallFlying()) return;
        if (self.isInWater()) return;        // 游泳上浮走原版
        if (self.isPassenger()) return;      // 骑乘
        if (self.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)) return;

        // CD 中不触发（consumeClick 已消费清积压，避免 CD 结束瞬间连发）
        if (JUMP_CD.getOrDefault(self, 0) > 0) return;

        // 有可用多段跳次数才触发
        if (!MultiJumpTracker.hasJump(self)) return;

        // 乐观预测：用 JUMP_HEIGHT 反解 Y 初速（手感即时）。服务端权威消耗纠正
        int height = MultiJumpTracker.jumpHeight(self);
        double vy = MultiJumpTracker.velocityFromHeight(height);
        self.setDeltaMovement(self.getDeltaMovement().x, vy, self.getDeltaMovement().z);
        self.hurtMarked = true;
        C2SMultiJumpPayload.send();

        // 进入多段跳内置 CD（防长按快速消耗）
        JUMP_CD.put(self, MultiJumpTracker.JUMP_COOLDOWN_TICKS);
    }
}
