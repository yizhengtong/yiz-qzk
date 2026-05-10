package net.minecraft.client.yiz.effect.perception;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * 容器绑定感知 — 随影 (Shadow)
 * 效果绑定到容器（如末影箱、特定位置的容器），
 * 当容器被访问时感知。
 */
public class ContainerPerception implements PerceptionMode {

    private final ContainerType containerType;

    public ContainerPerception(ContainerType containerType) {
        this.containerType = containerType;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    @Override
    public boolean check(LivingEntity entity, EffectContext context) {
        // 具体容器检查逻辑由开发者实现
        // 前置库只提供接口和类型定义
        return false;
    }

    @Override
    public String getTypeName() {
        return "随影 (Shadow)";
    }

    @Override
    public PerceptionType getPerceptionType() {
        return PerceptionType.CONTAINER;
    }

    public enum ContainerType {
        SPECIFIC_CONTAINER,
        PERSONAL_CONTAINER
    }
}
