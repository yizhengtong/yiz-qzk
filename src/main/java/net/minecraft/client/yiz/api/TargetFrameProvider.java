package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * 锁定框供应者接口。
 * 由效果或模组实现，向渲染器提供锁定目标 + 充能/就绪状态。
 * priority 越高越优先渲染。
 */
public interface TargetFrameProvider {
    Entity getTarget(Player player);
    float getCharge();   // 0~1
    boolean isReady();   // 充能满→红色框
    int getPriority();
}
