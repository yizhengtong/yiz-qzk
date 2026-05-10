package net.minecraft.client.yiz.test.effect;

import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.ContainerPerception;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * 测试随影：火焰守护
 * 打开容器时激活，给予火焰抗性。
 * 测试流程：ContainerPerception → PassiveCondition → execute
 */
public class TestFlameShadow extends AbstractEffect {

    public static final ResourceLocation ID = ResourceLocation.parse("yizmodqzk:test_flame_shadow");

    private long lastActivation = 0;

    public TestFlameShadow() {
        super(
            ID,
            "effect.yizmodqzk.test_flame_shadow",
            "§6试炼·火焰随影",
            ParentType.MANIFESTATION,
            1,
            Set.of(new ContainerPerception(ContainerPerception.ContainerType.SPECIFIC_CONTAINER)),
            (ActivationCondition) context -> true,
            Rarity.EPIC
        );
    }

    @Override
    public void execute(EffectContext context) {
        long now = System.currentTimeMillis();
        if (now - lastActivation < 8000) return; // 8秒冷却
        lastActivation = now;

        if (context.entity() instanceof Player player) {
            player.sendSystemMessage(Component.literal("§6🔥 [随影·试炼] 火焰随影已激活！获得火焰抗性"));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        }
    }
}
