package net.minecraft.client.yiz.api;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.client.yiz.tool.health.EntityASMUtil;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
// 大白话: 总入口方法
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

    // ==================== 真实伤害 / 破甲 / 破无敌帧（方法④） ====================

    /**
     * 真实伤害：直接扣除目标生命值，无视护甲、无敌帧、伤害减免。
     * <p>
     * 伤害值发布 {@link DamageEvent}，可被监听和取消。
     * 应用方式：{@code target.setHealth(target.getHealth() - amount)}。
     * </p>
     *
     * @param target 伤害承受方
     * @param amount 伤害值（正数）
     * @param source 伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult trueDamage(LivingEntity target, float amount, Entity source) {
        if (target == null || amount <= 0) {
            return DamageResult.canceled("invalid parameters");
        }
        // 自我伤害免疫
        if (target == source) {
            return DamageResult.canceled("self-damage immunity");
        }

        DamageEvent event = new DamageEvent(target, amount, DamageType.TRUE, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return DamageResult.canceled(event.getCancelReason());

        float finalAmount = event.getAmount();
        float newHealth = Math.max(0, target.getHealth() - finalAmount);
        target.setHealth(newHealth);
        target.hurtMarked = true;
        if (target.level() != null) {
            target.level().broadcastEntityEvent(target, (byte) 2);
        }
        if (newHealth <= 0 && source != null) {
            target.die(source.damageSources().generic());
        }
        return DamageResult.success(finalAmount, 0);
    }

    /**
     * 破甲伤害：跳过护甲减伤，仍受无敌帧限制。
     * <p>
     * 使用 {@link DamageSource#magic()} 作为伤害源以跳过护甲减免。
     * </p>
     *
     * @param target 伤害承受方
     * @param amount 伤害值（正数）
     * @param source 伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult armorPiercingDamage(LivingEntity target, float amount, Entity source) {
        if (target == null || amount <= 0) {
            return DamageResult.canceled("invalid parameters");
        }
        // 自我伤害免疫
        if (target == source) {
            return DamageResult.canceled("self-damage immunity");
        }

        DamageEvent event = new DamageEvent(target, amount, DamageType.ARMOR_PIERCING, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return DamageResult.canceled(event.getCancelReason());

        float finalAmount = event.getAmount();
        DamageSource dmgSource = source != null
            ? target.damageSources().source(net.minecraft.world.damagesource.DamageTypes.MAGIC, source)
            : target.damageSources().generic();
        target.hurt(dmgSource, finalAmount);
        return DamageResult.success(finalAmount, 0);
    }

    /**
     * 破无敌帧伤害：无视目标无敌帧，但仍经过护甲减伤。
     * <p>
     * 临时清除目标 {@code invulnerableTime} 后通过原版 {@code hurt()} 应用伤害，
     * 完成后恢复。
     * </p>
     *
     * @param target 伤害承受方
     * @param amount 伤害值（正数）
     * @param source 伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult pierceInvulnerabilityDamage(LivingEntity target, float amount, Entity source) {
        if (target == null || amount <= 0) {
            return DamageResult.canceled("invalid parameters");
        }
        // 自我伤害免疫
        if (target == source) {
            return DamageResult.canceled("self-damage immunity");
        }

        DamageEvent event = new DamageEvent(target, amount, DamageType.PIERCE_INVULNERABILITY, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return DamageResult.canceled(event.getCancelReason());

        float finalAmount = event.getAmount();
        int saved = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            DamageSource ds = source != null
                ? target.damageSources().source(net.minecraft.world.damagesource.DamageTypes.GENERIC, source)
                : target.damageSources().generic();
            target.hurt(ds, finalAmount);
        } finally {
            target.invulnerableTime = saved;
        }
        return DamageResult.success(finalAmount, 0);
    }

    /**
     * 破甲 + 破无敌帧：跳过护甲且无视无敌帧。
     *
     * @param target 伤害承受方
     * @param amount 伤害值（正数）
     * @param source 伤害来源（攻击者），可为 null
     * @return 伤害应用结果
     */
    public static DamageResult armorPiercingAndPierceInvulnerabilityDamage(LivingEntity target, float amount, Entity source) {
        if (target == null || amount <= 0) {
            return DamageResult.canceled("invalid parameters");
        }
        // 自我伤害免疫：玩家使用技能时伤害来源为自己，防止自伤
        if (target == source) {
            return DamageResult.canceled("self-damage immunity");
        }

        DamageEvent event = new DamageEvent(target, amount, DamageType.ARMOR_PIERCING, source);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return DamageResult.canceled(event.getCancelReason());

        float finalAmount = event.getAmount();
        int saved = target.invulnerableTime;
        target.invulnerableTime = 0;
        try {
            DamageSource dmgSource = source != null
                ? target.damageSources().source(net.minecraft.world.damagesource.DamageTypes.MAGIC, source)
                : target.damageSources().generic();
            target.hurt(dmgSource, finalAmount);
        } finally {
            target.invulnerableTime = saved;
        }
        return DamageResult.success(finalAmount, 0);
    }

    // ==================== 特殊伤害属性绑定（方法④-子） ====================

    /**
     * 注册一个属性为真实伤害属性。
     * <p>
     * 攻击者拥有该属性时，每次近战攻击额外附加等量真实伤害。
     * 真实伤害直接 {@code setHealth(health - damage)}。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的伤害值）
     */
    public static void registerTrueDamageAttribute(Holder<Attribute> holder, float scale) {
        SpecialDamageAttributeRegistry.registerTrueDamage(holder, scale);
    }

    /**
     * 注册一个属性为破甲伤害属性。
     * <p>
     * 攻击者拥有该属性时，每次近战攻击附加等量破甲伤害。
     * 破甲伤害跳过护甲减伤。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（每点属性的伤害值）
     */
    public static void registerArmorPiercingAttribute(Holder<Attribute> holder, float scale) {
        SpecialDamageAttributeRegistry.registerArmorPiercing(holder, scale);
    }

    /**
     * 注册一个属性为破无敌帧属性。
     * <p>
     * 攻击者拥有该属性时（总值 > 0），破甲伤害同时无视目标无敌帧。
     * </p>
     *
     * @param holder 属性
     * @param scale  缩放系数（大于 0 即可激活）
     */
    public static void registerPierceInvulnerabilityAttribute(Holder<Attribute> holder, float scale) {
        SpecialDamageAttributeRegistry.registerPierceInvulnerability(holder, scale);
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

    /**
     * Delta 通道持续回血——走 {@link #modifyHealth} 绕过原版 {@code heal()}。
     * <p>
     * 跟 {@link #modifyHealth} 一样走三层 Delta 通道（delta 偏移 →
     * Float 通道扫描 → 反射保底），不触发 {@code LivingHealEvent}，
     * 不受禁疗系统拦截。专门给境界回血、持续性治疗效果用。
     * </p>
     *
     * @param target 目标实体
     * @param amount 每 tick 回复量（正数，单位：血量点）
     */
    public static void healthRegen(LivingEntity target, float amount) {
        if (target == null || amount <= 0) return;
        if (target.getHealth() >= target.getMaxHealth()) return;
        EntityASMUtil.modifyHealth(target, amount);
    }

    /**
     * 饱食增幅回血——满血不拦截，走 Delta 通道填到上限。
     * <p>
     * 跟 {@link #healthRegen} 的区别：不检查当前血量是否已满，
     * 留给 {@code modifyHealth} 自己处理上限。专门给饱食增幅用——
     * 增幅后的数值可能让血量超出上限的那部分由底层截断。
     * </p>
     */
    public static void healWithFoodBonus(LivingEntity target, float amount) {
        if (target == null || amount <= 0) return;
        EntityASMUtil.modifyHealth(target, amount);
    }

    // ==================== 伤害效果开关 ====================

    /**
     * 设置 Delta 改血系统是否触发受伤动画和音效。
     * <p>
     * 开启后，通过 {@link #damage} / {@link #modifyHealth} / {@link #setHealth}
     * 等途径造成的 Delta 伤害都会触发目标实体的受伤闪烁红心和 {@code GENERIC_HURT} 音效。
     * </p>
     *
     * @param enabled {@code true} 开启效果，{@code false} 关闭（默认）
     */
    public static void setDamageEffectsEnabled(boolean enabled) {
        EntityASMUtil.setDamageEffectsEnabled(enabled);
    }

    /**
     * 查询 Delta 伤害效果开关状态。
     */
    public static boolean isDamageEffectsEnabled() {
        return EntityASMUtil.isDamageEffectsEnabled();
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

    // ==================== 物品属性修改 ====================

    // -- 攻击力 --

    public static double getAttackDamage(ItemStack stack) {
        return net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getAttackDamage(stack);
    }

    public static void setAttackDamage(ItemStack stack, double value) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setAttackDamage(stack, value);
    }

    public static void addAttackDamage(ItemStack stack, double delta) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.addAttackDamage(stack, delta);
    }

    // -- 攻击速度 --

    public static double getAttackSpeed(ItemStack stack) {
        return net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getAttackSpeed(stack);
    }

    public static void setAttackSpeed(ItemStack stack, double value) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setAttackSpeed(stack, value);
    }

    public static void addAttackSpeed(ItemStack stack, double delta) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.addAttackSpeed(stack, delta);
    }

    // -- 交互距离 --

    public static double getInteractionRange(ItemStack stack) {
        return net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getInteractionRange(stack);
    }

    public static void setInteractionRange(ItemStack stack, double value) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setInteractionRange(stack, value);
    }

    public static void addInteractionRange(ItemStack stack, double delta) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.addInteractionRange(stack, delta);
    }

    // -- 横扫伤害比例 --

    public static double getSweepRatio(ItemStack stack) {
        return net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getSweepRatio(stack);
    }

    public static void setSweepRatio(ItemStack stack, double value) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setSweepRatio(stack, value);
    }

    public static void addSweepRatio(ItemStack stack, double delta) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.addSweepRatio(stack, delta);
    }

    // -- 耐久值 --

    public static int getMaxDurability(ItemStack stack) {
        return net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.getMaxDurability(stack);
    }

    public static void setMaxDurability(ItemStack stack, int value) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setMaxDurability(stack, value);
    }

    public static void addMaxDurability(ItemStack stack, int delta) {
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.addMaxDurability(stack, delta);
    }

    // ==================== 简易指令注册 ====================

    /**
     * 注册一个指令（完整 builder）。
     *
     * <pre>{@code
     * YizModQZKAPI.registerCommand(
     *     Commands.literal("mytest")
     *         .executes(ctx -> { ... })
     * );
     * }</pre>
     */
    public static void registerCommand(com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> builder) {
        net.minecraft.client.yiz.tool.SimpleCommandRegistry.register(builder);
    }

    /**
     * 快捷注册：无参数的字面指令。
     *
     * <pre>{@code
     * YizModQZKAPI.registerSimpleCommand("heal", ctx -> {
     *     ctx.getSource().getPlayerOrException().heal(20);
     *     return 1;
     * });
     * }</pre>
     */
    public static void registerSimpleCommand(String name, com.mojang.brigadier.Command<net.minecraft.commands.CommandSourceStack> action) {
        net.minecraft.client.yiz.tool.SimpleCommandRegistry.register(name, action);
    }

    // ==================== 攻击目标锁定 ====================

    /**
     * 获取玩家最近一次攻击的原始目标。
     * <p>由 ASM Agent 在 {@code Player.attack()} 最早时机捕获，
     * 不受其他模组 Mixin 或事件取消/偷换的影响。</p>
     *
     * @return 原始目标实体，无记录时返回 null
     */
    public static net.minecraft.world.entity.Entity getOriginalAttackTarget(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.client.yiz.core.AttackTargetLock.getOriginalTarget(player);
    }

    /**
     * 清理玩家的攻击目标记录。
     */
    public static void cleanupAttackTarget(net.minecraft.world.entity.player.Player player) {
        net.minecraft.client.yiz.core.AttackTargetLock.cleanup(player);
    }

    // ==================== Unsafe 保护态 ====================

    /**
     * 启用玩家保护态（Unsafe class 指针替换）。
     * 保护态下玩家免疫一切伤害，生命值恒 ≥ 0.5。
     */
    public static boolean enableProtection(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.client.yiz.core.PlayerClassSwapper.enableProtection(player);
    }

    /**
     * 关闭玩家保护态，恢复原始类。
     */
    public static boolean disableProtection(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.client.yiz.core.PlayerClassSwapper.disableProtection(player);
    }

    /**
     * 查询玩家是否处于保护态。
     */
    public static boolean isProtected(net.minecraft.world.entity.player.Player player) {
        return net.minecraft.client.yiz.core.PlayerClassSwapper.isProtected(player);
    }

    // ==================== VTable 方法替换 ====================

    /**
     * 允许调用 vtable 方法替换的下游模组白名单。
     * 只有在此白名单中的 modid 才能调用 {@link #replaceKillMethods} 等方法。
     * 使用 {@link #allowVTableModId(String)} 注册受信任的下游模组。
     */
    private static final Set<String> ALLOWED_VTABLE_MODIDS = ConcurrentHashMap.newKeySet();

    static {
        // 已知受信任的下游模组 — 在此添加新的下游模组 modid
        ALLOWED_VTABLE_MODIDS.add("yizxgmod");    // YizXG 模组模板
        ALLOWED_VTABLE_MODIDS.add("yizxianmod");   // 泰拉棱镜 (yizxian)
    }

    /**
     * 将指定 modid 加入 vtable 方法替换白名单。
     * 应在模组构造器中尽早调用（在调用 {@link #replaceKillMethods} 之前）。
     *
     * @param modid 要加入白名单的模组 ID
     */
    public static void allowVTableModId(String modid) {
        ALLOWED_VTABLE_MODIDS.add(modid);
        tizMod.LOGGER.info("[YizModQZKAPI] VTable whitelist updated: added {}", modid);
    }

    /**
     * 通过 StackWalker 检测调用方的 modid。
     * 遍历调用栈，找到第一个标注了 {@code @Mod} 注解的调用者类，
     * 取其注解 value 作为 modid。
     *
     * @return 调用方 modid，无法检测时返回 "unknown"
     */
    private static String detectCallerModId() {
        try {
            Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames
                            .skip(2)  // 跳过 detectCallerModId 和调用它的 API 方法
                            .filter(f -> {
                                Class<?> c = f.getDeclaringClass();
                                // 跳过本库自身的类
                                return !c.getPackageName().startsWith("net.minecraft.client.yiz");
                            })
                            .findFirst()
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .orElse(null));
            if (callerClass == null) return "unknown";

            // 检查 @Mod 注解以确定 modid
            for (Annotation ann : callerClass.getAnnotations()) {
                if (ann.annotationType().getName().equals("net.neoforged.fml.common.Mod")) {
                    try {
                        return (String) ann.annotationType().getMethod("value").invoke(ann);
                    } catch (Exception ignored) {}
                }
            }

            // 回退：使用调用方包名推断
            String pkg = callerClass.getPackageName();
            // 取包名的第一个非公共段作为候选
            // 例如 "net.minecraft.client.yiz.xian" → "yizxianmod" 不太可靠，返回 unknown
            return "unknown";

        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 校验调用方 modid 是否在 vtable 替换白名单中。
     */
    private static boolean isCallerModidAllowed() {
        String modid = detectCallerModId();
        boolean allowed = ALLOWED_VTABLE_MODIDS.contains(modid);
        if (!allowed) {
            tizMod.LOGGER.warn("[YizModQZKAPI] VTable replaceKillMethods blocked: " +
                    "caller modid '{}' is not in the whitelist. " +
                    "Call YizModQZKAPI.allowVTableModId(\"{}\") to register.", modid, modid);
        }
        return allowed;
    }

    /**
     * 查询 vtable 方法替换系统是否可用。
     * 在调用 {@link #replaceMethod} / {@link #replaceKillMethods} 之前应先检查。
     */
    public static boolean isVTableReplaceAvailable() {
        return net.minecraft.client.yiz.core.VTableReplace.isAvailable();
    }

    /**
     * 通过 vtable 替换，将指定类的某个 void 方法替换为空实现。
     * <p>
     * 这是最底层的方法替换——直接覆写 HotSpot 内部方法入口指针，
     * 不经过 ASM、Agent、Mixin、ClassFileTransformer 任一层。
     * 一旦替换，所有通过 vtable 虚方法分派到该方法的调用都会进入空实现。
     * </p>
     * <p>
     * 【注意】此操作不可逆（在当前版本中），影响该类的所有实例。
     * 仅替换 void-returning 方法。
     * </p>
     *
     * @param targetClass 目标类
     * @param methodName  JVM 方法名（如 "setHealth"、"kill"）
     * @param paramTypes  方法参数类型（仅用于构造 JVM 描述符）
     * @return true 表示替换成功
     */
    public static boolean replaceMethod(Class<?> targetClass, String methodName,
                                        Class<?>... paramTypes) {
        // 安全校验：只有白名单中的下游模组才能使用 vtable 方法替换
        if (!isCallerModidAllowed()) {
            return false;
        }
        return net.minecraft.client.yiz.core.VTableReplace.replaceVoidMethod(
                targetClass, methodName, paramTypes);
    }

    /**
     * 替换指定类上所有已知的致死方法为空实现。
     * <p>
     * 覆盖以下路径：{@code setHealth(float)}、{@code kill()}、
     * {@code die(DamageSource)}、{@code remove(RemovalReason)}。
     * 替换后，即使其他模组调用这些方法也无法杀死该类的实例。
     * </p>
     *
     * @param entityClass 目标实体类（通常是某模组自定义的 LivingEntity 子类）
     * @return 成功替换的方法数量
     */
    public static int replaceKillMethods(Class<? extends LivingEntity> entityClass) {
        if (!isVTableReplaceAvailable()) return 0;

        // 安全校验：只有白名单中的下游模组才能使用 vtable 方法替换
        if (!isCallerModidAllowed()) {
            return 0;
        }

        int count = 0;

        // setHealth(float) — (F)V
        if (net.minecraft.client.yiz.core.VTableReplace.replaceVoidMethod(
                entityClass, "setHealth", float.class)) {
            count++;
        }

        // kill() — ()V
        if (net.minecraft.client.yiz.core.VTableReplace.replaceVoidMethod(
                entityClass, "kill")) {
            count++;
        }

        // die(DamageSource) — (Lnet/minecraft/world/damagesource/DamageSource;)V
        if (net.minecraft.client.yiz.core.VTableReplace.replaceMethod(
                entityClass, "die",
                "(Lnet/minecraft/world/damagesource/DamageSource;)V")) {
            count++;
        }

        // remove(RemovalReason) — (Lnet/minecraft/world/entity/Entity$RemovalReason;)V
        if (net.minecraft.client.yiz.core.VTableReplace.replaceMethod(
                entityClass, "remove",
                "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")) {
            count++;
        }

        return count;
    }

    /**
     * 替换 LivingEntity 基类的所有致死方法为空实现。
     * <p>
     * 这会影响所有未 override 这些方法的 LivingEntity 子类实例。
     * 单独 override 了某个方法的子类不受影响（其 vtable 中有自己的 Method*）。
     * </p>
     *
     * @return 成功替换的方法数量
     */
    public static int replaceBaseLivingEntityKillMethods() {
        return replaceKillMethods(LivingEntity.class);
    }

    // ==================== 蓝量系统 ====================

    /** 获取实体当前蓝量。 */
    public static float getMana(net.minecraft.world.entity.LivingEntity entity) {
        return net.minecraft.client.yiz.tool.health.ManaTracker.get(entity);
    }

    /** 设置实体蓝量。 */
    public static void setMana(net.minecraft.world.entity.LivingEntity entity, float value) {
        net.minecraft.client.yiz.tool.health.ManaTracker.set(entity, value);
    }

    /** 增减实体蓝量，返回实际变化量。 */
    public static float addMana(net.minecraft.world.entity.LivingEntity entity, float delta) {
        return net.minecraft.client.yiz.tool.health.ManaTracker.add(entity, delta);
    }

    /** 消耗蓝量，返回是否足够。 */
    public static boolean consumeMana(net.minecraft.world.entity.LivingEntity entity, float amount) {
        return net.minecraft.client.yiz.tool.health.ManaTracker.consume(entity, amount);
    }

    /** 获取实体蓝量上限。 */
    public static float getMaxMana(net.minecraft.world.entity.LivingEntity entity) {
        return net.minecraft.client.yiz.tool.health.ManaTracker.getMax(entity);
    }

    // ==================== 创造标签页 ====================

    /**
     * 获取物品所属的创造标签页类别。
     * <p>
     * 根据物品实现的接口返回对应类别 key：
     * {@code talent} / {@code skill} / {@code item} / {@code weapon}。
     * 未实现任何接口返回 {@code null}。
     * </p>
     *
     * @param item 要查询的物品
     * @return 类别 key，未分类返回 null
     */
    public static String getCreativeTabCategory(Item item) {
        if (item instanceof ISkillItem) return "skill";
        if (item instanceof IGeneralItem) return "item";
        if (item instanceof IWeaponItem) return "weapon";
        return null;
    }

    /**
     * 检查物品是否实现了任意创造标签页接口（归入任一母页类别）。
     *
     * @param item 要检查的物品
     * @return true 如果物品实现了 ITalentItem/ISkillItem/IGeneralItem/IWeaponItem 中的任意一个
     */
    public static boolean isCreativeTabRegistered(Item item) {
        return getCreativeTabCategory(item) != null;
    }
}
