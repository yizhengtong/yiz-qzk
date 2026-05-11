package net.minecraft.client.yiz.effect;

import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.PerceptionMode;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.tool.health.HealthModificationManager;
import net.minecraft.client.yiz.tool.health.HealthModificationResult;
import net.minecraft.client.yiz.tool.health.HealthModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
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

    // ==================== 健康值修改快捷方法 ====================

    /**
     * 【可选工具】执行单次健康值修改。
     * 使用 ADDITIVE 模式，直接增加/减少实体健康值。
     *
     * @param context 效果上下文
     * @param amount  修改量（正数=治疗，负数=伤害）
     * @return 修改结果
     */
    protected HealthModificationResult executeHealthModification(
        EffectContext context, double amount
    ) {
        return HealthModificationManager.executeModification(
            context.entity(), context
        );
    }

    /**
     * 【可选工具】使用自定义修正器执行健康值修改。
     *
     * @param context  效果上下文
     * @param modifier 健康值修正器
     * @return 修改结果
     */
    protected HealthModificationResult executeHealthModificationWithModifier(
        EffectContext context, HealthModifier modifier
    ) {
        // 通过 EventBus 发布事件，让订阅者可以添加额外修正器
        return HealthModificationManager.executeModification(
            context.entity(), context
        );
    }

    /**
     * 【可选工具】执行 Delta 模式健康值修改。
     * 修改的是健康值上限偏移量，而非直接血量。
     * 对应 ASM 层的 FE_GET_HEALTH_DATA delta 机制。
     *
     * @param context     效果上下文
     * @param deltaAmount delta 偏移量（负数=降低血量上限，正数=恢复）
     * @return 修改结果
     */
    protected HealthModificationResult executeDeltaModification(
        EffectContext context, float deltaAmount
    ) {
        net.minecraft.client.yiz.tool.health.EntityASMUtil.addDelta(
            context.entity(), deltaAmount
        );
        return HealthModificationResult.successWithDelta(
            deltaAmount, context.entity().getHealth(),
            List.of(), deltaAmount
        );
    }

    /**
     * 【可选工具】执行周期性健康值修改。
     * 由子类的 execute() 中定期调用。
     * 搭配 BuiltInTriggers.IntervalTrigger 使用效果最佳。
     *
     * @param context  效果上下文
     * @param modifier 每次触发时使用的修正器
     * @return 修改结果
     */
    protected HealthModificationResult executePeriodicHealthMod(
        EffectContext context, HealthModifier modifier
    ) {
        return HealthModificationManager.executeModification(
            context.entity(), context
        );
    }

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
