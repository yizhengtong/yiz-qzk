package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 属性修饰正负相等锁定
 * <p>
 * enableFor(entity) 记录所有属性当前值为底线，
 * 后续任何修改都不会让属性值低于记录值。
 * </p>
 */
public final class AttributeBalanceRegistry {

    private static final Map<AttributeInstance, Double> FLOORS = new IdentityHashMap<>();

    private AttributeBalanceRegistry() {}

    /**
     * 为该实体的所有属性记录当前值作为底线
     */
    @SuppressWarnings("unchecked")
    public static void enableFor(LivingEntity entity) {
        try {
            var field = entity.getAttributes().getClass().getDeclaredField("attributes");
            field.setAccessible(true);
            var map = (java.util.Map<?, AttributeInstance>) field.get(entity.getAttributes());
            for (AttributeInstance inst : map.values()) {
                FLOORS.putIfAbsent(inst, inst.getValue());
            }
        } catch (Exception ignored) {}
    }

    /**
     * 每 tick 强制恢复：若当前值低于底线，直接设回底线
     */
    @SuppressWarnings("unchecked")
    public static void enforceFloors(LivingEntity entity) {
        try {
            var field = entity.getAttributes().getClass().getDeclaredField("attributes");
            field.setAccessible(true);
            var map = (java.util.Map<?, AttributeInstance>) field.get(entity.getAttributes());
            for (AttributeInstance inst : map.values()) {
                Double floor = FLOORS.get(inst);
                if (floor != null && inst.getValue() < floor) {
                    inst.setBaseValue(floor);
                }
            }
        } catch (Exception ignored) {}
    }
}
