package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.tool.abolish.EntityAbolitionStateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体废除服务端 Mixin — 阻止废除实体加入服务端世界。
 *
 * <p>这是四层架构中第二层（Mixin）的服务端部分，在实体加入世界的
 * 最早期入口拦截——实体永远不会被加入 EntityList、不会被 tick、
 * 不会被同步到客户端。</p>
 *
 * <p>注入点：{@link ServerLevel#addEntity(Entity)} —
 * 这是所有实体加入服务端世界的必经之路（包括自然生成、刷怪蛋、
 * 命令召唤等）。</p>
 */
@Mixin(ServerLevel.class)
public abstract class EntityAbolitionServerMixin {

    /**
     * 服务端实体加入拦截。
     * <p>如果实体类型在废除列表中，返回 false（类似实体未成功加入），
     * 调用方通常会在 addFreshEntity 中检查返回值来决定后续操作。</p>
     * <p>注意：addEntity 返回 boolean，false 表示实体未被加入。</p>
     */
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onAddEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (EntityAbolitionStateManager.isEntityAbolished(entity)) {
            cir.setReturnValue(false);
        }
    }
}
