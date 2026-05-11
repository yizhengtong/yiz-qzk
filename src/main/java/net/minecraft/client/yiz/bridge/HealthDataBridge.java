package net.minecraft.client.yiz.bridge;

/**
 * 访问 LivingEntity 的健康增量数据。
 * 由 LivingEntityMixin 在 LivingEntity 上实现。
 */
public interface HealthDataBridge {

    /** 获取健康增量值。有效血量上限 = getMaxHealth() + delta */
    float yizmodqzk$getHealthDelta();

    /** 设置健康增量值。 */
    void yizmodqzk$setHealthDelta(float delta);
}
