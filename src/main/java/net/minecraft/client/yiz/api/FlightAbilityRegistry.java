package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 飞行权限注册表 — 强制 mayFly() 返回 true
 */
public final class FlightAbilityRegistry {

    private static final List<Condition> CONDITIONS = new CopyOnWriteArrayList<>();

    private FlightAbilityRegistry() {}

    @FunctionalInterface
    public interface Condition {
        boolean shouldHaveFlight(LivingEntity entity);
    }

    public static void register(Condition condition) {
        CONDITIONS.add(condition);
    }

    public static boolean shouldHaveFlight(LivingEntity entity) {
        for (Condition c : CONDITIONS) if (c.shouldHaveFlight(entity)) return true;
        return false;
    }
}
