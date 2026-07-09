package net.minecraft.client.yiz.tool.damage;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * 伤害计算公式父类
 * 定义数值结构和标准计算流程。
 *
 * 计算公式：
 * finalDamage = (baseDamage + fixedValue)
 *              + maxHealthDamage
 *              × (1 + finalPercentageMultiplier)
 */
public class DamageFormula {

    protected final Set<DamageTag> tags = new HashSet<>();
    protected DamageValueProvider valueProvider;

    /**
     * 计算最终伤害。
     *
     * @param entity     攻击者
     * @param target     目标
     * @param baseDamage 基础伤害值
     * @return 伤害结果
     */
    public DamageResult calculate(Entity entity, Entity target, double baseDamage) {
        if (valueProvider == null) {
            throw new IllegalStateException("DamageValueProvider has not been set. Call withValueProvider() first.");
        }

        // 1. 获取固定数值（接口2）
        double fixedValue = valueProvider.getFixedValue(entity, target);

        // 2. 基础伤害 + 固定修正
        double damageAfterFixed = baseDamage + fixedValue;

        // 3. 获取目标最大生命值百分比（接口1）
        if (target instanceof LivingEntity livingTarget) {
            double maxHealthPercentage = valueProvider.getTargetMaxHealthPercentage(livingTarget);
            if (maxHealthPercentage > 0) {
                double percentageDamage = livingTarget.getMaxHealth() * maxHealthPercentage;
                damageAfterFixed += percentageDamage;
            }
        }

        // 4. 获取最终百分比提升（接口3）
        double finalMultiplier = valueProvider.getFinalPercentageMultiplier(entity, target);
        double finalDamage = damageAfterFixed * (1.0 + finalMultiplier);

        // 5. 确保伤害不为负
        finalDamage = Math.max(0, finalDamage);

        // 6. 构建结果
        ResourceLocation source = ResourceLocation.parse("yizmodqzk:generic_damage");

        return new DamageResult(finalDamage, baseDamage, source)
            .withTags(tags);
    }

    /**
     * 添加伤害标签。
     */
    public DamageFormula withTag(DamageTag tag) {
        this.tags.add(tag);
        return this;
    }

    /**
     * 添加多个伤害标签。
     */
    public DamageFormula withTags(DamageTag... tags) {
        for (DamageTag tag : tags) {
            this.tags.add(tag);
        }
        return this;
    }

    /**
     * 检查是否包含特定标签。
     */
    public boolean hasTag(DamageTag tag) {
        return tags.contains(tag);
    }

    /**
     * 获取所有标签。
     */
    public Set<DamageTag> getTags() {
        return Set.copyOf(tags);
    }

    /**
     * 设置数值提供者。
     */
    public DamageFormula withValueProvider(DamageValueProvider provider) {
        this.valueProvider = provider;
        return this;
    }

    /**
     * 获取数值提供者。
     */
    public DamageValueProvider getValueProvider() {
        return valueProvider;
    }
}
