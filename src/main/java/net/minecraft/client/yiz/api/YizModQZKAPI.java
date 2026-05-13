package net.minecraft.client.yiz.api;

import net.minecraft.client.yiz.core.event.EffectEventBus;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * YizMod QZK 公开 API
 * <p>
 * 第三方模组通过此接口与前置库交互。
 * 伤害相关方法会触发 {@link DamageEvent} 通知，可被监听和取消。
 * </p>
 *
 * <h3>伤害通知入口（两个接口）</h3>
 * <ul>
 *   <li>{@link #damage(LivingEntity, float, Entity)} — 固定数值伤害</li>
 *   <li>{@link #percentDamage(LivingEntity, float, Entity)} — 百分比伤害</li>
 * </ul>
 */
public final class YizModQZKAPI {

    private YizModQZKAPI() {}

    // ==================== 伤害通知入口 ====================

    /**
     * 通知入口①：对目标造成固定数值伤害。
     * <p>
     * 通过 ASM Agent DELTA 系统应用伤害，独立于 vanilla 血量，
     * 不会因后续 vanilla 伤害计算而被覆盖。
     * 伤害应用前发布 {@link DamageEvent}，其他模组可监听修改或取消。
     * </p>
     *
     * @param target 伤害承受方
     * @param amount 伤害值（正数）
     * @param source 伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult damage(LivingEntity target, float amount, Entity source) {
        if (target == null || amount <= 0) {
            return DamageResult.canceled("invalid parameters");
        }

        // 1. 发布通知事件（其他模组可监听修改或取消）
        DamageEvent event = new DamageEvent(target, amount, DamageType.FLAT, source);
        NeoForge.EVENT_BUS.post(event);

        // 2. 检查取消
        if (event.isCanceled()) {
            return DamageResult.canceled(event.getCancelReason());
        }

        // 3. 通过 ASM Agent DELTA 系统应用
        float finalAmount = event.getAmount();
        EntityASMUtil.addDelta(target, -finalAmount);

        // 4. 返回结果
        float remaining = EntityASMUtil.getHealthDelta(target);
        return DamageResult.success(finalAmount, remaining);
    }

    /**
     * 通知入口②：对目标造成最大生命值百分比伤害。
     * <p>
     * 计算方式：amount = target.getMaxHealth() * percent / 100
     * 与 {@link #damage} 一样使用 ASM Agent DELTA 系统并发布 {@link DamageEvent}。
     * </p>
     *
     * @param target  伤害承受方
     * @param percent 百分比（如 10 表示 10%）
     * @param source  伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult percentDamage(LivingEntity target, float percent, Entity source) {
        if (target == null || percent <= 0) {
            return DamageResult.canceled("invalid parameters");
        }

        float amount = target.getMaxHealth() * percent / 100.0f;

        // 1. 发布通知事件
        DamageEvent event = new DamageEvent(target, amount, DamageType.PERCENT, source);
        NeoForge.EVENT_BUS.post(event);

        // 2. 检查取消
        if (event.isCanceled()) {
            return DamageResult.canceled(event.getCancelReason());
        }

        // 3. 通过 ASM Agent DELTA 系统应用
        float finalAmount = event.getAmount();
        EntityASMUtil.addDelta(target, -finalAmount);

        // 4. 返回结果
        float remaining = EntityASMUtil.getHealthDelta(target);
        return DamageResult.success(finalAmount, remaining);
    }

    // ==================== 属性绑定伤害（方法①-子） ====================

    /**
     * 注册一个属性为伤害属性。
     * <p>
     * 攻击者拥有该属性时，每次近战攻击额外附加等量伤害。
     * 伤害经由三層系统（Delta → ChannelScanner → DirectHealthFallback）施加，
     * 绕过目标实体的自定义 {@code hurt()}。
     * </p>
     *
     * @param holder 要注册为伤害源的属性
     */
    public static void registerDamageAttribute(Holder<Attribute> holder) {
        DamageAttributeRegistry.register(holder);
    }

    /**
     * 获取攻击者身上所有已注册伤害属性的总值。
     *
     * @param attacker 攻击者实体
     * @return 所有已注册伤害属性的总和
     */
    public static float getDamageAttributeValue(LivingEntity attacker) {
        return DamageAttributeRegistry.getTotalValue(attacker);
    }

    // ==================== 直接健康值修改（方法②） ====================

    /**
     * 直接增减实体健康值（治疗或伤害）。
     * <p>
     * 正数 = 治疗，负数 = 伤害。<br>
     * 经过三層系统修改所有 Float 数据通道，绕过目标实体的自定义 {@code hurt()}。
     * 可供外部模组技能系统直接调用，不受伤害类型黑名单/阈值/上限影响。
     * </p>
     *
     * @param target 目标实体
     * @param delta  变化值（正数治疗，负数伤害）
     */
    public static void modifyHealth(LivingEntity target, float delta) {
        if (target == null) return;
        EntityASMUtil.modifyHealth(target, delta);
    }

    /**
     * 直接设置实体健康值（绝对值）。
     * <p>
     * 与 {@link #modifyHealth} 一样绕过 {@code hurt()}。
     * 自动计算与当前生命值的差值后委派给 {@code modifyHealth}。
     * </p>
     *
     * @param target 目标实体
     * @param health 目标生命值
     */
    public static void setHealth(LivingEntity target, float health) {
        if (target == null) return;
        float delta = health - target.getHealth();
        if (delta != 0) {
            EntityASMUtil.modifyHealth(target, delta);
        }
    }

    // ==================== 健康值根本禁疗（方法③） ====================

    /**
     * 从健康值根本禁止治疗（百分比 + 固定值）。
     * <p>
     * 在 {@code modifyHealth} 和 {@code HealthApplier} 层面拦截治疗，
     * 对任何途径的治疗生效。
     * </p>
     *
     * @param entity      目标实体
     * @param percent     百分比禁疗（0~100），如 50 = 削减一半治疗
     * @param fixedAmount 固定值禁疗（≥0），如 10 = 每次治疗减 10 点
     */
    public static void setHealBan(LivingEntity entity, float percent, float fixedAmount) {
        if (entity == null) return;
        net.minecraft.client.yiz.tool.health.HealBanConfig.set(entity, percent, fixedAmount);
    }

    /**
     * 百分比禁疗（子方法①）。
     * <p>
     * 按百分比削减所有治疗量。
     * 例：percent=50 → 所有治疗仅生效一半。
     * </p>
     *
     * @param entity  目标实体
     * @param percent 禁疗百分比（0~100）
     */
    public static void setHealBanPercent(LivingEntity entity, float percent) {
        if (entity == null) return;
        var existing = net.minecraft.client.yiz.tool.health.HealBanConfig.get(entity);
        float fixed = existing != null ? existing.fixedAmount() : 0;
        net.minecraft.client.yiz.tool.health.HealBanConfig.set(entity, percent, fixed);
    }

    /**
     * 固定值禁疗（子方法②）。
     * <p>
     * 每次治疗减掉固定数值。治疗量不足时完全取消。
     * 例：fixedAmount=10 → 10点以下的治疗全取消，20点的治疗只生效 10 点。
     * </p>
     *
     * @param entity      目标实体
     * @param fixedAmount 固定禁疗值（≥0）
     */
    public static void setHealBanFixed(LivingEntity entity, float fixedAmount) {
        if (entity == null) return;
        var existing = net.minecraft.client.yiz.tool.health.HealBanConfig.get(entity);
        float percent = existing != null ? existing.percent() : 0;
        net.minecraft.client.yiz.tool.health.HealBanConfig.set(entity, percent, fixedAmount);
    }

    // ==================== 禁疗属性绑定（方法③-子） ====================

    /**
     * 注册一个属性为百分比禁疗属性。
     * <p>
     * 攻击者拥有该属性时，每次攻击为目标施加百分比禁疗。
     * 例：registerHealBanPercentAttribute(holder, 10)，攻击者有 3 点 → 目标 30% 禁疗。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的禁疗百分比）
     */
    public static void registerHealBanPercentAttribute(Holder<Attribute> holder, float scale) {
        HealBanAttributeRegistry.registerPercent(holder, scale);
    }

    /**
     * 注册一个属性为固定值禁疗属性。
     * <p>
     * 攻击者拥有该属性时，每次攻击为目标施加固定值禁疗。
     * 例：registerHealBanFixedAttribute(holder, 5)，攻击者有 3 点 → 目标每次治疗减 15 点。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的禁疗值）
     */
    public static void registerHealBanFixedAttribute(Holder<Attribute> holder, float scale) {
        HealBanAttributeRegistry.registerFixed(holder, scale);
    }

    // ==================== 效果注册 ====================

    /**
     * 注册效果。
     */
    public static void registerEffect(AbstractEffect effect) {
        ModRegistries.registerEffect(effect);
    }

    /**
     * 根据 ID 获取效果。
     */
    public static Optional<AbstractEffect> getEffect(ResourceLocation id) {
        return ModRegistries.getEffect(id);
    }

    // ==================== 解锁管理 ====================

    /**
     * 为实体解锁效果。
     */
    public static void unlockEffect(LivingEntity entity, ResourceLocation effectId) {
        UnlockManager.unlock(entity, effectId);
    }

    /**
     * 检查实体是否已解锁效果。
     */
    public static boolean isEffectUnlocked(LivingEntity entity, ResourceLocation effectId) {
        return UnlockManager.isUnlocked(entity, effectId);
    }

    // ==================== 效果查询 ====================

    /**
     * 分发效果上下文（触发效果系统）。
     */
    public static void dispatchContext(EffectContext context) {
        EffectEventBus.dispatchContext(context);
    }

    // ==================== 注册表查询 ====================

    /**
     * 获取所有注册的效果。
     */
    public static List<AbstractEffect> getAllEffects() {
        return List.copyOf(ModRegistries.getAllEffects());
    }

    // ==================== 快捷方法 ====================

    /**
     * 直接为物品附加效果（写入 NBT）。
     */
    public static void attachEffectToItem(ItemStack stack, AbstractEffect effect) {
        net.minecraft.client.yiz.core.data.EffectNBTHandler.addEffectToItem(stack, effect);
    }

    /**
     * 获取物品上的所有效果。
     */
    public static List<AbstractEffect> getItemEffects(ItemStack stack) {
        return net.minecraft.client.yiz.core.data.EffectNBTHandler.getItemEffects(stack);
    }
}
