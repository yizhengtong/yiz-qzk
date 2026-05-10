package net.minecraft.client.yiz.test;

import net.minecraft.client.yiz.core.event.EffectEventBus;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.test.effect.TestDamageAffix;
import net.minecraft.client.yiz.test.effect.TestFlameShadow;
import net.minecraft.client.yiz.test.talent.TestStrengthTalent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 测试设置
 * 注册测试效果并连接事件总线。
 */
public final class TestSetup {

    private static boolean initialized = false;
    private static int tickCounter = 0;

    // 持有引用防止 GC
    @SuppressWarnings("unused")
    private static TestDamageAffix damageAffix;
    @SuppressWarnings("unused")
    private static TestFlameShadow flameShadow;
    @SuppressWarnings("unused")
    private static TestStrengthTalent strengthTalent;

    private TestSetup() {}

    /**
     * 初始化所有测试内容。
     * 在客户端设置阶段调用。
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // 1. 创建测试效果（自动注册到 ModRegistries）
        damageAffix = new TestDamageAffix();
        flameShadow = new TestFlameShadow();
        strengthTalent = new TestStrengthTalent();

        // 2. 注册事件处理器
        NeoForge.EVENT_BUS.addListener(TestSetup::onPlayerAttack);
        NeoForge.EVENT_BUS.addListener(TestSetup::onPlayerTick);

        net.minecraft.client.yiz.tizMod.LOGGER.info("[YizTest] 已注册 3 个测试效果");
    }

    /**
     * 玩家攻击事件 → 分发攻击类效果（词缀）。
     * 当玩家攻击任意实体时触发。
     */
    private static void onPlayerAttack(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;

        Entity target = event.getEntity();
        EffectContext context = EffectContext.createAttackContext(attacker, target);
        EffectEventBus.dispatchContext(context);
    }

    /**
     * 玩家 Tick 事件 → 分发被动/常驻类效果（随影、天赋）。
     * 每 2 秒检查一次，避免过于频繁。
     */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter % 40 != 0) return; // 每 40 tick ≈ 2 秒

        Player player = event.getEntity();
        EffectContext context = EffectContext.create(player, null);
        EffectEventBus.dispatchContext(context);
    }
}
