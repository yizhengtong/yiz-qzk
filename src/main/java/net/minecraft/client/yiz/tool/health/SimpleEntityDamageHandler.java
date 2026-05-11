package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 最简单的 ASM Agent 伤害测试接口。
 * <p>
 * 通过 /yiz e &lt;value&gt; 设置伤害值，
 * 攻击非玩家实体时直接通过 {@link EntityASMUtil#addDelta} 应用 DELTA 伤害，
 * 不走事件总线/修正器流水线，纯粹测试 ASM Agent 的 DELTA 系统。
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
        // 不作用于玩家实体
        if (target instanceof Player) return;

        // 通过 ASM Agent DELTA 系统直接应用伤害
        // DELTA 存储在 SynchedEntityData 中，独立于 vanilla 血量，不会被覆盖
        EntityASMUtil.addDelta(target, -damageValue);
    }
}
