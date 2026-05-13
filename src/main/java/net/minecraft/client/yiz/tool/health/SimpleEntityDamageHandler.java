package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 最简单的 ASM Agent 伤害测试接口。
 * <p>
 * 通过 /yiz e &lt;value&gt; 设置伤害值，
 * 攻击非玩家实体时通过 {@link YizModQZKAPI#damage} 应用 DELTA 伤害。
 * </p>
 */
public final class SimpleEntityDamageHandler {

    private static boolean registered = false;
    private static float damageValue = 0;

    private SimpleEntityDamageHandler() {}

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.register(SimpleEntityDamageHandler.class);
    }

    public static void setDamageValue(float value) {
        damageValue = value;
    }

    public static float getDamageValue() {
        return damageValue;
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (damageValue <= 0) return;

        var source = event.getSource();
        if (source == null) return;

        var attacker = source.getEntity();
        if (!(attacker instanceof Player)) return;

        var target = event.getEntity();
        if (target instanceof Player) return;

        // 通过公开 API 应用伤害
        YizModQZKAPI.damage(target, damageValue, attacker);
    }
}
