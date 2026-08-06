package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.function.Predicate;

/**
 * 内置触发器实现
 * 提供常用的触发条件。
 */
public final class BuiltInTriggers {

    private BuiltInTriggers() {}

    /**
     * 定时触发器（每隔 N 刻触发一次）。
     *
     * 注意：使用内部计数器，不依赖实体 tick 循环。
     * 如需与实体 tick 同步，使用 {@link TickDrivenTrigger}。
     */
    public static class IntervalTrigger implements HealthModificationTrigger {
        private final int intervalTicks;
        private int tickCounter = 0;

        public IntervalTrigger(int intervalTicks) {
            this.intervalTicks = intervalTicks;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity) {
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
     * Tick 驱动触发器（与实体的 tick 循环同步）。
     *
     * 与 IntervalTrigger 不同，此触发器由外部提供 tick 计数，
     * 而非内部维护计数器。使用方式：
     * <pre>{@code
     * if (trigger.shouldTrigger(entity, context, entity.tickCount)) {
     *     // 执行修改
     * }
     * }</pre>
     */
    public static class TickDrivenTrigger implements HealthModificationTrigger {
        private final int intervalTicks;
        private int lastTriggerTick = 0;

        public TickDrivenTrigger(int intervalTicks) {
            this.intervalTicks = intervalTicks;
        }

        /**
         * 检查是否应在当前 tick 触发。
         *
         * @param entity   目标实体
         * @param tickCount 当前 tick 计数（通常来自 entity.tickCount）
         * @return true = 触发
         */
        public boolean shouldTrigger(LivingEntity entity, int tickCount) {
            if (tickCount - lastTriggerTick >= intervalTicks) {
                lastTriggerTick = tickCount;
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity) {
            return shouldTrigger(entity, entity.tickCount);
        }

        @Override
        public String getName() {
            return "TickDrivenTrigger(" + intervalTicks + " ticks)";
        }

        public void syncWithTick(int currentTick) {
            this.lastTriggerTick = currentTick;
        }
    }

    /**
     * 条件触发器（满足条件时触发）。
     */
    public static class ConditionTrigger implements HealthModificationTrigger {
        private final Predicate<LivingEntity> condition;

        public ConditionTrigger(Predicate<LivingEntity> condition) {
            this.condition = condition;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity) {
            return condition.test(entity);
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
        private final Map<String, Object> metadata;

        public EventTrigger(String triggerEvent, Map<String, Object> metadata) {
            this.triggerEvent = triggerEvent;
            this.metadata = metadata;
        }

        @Override
        public boolean shouldTrigger(LivingEntity entity) {
            return metadata != null && metadata.containsKey(triggerEvent);
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
        public boolean shouldTrigger(LivingEntity entity) {
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
        public boolean shouldTrigger(LivingEntity entity) {
            return entity.getHealth() / entity.getMaxHealth() < healthPercentage;
        }

        @Override
        public String getName() {
            return "HealthBelowTrigger(" + (healthPercentage * 100) + "%)";
        }
    }
}
