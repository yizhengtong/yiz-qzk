package net.minecraft.client.yiz.editor;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 破限附魔（yizmodqzk:poxian）效果处理器。
 * <p>
 * 机制：{@link LivingDamageEvent.Pre} 中其他模组可能对伤害值设上限。
 * 本处理器在 {@code HIGHEST} 优先保存原始伤害，在 {@code LOWEST} 取回，
 * 确保破限武器造成的伤害不低于原始值。
 * </p>
 */
public final class BreakLimitHandler {

    private BreakLimitHandler() {}

    private static final ThreadLocal<Float> ORIGINAL_AMOUNT = new ThreadLocal<>();

    /** HIGHEST — 在其他模组设上限之前保存伤害值 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamagePreSave(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof Player player
            && hasPoxian(player)) {
            ORIGINAL_AMOUNT.set(event.getContainer().getNewDamage());
        }
    }

    /** LOWEST — 其他模组处理完后，确保不低于原始值 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePreRestore(LivingDamageEvent.Pre event) {
        Float original = ORIGINAL_AMOUNT.get();
        ORIGINAL_AMOUNT.remove();
        if (original == null) return;
        if (event.getSource().getEntity() instanceof Player player
            && hasPoxian(player)) {
            float current = event.getContainer().getNewDamage();
            if (current < original) {
                event.getContainer().setNewDamage(original);
            }
        }
    }

    private static boolean hasPoxian(Player player) {
        var enchants = player.getMainHandItem()
            .getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            var key = entry.getKey().getKey();
            if (key != null && key.location().toString().equals("yizmodqzk:poxian")) {
                return true;
            }
        }
        return false;
    }
}
