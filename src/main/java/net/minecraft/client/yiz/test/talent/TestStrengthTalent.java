package net.minecraft.client.yiz.test.talent;

import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * 测试天赋：力量祝福
 * 解锁后常驻，给予力量效果。
 * 测试流程：EntityPerception → UnlockManager → PassiveCondition → execute
 */
public class TestStrengthTalent extends AbstractEffect {

    public static final ResourceLocation ID = ResourceLocation.parse("yizmodqzk:test_strength_talent");

    private long lastActivation = 0;

    public TestStrengthTalent() {
        super(
            ID,
            "effect.yizmodqzk.test_strength_talent",
            "§d试炼·力量天赋",
            ParentType.ORIGIN,
            1,
            Set.of(new EntityPerception()),
            (ActivationCondition) context -> true,
            Rarity.MYTHIC
        );
    }

    @Override
    public void execute(EffectContext context) {
        long now = System.currentTimeMillis();
        if (now - lastActivation < 10000) return; // 10秒冷却
        lastActivation = now;

        if (context.entity() instanceof Player player) {
            player.sendSystemMessage(Component.literal("§d✨ [天赋·试炼] 力量天赋已激活！"));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 300, 0));
        }
    }
}
