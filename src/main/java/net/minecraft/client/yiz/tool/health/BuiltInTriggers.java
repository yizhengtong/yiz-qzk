package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Predicate;

/**
 * 内置触发器实现
 * 提供常用的触发条件。
 */
public final class BuiltInTriggers {

    private BuiltInTriggers() {}

    /**
     * 定时触发器（每隔 N 刻触发一次）。
     */
    public static class IntervalTrigger implements HealthModificationTrigger {
        private final int intervalTicks;
        private int tickCounter = 0;

        public IntervalTrigger(int intervalTicks) {
            this.intervalTicks = intervalTicks;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            tickCounter++;
            if (tickCounter >= intervalTicks) {
                tickCounter = 0;
                return true;
            }
            return false;
        }

        @Override
        public String getName() {
            return "IntervalTrigger(" + intervalTicks + " ticks)";
        }

        public void reset() {
            tickCounter = 0;
        }
    }

    /**
     * 条件触发器（满足条件时触发）。
     */
    public static class ConditionTrigger implements HealthModificationTrigger {
        private final Predicate<EffectContext> condition;

        public ConditionTrigger(Predicate<EffectContext> condition) {
            this.condition = condition;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            return condition.test(context);
        }

        @Override
        public String getName() {
            return "ConditionTrigger";
        }
    }

    /**
     * 事件触发器（特定事件发生时触发）。
     */
    public static class EventTrigger implements HealthModificationTrigger {
        private final String triggerEvent;

        public EventTrigger(String triggerEvent) {
            this.triggerEvent = triggerEvent;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            return context.metadata() != null && context.metadata().containsKey(triggerEvent);
        }

        @Override
        public String getName() {
            return "EventTrigger(" + triggerEvent + ")";
        }
    }

    /**
     * 立即触发器（总是触发）。
     */
    public static class ImmediateTrigger implements HealthModificationTrigger {
        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            return true;
        }

        @Override
        public String getName() {
            return "ImmediateTrigger";
        }
    }

    /**
     * 血量低于阈值时触发。
     */
    public static class HealthBelowTrigger implements HealthModificationTrigger {
        private final double healthPercentage;

        public HealthBelowTrigger(double healthPercentage) {
            this.healthPercentage = healthPercentage;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            return entity.getHealth() / entity.getMaxHealth() < healthPercentage;
        }

        @Override
        public String getName() {
            return "HealthBelowTrigger(" + (healthPercentage * 100) + "%)";
        }
    }
}
