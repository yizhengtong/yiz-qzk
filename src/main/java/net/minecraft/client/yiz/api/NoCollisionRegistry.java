package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 碰撞免疫注册表（参考原版旁观者模式 isSpectator → 跳过 push）
 */
// 大白话: 碰撞免疫方法
public final class NoCollisionRegistry {

    private static final List<Condition> CONDITIONS = new CopyOnWriteArrayList<>();

    private NoCollisionRegistry() {}

    @FunctionalInterface
    public interface Condition {
        boolean isImmune(LivingEntity entity);
    }

    public static void register(Condition condition) {
        CONDITIONS.add(condition);
    }

    public static boolean isImmune(LivingEntity entity) {
        for (Condition c : CONDITIONS) if (c.isImmune(entity)) return true;
        return false;
    }
}
