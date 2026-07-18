package net.minecraft.client.yiz.handler;

import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 开关形技能状态追踪（库级别，供 HUD 读取）。 */
public final class ToggleSkillState {

    private static final ConcurrentHashMap<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();

    private ToggleSkillState() {}

    public static void setActive(Player player, boolean active) {
        if (active) ACTIVE.put(player.getUUID(), true);
        else ACTIVE.remove(player.getUUID());
    }

    public static boolean isActive(Player player) {
        return ACTIVE.getOrDefault(player.getUUID(), false);
    }
}
