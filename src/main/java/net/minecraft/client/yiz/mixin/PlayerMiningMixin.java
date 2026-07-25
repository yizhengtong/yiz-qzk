package net.minecraft.client.yiz.mixin;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 挖掘属性 Mixin — 7 个挖掘属性，全部支持任意手持物品（含空手）。
 *
 * <h3>属性表</h3>
 * <ul>
 *   <li><b>挖掘类：镐/斧/铲/全</b>（0~1）— 控制可挖掘方块类别，与手持物品无关。</li>
 *   <li><b>挖掘等级</b>（≥0）— 控制掉落所需 Tier：0=木, 1=石, 2=铁, 3=钻石, 4=下界合金。</li>
 *   <li><b>免疫挖掘惩罚</b>（0~1）— 1=免疫空中/水中/挖掘疲劳全部负面效果。</li>
 *   <li><b>挖掘效率</b>（≥0, 1=1%）— 百分比加速，始终生效。</li>
 * </ul>
 */
@Mixin(Player.class)
public abstract class PlayerMiningMixin {

    private static double attr(Player p, Holder<Attribute> a) {
        var inst = p.getAttribute(a);
        return inst != null ? inst.getValue() : 0;
    }

    /** 任意一个挖掘类型属性覆盖此方块即返回 true。挖掘类：全 覆盖一切。 */
    private static boolean typeCovers(Player p, BlockState state) {
        if (attr(p, YizAttributes.MINING_PICKAXE) >= 1 && state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return true;
        if (attr(p, YizAttributes.MINING_AXE) >= 1 && state.is(BlockTags.MINEABLE_WITH_AXE)) return true;
        if (attr(p, YizAttributes.MINING_SHOVEL) >= 1 && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return true;
        if (attr(p, YizAttributes.MINING_ALL) >= 1) return true;
        return false;
    }

    // ═══════════════════════════════════════════════════════════
    //  getDigSpeed RETURN：免疫惩罚 + 类型覆盖 + 效率
    // ═══════════════════════════════════════════════════════════

    @Inject(method = "getDigSpeed", at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$applyMiningAttrs(BlockState state, net.minecraft.core.BlockPos pos,
                                            CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;
        float speed = cir.getReturnValue();

        // ── 免疫挖掘惩罚：逆转空中/水中/挖掘疲劳的全部负面效果 ──
        if (attr(player, YizAttributes.MINING_PENALTY_IMMUNITY) >= 1) {
            // 逆转空中减速（原版 /= 5.0F）
            if (!player.onGround()) speed *= 5.0F;
            // 逆转水中减速
            if (player.isEyeInFluid(FluidTags.WATER)) {
                var sub = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
                if (sub != null && sub.getValue() != 1.0) {
                    speed /= (float) sub.getValue();
                }
            }
            // 逆转挖掘疲劳
            if (player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
                int amp = player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier();
                float fatigueMult = switch (amp) {
                    case 0 -> 0.3F;
                    case 1 -> 0.09F;
                    case 2 -> 0.0027F;
                    default -> 8.1E-4F;
                };
                speed /= fatigueMult;
            }
        }

        // ── 挖掘类型覆盖：手持错误工具时给合理基础速度 ──
        float itemBase = player.getInventory().getDestroySpeed(state);
        if (itemBase <= 1.0f && typeCovers(player, state)) {
            int level = (int) attr(player, YizAttributes.MINING_LEVEL);
            float base = 2.0f + level * 2.0f; // lv0=2, lv1=4, lv2=6, lv3=8, lv4=10
            if (speed < base) speed = base;
        }

        // ── 挖掘效率固定加成 ──
        double eff = attr(player, YizAttributes.MINING_EFFICIENCY);
        if (eff > 0) speed *= (float) (1.0 + eff / 100.0);

        cir.setReturnValue(speed);
    }

    // ═══════════════════════════════════════════════════════════
    //  hasCorrectToolForDrops RETURN：挖掘等级覆盖掉落判定
    // ═══════════════════════════════════════════════════════════

    @Inject(method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("RETURN"), cancellable = true)
    private void yizmodqzk$applyMiningLevel(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        Player player = (Player) (Object) this;
        int level = (int) attr(player, YizAttributes.MINING_LEVEL);
        if (level <= 0) return;
        if (level >= getRequiredHarvestTier(state)) {
            cir.setReturnValue(true);
        }
    }

    /** 返回挖掘该方块所需最低 Tier：0=木, 1=石, 2=铁, 3=钻石, 4=下界合金, 5=不可挖掘。 */
    private static int getRequiredHarvestTier(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return 0;
        if (state.is(BlockTags.INCORRECT_FOR_NETHERITE_TOOL)) return 5;
        if (state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) return 4;
        if (state.is(BlockTags.INCORRECT_FOR_IRON_TOOL)) return 3;
        if (state.is(BlockTags.INCORRECT_FOR_STONE_TOOL)) return 2;
        if (state.is(BlockTags.INCORRECT_FOR_WOODEN_TOOL)) return 1;
        return 0;
    }
}
