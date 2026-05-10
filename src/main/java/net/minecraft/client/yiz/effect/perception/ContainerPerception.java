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
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return player.containerMenu != player.inventoryMenu;
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
