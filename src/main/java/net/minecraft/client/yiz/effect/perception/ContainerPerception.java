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
    private final Class<?> targetContainerClass;

    public ContainerPerception(ContainerType containerType) {
        this(containerType, null);
    }

    /**
     * @param containerType        容器类型
     * @param targetContainerClass 目标容器类（仅 SPECIFIC_CONTAINER 使用，null 表示任意容器）
     */
    public ContainerPerception(ContainerType containerType, Class<?> targetContainerClass) {
        this.containerType = containerType;
        this.targetContainerClass = targetContainerClass;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public Class<?> getTargetContainerClass() {
        return targetContainerClass;
    }

    @Override
    public boolean check(LivingEntity entity, EffectContext context) {
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            boolean hasContainerOpen = player.containerMenu != player.inventoryMenu;
            if (!hasContainerOpen) return false;

            return switch (containerType) {
                case PERSONAL_CONTAINER -> true;
                case SPECIFIC_CONTAINER -> {
                    if (targetContainerClass == null) yield true;
                    yield targetContainerClass.isInstance(player.containerMenu);
                }
            };
        }
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
