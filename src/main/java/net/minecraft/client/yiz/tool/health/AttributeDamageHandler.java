package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 方法B：属性伤害事件处理器
 * 在受伤事件中获取攻击者与目标实体，
 * 通过 {@link HealthAttributeHandler#applyReimuAttributes}（方法A）
 * 将灵梦属性传递到 ASM Agent 健康值修改流水线。
 *
 * <p>使用 NeoForge 事件总线，保证在各种环境下均可靠触发。</p>
 * <p>与旧版不同：不再直接修改事件伤害值（会经过护甲计算），
 * 而是通过 HealthApplier + ASM Agent 流水线直接修改实体生命值。</p>
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

        // 获取攻击者（任意 LivingEntity，不限于 Player）
        var attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity livingAttacker)) return;

        // 获取目标实体
        var target = event.getEntity();
        if (target == null) return;

        // 委托方法A：将灵梦属性通过 ASM Agent 流水线应用到目标
        HealthAttributeHandler.applyReimuAttributes(livingAttacker, target);
    }
}
