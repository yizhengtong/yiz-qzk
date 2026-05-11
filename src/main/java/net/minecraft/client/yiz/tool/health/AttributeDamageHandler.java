package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 属性伤害事件处理器
 * 在受伤事件中读取攻击者的自定义属性（灵梦固定伤害/百分比伤害），
 * 将其叠加到本次伤害中。
 *
 * <p>使用 NeoForge 事件总线而非 Mixin，保证在各种环境下均可靠触发。</p>
 */
public final class AttributeDamageHandler {

    private static boolean registered = false;

    private AttributeDamageHandler() {}

    /**
     * 注册到 NeoForge 事件总线。
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.register(AttributeDamageHandler.class);
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        var source = event.getSource();
        if (source == null) return;

        // 只处理玩家作为攻击者的情况
        var attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return;

        double flatDmg = player.getAttributeValue(ModAttributes.REIMU_FLAT_DAMAGE);
        double pctDmg = player.getAttributeValue(ModAttributes.REIMU_PERCENT_DAMAGE);

        if (flatDmg <= 0 && pctDmg <= 0) return;

        // 额外伤害 = 固定伤害 + 目标最大生命值 × 百分比 / 100
        float extra = (float) flatDmg + event.getEntity().getMaxHealth() * (float) pctDmg / 100.0f;
        if (extra <= 0) return;

        event.setAmount(event.getAmount() + extra);
    }
}
