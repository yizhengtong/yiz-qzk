package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * 健康值修改管理器
 * 负责收集、排序、汇总所有健康值修改并应用到实体。
 *
 * 核心流程：
 * 1. 创建事件 → 2. 发布事件 → 3. 收集修正器 → 4. 按优先级排序
 * → 5. 汇总计算 → 6. 应用到实体
 */
public final class HealthModificationManager {

    private HealthModificationManager() {}

    /**
     * 执行健康值修改。
     *
     * @param entity  目标实体
     * @param context 效果上下文
     * @return 修改结果
     */
    public static HealthModificationResult executeModification(LivingEntity entity, EffectContext context) {
        // 1. 创建事件
        HealthModificationEvent event = new HealthModificationEvent(entity, context);

        // 2. 发布事件（通过 NeoForge EventBus）
        NeoForge.EVENT_BUS.post(event);

        // 3. 检查事件是否被取消
        if (event.isCanceled()) {
            return HealthModificationResult.canceled(event.getCancelReason());
        }

        // 4. 获取所有修正器
        List<HealthModifier> modifiers = event.getModifiers();

        // 5. 按优先级排序（高优先级先计算）
        modifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        // 6. 汇总计算
        double totalModification = 0;
        List<HealthModificationResult.ModificationDetail> details = new ArrayList<>();

        for (HealthModifier modifier : modifiers) {
            double amount = modifier.getModificationAmount(entity, context);
            details.add(new HealthModificationResult.ModificationDetail(
                modifier.getId(),
                modifier.getType(),
                amount
            ));
            totalModification += amount;
        }

        // 7. 应用到实体
        applyToEntity(entity, totalModification);

        // 8. 返回结果
        return HealthModificationResult.success(totalModification, entity.getHealth(), details);
    }

    /**
     * 应用修改到实体。
     */
    private static void applyToEntity(LivingEntity entity, double modification) {
        if (modification > 0) {
            // 治疗
            float newHealth = Math.min(entity.getMaxHealth(), entity.getHealth() + (float) modification);
            entity.setHealth(newHealth);
        } else if (modification < 0) {
            // 伤害
            float newHealth = Math.max(0, entity.getHealth() + (float) modification);
            entity.setHealth(newHealth);

            // 检查死亡
            if (newHealth <= 0) {
                entity.die(entity.damageSources().generic());
            }
        }
    }

    /**
     * 快捷方法：触发生命值修改。
     */
    public static HealthModificationResult triggerModification(LivingEntity entity, EffectContext context) {
        return executeModification(entity, context);
    }
}
