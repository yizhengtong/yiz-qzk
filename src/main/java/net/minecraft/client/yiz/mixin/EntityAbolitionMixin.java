package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.tool.abolish.EntityAbolitionStateManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体废除 Mixin — 主动移除废除实体。
 *
 * <p>核心逻辑：废除实体在第一个 tick 时被主动 discard()，
 * 从世界中移除。服务端 + 客户端双端生效。</p>
 *
 * <p>流程：
 * <ol>
 *   <li>实体加入世界（自然生成/刷怪蛋/命令）</li>
 *   <li>第一个 tick → 检查是否在废除列表中</li>
 *   <li>是 → 调用 discard() 立即移除，取消 tick</li>
 *   <li>实体从 EntityList 清除，不再渲染、不再碰撞、不再同步</li>
 * </ol>
 *
 * <p>注意：VTable 层不废除 remove/discard/kill，确保这些方法能正常执行。</p>
 */
@Mixin(Entity.class)
public abstract class EntityAbolitionMixin {

    /**
     * 废除实体 tick 入口 → 主动 discard 并跳过。
     * <p>只对非玩家实体生效（玩家不能被 discard）。</p>
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onEntityTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!EntityAbolitionStateManager.isEntityAbolished(self)) return;

        // 玩家不走 discard 路径
        if (self instanceof net.minecraft.world.entity.player.Player) {
            ci.cancel();
            return;
        }

        // 服务端 + 单机：主动从世界移除
        if (!self.level().isClientSide()) {
            self.discard();
        }
        ci.cancel();
    }

    /**
     * baseTick 也跳过。
     */
    @Inject(method = "baseTick", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onBaseTick(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (EntityAbolitionStateManager.isEntityAbolished(self)) {
            ci.cancel();
        }
    }
}
