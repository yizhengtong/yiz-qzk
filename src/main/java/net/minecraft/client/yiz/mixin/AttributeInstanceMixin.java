package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.client.yiz.tool.attribute.EntityAttributeGate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AttributeInstance Mixin — 受保护属性防移除。
 *
 * <p>拦截 {@link AttributeInstance#removeModifier(ResourceLocation)} —— 这是所有移除路径的唯一汇聚点
 * （{@code removeModifier(AttributeModifier)}、{@code removeModifiers()}、{@code addOrReplacePermanentModifier}
 * 都委派到它）。对 {@link EntityAttributeGate#isProtectedId} 判定的受保护 id（{@code yizmodqzk:prot_*}），
 * 做「调用栈 + 包名」鉴权：受信任调用方（本家 / 引擎帧 / 白名单 modid）放行，其他模组当场拒绝。</p>
 *
 * <p>非受保护 id（物品装备的 {@code item_/attr_} 等）直接放行，零额外开销，不影响原版装备检测。</p>
 */
@Mixin(AttributeInstance.class)
public abstract class AttributeInstanceMixin {

    /**
     * 受保护属性移除守卫。
     * <p>removeModifier 有 (AttributeModifier) 与 (ResourceLocation) 两个重载，此处用完整描述符
     * 精确匹配 ResourceLocation 版本（另一个重载会委派到本方法）。</p>
     */
    @Inject(method = "removeModifier(Lnet/minecraft/resources/ResourceLocation;)Z",
            at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$guardProtectedRemove(ResourceLocation id,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!EntityAttributeGate.isProtectedId(id)) return;  // 非受保护 id 零开销放行
        if (EntityAttributeGate.isCallerTrusted()) return;    // 本家/引擎/白名单放行
        tizMod.LOGGER.warn("[AttributeGate] 拒绝外部移除受保护属性: {}", id);
        cir.setReturnValue(false);                            // 阻止移除（返回 false = 未移除）
    }
}
