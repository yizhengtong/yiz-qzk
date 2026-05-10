package net.minecraft.client.yiz.test.effect;

import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.ItemPerception;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.tool.damage.DamageResult;
import net.minecraft.client.yiz.tool.damage.DamageTag;
import net.minecraft.client.yiz.tool.damage.DirectAttackExecutor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/**
 * 测试词缀：伤害增幅
 * 手持物品攻击时触发，额外造成伤害。
 * 测试流程：ItemPerception → 条件检查 → execute
 */
public class TestDamageAffix extends AbstractEffect {

    public static final ResourceLocation ID = ResourceLocation.parse("yizmodqzk:test_damage_affix");

    public TestDamageAffix() {
        super(
            ID,
            "effect.yizmodqzk.test_damage_affix",
            "§c试炼·伤害增幅",
            ParentType.ECHO,
            1,
            Set.of(new ItemPerception(ItemPerception.ItemSlot.MAIN_HAND)),
            (ActivationCondition) context -> context.target() instanceof LivingEntity,
            Rarity.LEGENDARY
        );
    }

    @Override
    public void execute(EffectContext context) {
        if (!(context.entity() instanceof Player player)) return;
        if (!(context.target() instanceof LivingEntity target)) return;
        if (target instanceof Player) return;

        player.sendSystemMessage(Component.literal("§a⚡ [词缀·试炼] 伤害增幅触发！额外造成 5.0 点真实伤害"));

        // 使用直接伤害执行器，避免递归触发 LivingDamageEvent
        DamageResult damage = new DamageResult(5.0, 5.0,
                player.damageSources().playerAttack(player).typeHolder()
                        .unwrapKey().map(ResourceKey::location).orElse(null))
                .withTag(DamageTag.TRUE_DAMAGE);
        DirectAttackExecutor.executeForcedAttackFromContext(context, damage);
    }
}
