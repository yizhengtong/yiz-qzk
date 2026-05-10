package net.minecraft.client.yiz.tool.damage;

import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 攻击上下文
 * 收集攻击相关的所有数据，用于标签检测和伤害计算。
 */
public class AttackContext {

    public final LivingEntity attacker;
    public final Entity target;
    public final ItemStack mainHandItem;
    public final ItemStack offHandItem;
    public final List<AbstractEffect> activeEffects;

    private AttackContext(LivingEntity attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
        this.mainHandItem = attacker.getMainHandItem();
        this.offHandItem = attacker.getOffhandItem();
        this.activeEffects = new ArrayList<>();
    }

    public static AttackContext create(LivingEntity attacker, Entity target) {
        return new AttackContext(attacker, target);
    }

    /**
     * 检查是否包含特定强制执行标签。
     * 逻辑：检查手持物品词缀 + 实体天赋。
     */
    public boolean hasEnforcementTag(DamageTag tag) {
        // 1. 检查主手物品词缀（通过 NBT 效果）
        if (checkItemForTag(mainHandItem, tag)) return true;

        // 2. 检查副手物品词缀
        if (checkItemForTag(offHandItem, tag)) return true;

        // 3. 检查活跃效果
        for (AbstractEffect effect : activeEffects) {
            if (effect instanceof DamageTagProvider provider) {
                if (provider.getAssociatedTags().contains(tag)) return true;
            }
        }

        return false;
    }

    private boolean checkItemForTag(ItemStack stack, DamageTag tag) {
        if (stack.isEmpty()) return false;
        // 检查物品 NBT 效果中是否包含指定标签
        List<AbstractEffect> itemEffects = EffectNBTHandler.getItemEffects(stack);
        for (AbstractEffect effect : itemEffects) {
            if (effect instanceof DamageTagProvider provider) {
                if (provider.getAssociatedTags().contains(tag)) return true;
            }
        }
        return false;
    }

    /**
     * 使用 DamageFormula 计算伤害。
     */
    public DamageResult calculateDamage(EffectContext context, double baseDamage, DamageValueProvider provider) {
        DamageFormula formula = new DamageFormula() {
            {
                withValueProvider(provider);
            }
        };
        return formula.calculate(context, baseDamage);
    }

    /**
     * 应用攻击后效果（击退等）。
     */
    public void applyPostAttackEffects() {
        // 默认空实现
    }

    /**
     * 标记效果可以提供标签。
     */
    public interface DamageTagProvider {
        List<DamageTag> getAssociatedTags();
    }
}
