package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;

/**
 * 健康值修改触发器
 * 定义触发时机和条件。
 */
@FunctionalInterface
public interface HealthModificationTrigger {

    /**
     * 检查是否应该触发健康值修改。
     *
     * @param entity  目标实体
     * @return true = 触发，false = 不触发
     */
    boolean shouldTrigger(LivingEntity entity);

    /**
     * 获取触发器名称。
     */
    default String getName() {
        return "HealthModificationTrigger";
    }
}
