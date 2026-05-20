package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 飞行惯性优化注册表
 * 下游注册条件，前置在 aiStep 中自动消除飞行惯性。
 */
public final class FlightOptimizationRegistry {

    private static final List<Condition> CONDITIONS = new CopyOnWriteArrayList<>();

    private FlightOptimizationRegistry() {}

    @FunctionalInterface
    public interface Condition {
        boolean shouldOptimize(LivingEntity entity);
    }

    public static void register(Condition condition) {
        CONDITIONS.add(condition);
    }

    public static boolean shouldOptimize(LivingEntity entity) {
        for (Condition c : CONDITIONS) if (c.shouldOptimize(entity)) return true;
        return false;
    }
}
