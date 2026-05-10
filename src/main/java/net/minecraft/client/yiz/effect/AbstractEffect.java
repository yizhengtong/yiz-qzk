package net.minecraft.client.yiz.effect;

import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.PerceptionMode;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * 效果基类
 * 统一所有特殊效果的六大维度，是词缀/天赋/随影的共同父类。
 *
 * 六大维度：
 * - id: 唯一标识
 * - parentType: 所属父类（残响/铭刻/显化/本形/升灵）
 * - level: 等级
 * - perceptionModes: 被感知方式集合（OR 逻辑）
 * - activationCondition: 生效条件
 * - rarity: 稀有度
 */
public abstract class AbstractEffect {

    protected final ResourceLocation id;
    protected final String translationKey;
    protected final String displayName;
    protected final ParentType parentType;
    protected final int level;
    protected final Set<PerceptionMode> perceptionModes;
    protected final ActivationCondition activationCondition;
    protected final Rarity rarity;

    /**
     * 完整构造函数。
     * 自动注册到全局注册表。
     */
    protected AbstractEffect(
        ResourceLocation id,
        String translationKey,
        String displayName,
        ParentType parentType,
        int level,
        Set<PerceptionMode> perceptionModes,
        ActivationCondition activationCondition,
        Rarity rarity
    ) {
        this.id = id;
        this.translationKey = translationKey;
        this.displayName = displayName;
        this.parentType = parentType;
        this.level = level;
        this.perceptionModes = perceptionModes;
        this.activationCondition = activationCondition;
        this.rarity = rarity;

        // 自动注册到全局注册表
        ModRegistries.registerEffect(this);
    }

    // ==================== 效果生命周期 ====================

    /**
     * 效果初始化时调用。
     */
    public void onInitialize() {
        // 默认空实现
    }

    /**
     * 效果激活时调用。
     */
    public void onActivate(LivingEntity entity) {
        // 默认空实现
    }

    /**
     * 效果失效时调用。
     */
    public void onDeactivate(LivingEntity entity) {
        // 默认空实现
    }

    // ==================== 感知系统 ====================

    /**
     * 检查当前是否满足感知条件（OR 逻辑）。
     * 任一感知方式满足即可。
     */
    public boolean checkPerception(LivingEntity entity, EffectContext context) {
        if (perceptionModes.isEmpty()) {
            return false;
        }
        EffectContext ctx = context != null ? context : EffectContext.create(entity, null).withEffect(this);
        for (PerceptionMode mode : perceptionModes) {
            if (mode.check(entity, ctx)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取感知类型名称（词缀/天赋/随影/自定义）。
     */
    public String getPerceptionTypeName() {
        if (perceptionModes.isEmpty()) {
            return "未知";
        }
        // 返回第一个感知方式的类型名称
        return perceptionModes.iterator().next().getTypeName();
    }

    // ==================== 解锁系统 ====================

    /**
     * 检查实体是否已解锁此效果。
     * ItemPerception（词缀）和 ContainerPerception（随影）不需要解锁，
     * 只有 EntityPerception（天赋）需要解锁。
     */
    public boolean isUnlocked(LivingEntity entity) {
        boolean needsUnlock = perceptionModes.stream()
            .anyMatch(m -> m.getPerceptionType() == PerceptionMode.PerceptionType.ENTITY);
        if (!needsUnlock) return true;
        return UnlockManager.isUnlocked(entity, id);
    }

    /**
     * 为实体解锁此效果。
     */
    public void unlock(LivingEntity entity) {
        UnlockManager.unlock(entity, id);
    }

    /**
     * 锁定此效果。
     */
    public void lock(LivingEntity entity) {
        UnlockManager.lock(entity, id);
    }

    // ==================== 生效系统 ====================

    /**
     * 判断是否应该生效（满足感知 + 已解锁 + 满足生效条件）。
     */
    public boolean shouldActivate(LivingEntity entity, EffectContext context) {
        if (!checkPerception(entity, context)) {
            return false;
        }
        if (!isUnlocked(entity)) {
            return false;
        }
        return activationCondition.shouldActivate(context);
    }

    /**
     * 执行效果逻辑（必须由子类实现）。
     */
    public abstract void execute(EffectContext context);

    // ==================== Getters ====================

    public ResourceLocation getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ParentType getParentType() {
        return parentType;
    }

    public int getLevel() {
        return level;
    }

    public Set<PerceptionMode> getPerceptionModes() {
        return perceptionModes;
    }

    public ActivationCondition getActivationCondition() {
        return activationCondition;
    }

    public Rarity getRarity() {
        return rarity;
    }

    @Override
    public String toString() {
        return String.format("AbstractEffect{id=%s, level=%d, rarity=%s}",
            id, level, rarity.name());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AbstractEffect other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
