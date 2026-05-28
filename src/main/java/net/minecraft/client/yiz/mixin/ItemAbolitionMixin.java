package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin 拦截 {@link Item} 的所有功能方法。
 *
 * <p>与 {@link net.minecraft.client.yiz.core.VTableReplace} 层形成双重保险：
 * <ul>
 *   <li>Mixin 层 — 运行时检查 {@link AbolitionStateManager}，可靠性高</li>
 *   <li>VTable 层 — JVM 级入口覆写，即使 Mixin 被绕过也能拦截</li>
 * </ul>
 * </p>
 */
@Mixin(Item.class)
public abstract class ItemAbolitionMixin {

    @Unique
    private boolean yizmodqzk$isAbolished() {
        Item self = (Item) (Object) this;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(self);
        return id != null && AbolitionStateManager.isItemAbolished(id);
    }

    // ══════════════════════════════════════════════════════════
    //  use() — 右键物品（对空气使用）
    // ══════════════════════════════════════════════════════════

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onUse(Level level, Player player, InteractionHand hand,
                                  CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (yizmodqzk$isAbolished()) {
            cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(hand)));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  useOn() — 右键物品（对方块使用）
    // ══════════════════════════════════════════════════════════

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onUseOn(UseOnContext context,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (yizmodqzk$isAbolished()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  hurtEnemy() — 攻击实体
    // ══════════════════════════════════════════════════════════

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (yizmodqzk$isAbolished()) {
            cir.setReturnValue(false);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  inventoryTick() — 背包内每 tick
    // ══════════════════════════════════════════════════════════

    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onInventoryTick(ItemStack stack, Level level, Entity entity,
                                            int slotId, boolean isSelected,
                                            CallbackInfo ci) {
        if (yizmodqzk$isAbolished()) {
            ci.cancel();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  releaseUsing() — 释放使用（弓/弩等蓄力物品）
    // ══════════════════════════════════════════════════════════

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onReleaseUsing(ItemStack stack, Level level, LivingEntity livingEntity,
                                           int timeCharged, CallbackInfo ci) {
        if (yizmodqzk$isAbolished()) {
            ci.cancel();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  appendHoverText() — 自定义 tooltip
    // ══════════════════════════════════════════════════════════

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onAppendHoverText(ItemStack stack, Item.TooltipContext context,
                                              List<Component> tooltipComponents,
                                              TooltipFlag tooltipFlag,
                                              CallbackInfo ci) {
        if (yizmodqzk$isAbolished()) {
            ci.cancel();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  onCraftedBy() — 合成时
    // ══════════════════════════════════════════════════════════

    @Inject(method = "onCraftedBy", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onCraftedBy(ItemStack stack, Level level, Player player,
                                        CallbackInfo ci) {
        if (yizmodqzk$isAbolished()) {
            ci.cancel();
        }
    }
}
