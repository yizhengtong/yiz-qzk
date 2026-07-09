package net.minecraft.client.yiz.tool.damage;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 攻击上下文
 * 收集攻击相关的所有数据，用于标签检测和伤害计算。
 *
 * <p>标签检测已从效果框架迁移至属性系统（Phase C）。
 * 当前 hasEnforcementTag 返回 false，后续由自定义属性驱动。</p>
 */
public class AttackContext {

    public final LivingEntity attacker;
    public final Entity target;
    public final ItemStack mainHandItem;
    public final ItemStack offHandItem;

    private AttackContext(LivingEntity attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
        this.mainHandItem = attacker.getMainHandItem();
        this.offHandItem = attacker.getOffhandItem();
    }

    public static AttackContext create(LivingEntity attacker, Entity target) {
        if (attacker == null) {
            throw new IllegalArgumentException("attacker must not be null");
        }
        return new AttackContext(attacker, target);
    }

    /**
     * 检查是否包含特定强制执行标签。
     * TODO Phase C: 改为从攻击者物品的自定义属性读取标签。
     */
    public boolean hasEnforcementTag(DamageTag tag) {
        // Phase C 将改为属性驱动
        return false;
    }

    /**
     * 使用 DamageFormula 计算伤害。
     */
    public DamageResult calculateDamage(Entity attacker, Entity target,
                                         double baseDamage, DamageValueProvider provider) {
        DamageFormula formula = new DamageFormula() {
            {
                withValueProvider(provider);
            }
        };
        return formula.calculate(attacker, target, baseDamage);
    }

    /**
     * 应用攻击后效果（击退等）。
     */
    public void applyPostAttackEffects() {
        // 默认空实现
    }

    /**
     * 标记效果可以提供标签（保留接口为 Phase C 属性消费点准备）。
     */
    public interface DamageTagProvider {
        List<DamageTag> getAssociatedTags();
    }
}