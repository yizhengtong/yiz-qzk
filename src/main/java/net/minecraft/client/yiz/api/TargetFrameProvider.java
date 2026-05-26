package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 锁定框供应者接口。
 * 由效果或模组实现，向渲染器提供锁定目标 + 充能/就绪状态。
 * priority 越高越优先渲染。
 */
// 大白话: 锁定框接口
public interface TargetFrameProvider {
    Entity getTarget(Player player);
    float getCharge();
    boolean isReady();
    int getPriority();
    default ResourceLocation[] getCornerTextures() { return null; } // null = use default
}
