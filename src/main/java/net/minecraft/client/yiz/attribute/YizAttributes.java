package net.minecraft.client.yiz.attribute;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 前置库自定义属性注册中心。
 *
 * <p>使用 NeoForge {@link DeferredRegister} 注册为原版 Attribute 系统的一等公民，
 * 支持 {@code DataComponents.ATTRIBUTE_MODIFIERS} 物品修饰符、
 * {@code /attribute} 指令查询和修改。</p>
 *
 * <h3>装备栏自动过滤</h3>
 * <p>物品通过 {@code EquipmentSlotGroup} 挂载的 AttributeModifier 只在对应槽位生效。
 * 例如主手武器上的暴击率只在手持时计入 {@code getAttributeValue()}，背包中不计入。</p>
 */
public final class YizAttributes {

    private YizAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES =
        DeferredRegister.create(Registries.ATTRIBUTE, "yizmodqzk");

    /**
     * 暴击率 — 任何攻击触发暴击的概率。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> CRIT_RATE =
        ATTRIBUTES.register("crit_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.crit_rate", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 暴伤 — 暴击时的额外伤害倍率（增量百分比，非相乘）。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> CRIT_DAMAGE =
        ATTRIBUTES.register("crit_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.crit_damage", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 精准 — 持有该属性时，所有以玩家为来源的伤害均可暴击。
     * <p>值 > 0 即生效（二元开关），具体暴击率/暴伤走 CRIT_RATE / CRIT_DAMAGE。</p>
     */
    public static final Holder<Attribute> PRECISION =
        ATTRIBUTES.register("precision",
            () -> new RangedAttribute("attribute.yizmodqzk.precision", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 吸血 — 造成伤害时回复自身生命的比例。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> LIFE_STEAL =
        ATTRIBUTES.register("life_steal",
            () -> new RangedAttribute("attribute.yizmodqzk.life_steal", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  伤害范围（溅射/AoE）
    // ═══════════════════════════════════════════════════════════

    /**
     * 伤害范围半径 — 攻击命中后对目标周围实体的溅射半径。
     * <p>值域 ≥0，无上限（格）。</p>
     */
    public static final Holder<Attribute> SPLASH_RADIUS =
        ATTRIBUTES.register("splash_radius",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_radius", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 伤害范围百分比 — 溅射伤害占原伤害的比例。
     * <p>值域 ≥0，无上限。</p>
     */
    public static final Holder<Attribute> SPLASH_DAMAGE =
        ATTRIBUTES.register("splash_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_damage", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 伤害范围衰减 — 溅射伤害从中心到边缘的衰减强度。
     * <p>值域 ≥0，无上限。中间使用平滑二次曲线插值。</p>
     */
    public static final Holder<Attribute> SPLASH_FALLOFF =
        ATTRIBUTES.register("splash_falloff",
            () -> new RangedAttribute("attribute.yizmodqzk.splash_falloff", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  锁定系统（会心/渴攻）
    // ═══════════════════════════════════════════════════════════

    /**
     * 会心 — 视觉锁定范围（格）。
     * <p>值域 ≥0，无上限。1 = 1 格锁定距离。</p>
     */
    public static final Holder<Attribute> HUIXIN =
        ATTRIBUTES.register("huixin",
            () -> new RangedAttribute("attribute.yizmodqzk.huixin", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 攻击速度 — 百分比增加攻击频率（0=无效果, 100=攻速翻倍）。
     * <p>值域 ≥0，无上限。计算公式：实际冷却 = 原冷却 / (1 + 值/100)。</p>
     */
    public static final Holder<Attribute> COOLDOWN_REDUCTION =
        ATTRIBUTES.register("cooldown_reduction",
            () -> new RangedAttribute("attribute.yizmodqzk.cooldown_reduction", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 渴攻 — 锁定充能时间（tick）。
     * <p>值域 ≥0，无上限。1 = 1 tick（0.05 秒）。</p>
     */
    public static final Holder<Attribute> KEGONG =
        ATTRIBUTES.register("kegong",
            () -> new RangedAttribute("attribute.yizmodqzk.kegong", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 护盾值 — 当前护盾容量上限（初始值，由物品/技能叠加）。
     * <p>实际护盾消耗由 {@code ShieldTracker} 追踪，受击时在格挡之后吸收伤害。</p>
     */
    public static final Holder<Attribute> SHIELD_VALUE =
        ATTRIBUTES.register("shield_value",
            () -> new RangedAttribute("attribute.yizmodqzk.shield_value", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  蓝条系统
    // ═══════════════════════════════════════════════════════════

    /** 蓝量上限 — 玩家默认 200。 */
    public static final Holder<Attribute> MAX_MANA =
        ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute.yizmodqzk.max_mana", 200.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 固定回蓝 — 每 tick 回 值×0.05 蓝。玩家默认 1。 */
    public static final Holder<Attribute> MANA_REGEN =
        ATTRIBUTES.register("mana_regen",
            () -> new RangedAttribute("attribute.yizmodqzk.mana_regen", 1.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 百分比回蓝 — 每 tick 回 值/100/20 × 蓝上限。 */
    public static final Holder<Attribute> MANA_REGEN_PCT =
        ATTRIBUTES.register("mana_regen_pct",
            () -> new RangedAttribute("attribute.yizmodqzk.mana_regen_pct", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /** 永恒储蓝 — 直接减少技能耗蓝。 */
    public static final Holder<Attribute> MANA_COST_REDUCTION =
        ATTRIBUTES.register("mana_cost_reduction",
            () -> new RangedAttribute("attribute.yizmodqzk.mana_cost_reduction", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 单次耗蓝 — 技能物品的属性，使用一次消耗的蓝量。 */
    public static final Holder<Attribute> MANA_COST =
        ATTRIBUTES.register("mana_cost",
            () -> new RangedAttribute("attribute.yizmodqzk.mana_cost", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 每秒耗蓝 — 持续性技能每秒耗蓝，实际按 tick 扣 值/20。 */
    public static final Holder<Attribute> MANA_COST_PER_SEC =
        ATTRIBUTES.register("mana_cost_per_sec",
            () -> new RangedAttribute("attribute.yizmodqzk.mana_cost_per_sec", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 格挡 — 固定值减免伤害（在百分比减免之后、护盾之前扣除）。
     * <p>值域 ≥0，无上限。1 = 1 点伤害减免。</p>
     */
    public static final Holder<Attribute> DAMAGE_BLOCK =
        ATTRIBUTES.register("damage_block",
            () -> new RangedAttribute("attribute.yizmodqzk.damage_block", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 全伤害 — 对所有伤害来源的百分比加成。
     * <p>值域 ≥0，无上限。1.0 = +100%。与 test-plan #15 generic_damage 对齐。</p>
     */
    public static final Holder<Attribute> GENERIC_DAMAGE =
        ATTRIBUTES.register("generic_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.generic_damage", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 减伤率 — 百分比最终减伤。
     * <p>值域 0~100。在 {@code LivingEntityMixin.setHealth} 写入前按 {@code 伤害 × (1 - 值/100)} 减免，
     * 位于 DamageReductionRegistry 百分比减免之后、DAMAGE_BLOCK 固定减免之前。
     * 下游泰拉饰品（蠕虫围巾等）的减伤数据迁移至此原生属性，主手/副手/盔甲/饰品槽全部生效。</p>
     */
    public static final Holder<Attribute> DAMAGE_REDUCTION =
        ATTRIBUTES.register("damage_reduction",
            () -> new RangedAttribute("attribute.yizmodqzk.damage_reduction", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 攻击强度防御 — 1:1 镜像到原版护甲+韧性，同时提供指数公式通用伤害减免。
     * <p>减免公式：1 - (1 + 值/40)^(-ln2/ln1.5)。20点=50%，50点=75%。</p>
     * <p>仅减免通用型伤害（物理类），不减免火焰/冰冻/闪电/魔法伤害。</p>
     */
    public static final Holder<Attribute> ARMOR =
        ATTRIBUTES.register("armor",
            () -> new RangedAttribute("attribute.yizmodqzk.armor", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 攻击强度 — 按百分比增幅最终伤害（值 = 百分比，30 表示 +30%）。
     * <p>不影响原版攻击力基础值，仅在伤害结算时 {@code amount *= (1 + 值/100)}。默认 0。</p>
     */
    public static final Holder<Attribute> ATTACK_STRENGTH =
        ATTRIBUTES.register("attack_strength",
            () -> new RangedAttribute("attribute.yizmodqzk.attack_strength", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 法术防御 — 1:1 镜像到原版击退韧性，同时提供指数公式非通用伤害减免。
     * <p>减免公式：1 - (1 + 值/40)^(-ln2/ln1.5)。减免火焰/冰冻/闪电/魔法等非物理伤害。</p>
     */
    public static final Holder<Attribute> SPELL_DEFENSE =
        ATTRIBUTES.register("spell_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.spell_defense", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 法术强度 — 百分比属性（值 = 百分比，100 表示 100%）。
     * <p>作为所有法术伤害/护盾的统一加成系数：技能效果 = 基础值 × 法强/100。
     * 不再直接附加到普攻伤害。默认 100（=100%），可由 {@link #getEffectiveSpellPower}
     * 经 MAGIC_DAMAGE 进一步放大。</p>
     */
    public static final Holder<Attribute> SPELL_POWER =
        ATTRIBUTES.register("spell_power",
            () -> new RangedAttribute("attribute.yizmodqzk.spell_power", 100.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 冷却值 — 决定物品功能冷却间隔（tick）。≤0=无冷却。
     */
    public static final Holder<Attribute> COOLDOWN_VALUE =
        ATTRIBUTES.register("cooldown_value",
            () -> new RangedAttribute("attribute.yizmodqzk.cooldown_value", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 技能范围倍率 — 百分比放大技能所有用途的效果范围（AoE/链锁/光环半径）。
     * <p>值域 ≥0，1 = +1%。例：20 = 所有范围 ×1.2。配合 {@code SkillRanges.get} 使用，
     * 倍率作用于 (基础值 + 定向偏移) 之上。强化槽加 modifier 即可全局扩大技能范围。</p>
     */
    public static final Holder<Attribute> SKILL_RANGE =
        ATTRIBUTES.register("skill_range",
            () -> new RangedAttribute("attribute.yizmodqzk.skill_range", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 技能间隔加速率 — 百分比缩短技能所有用途的周期间隔（伤害/护盾/击退等周期）。
     * <p>值域 0~100，1 = 周期 ×0.99。例：20 = 所有间隔 ×0.8（触发更频繁）。配合 {@code SkillIntervals.get} 使用。
     * 强化槽加 modifier 即可全局加速技能周期。</p>
     */
    public static final Holder<Attribute> SKILL_INTERVAL =
        ATTRIBUTES.register("skill_interval",
            () -> new RangedAttribute("attribute.yizmodqzk.skill_interval", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 最大充能数 — 技能可攒的可用次数上限。默认 1。大装载槽位 ×2。
     * <p>放一次技能消耗 1 充能；充能未满时每 cooldown_value 回复 1。</p>
     */
    public static final Holder<Attribute> MAX_CHARGES =
        ATTRIBUTES.register("max_charges",
            () -> new RangedAttribute("attribute.yizmodqzk.max_charges", 1.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 伤害类型 — 技能造成的伤害类型编码。
     * 0=物理, 1=火焰, 2=冰冻, 3=闪电, 4=感电, 5=魔法。
     */
    public static final Holder<Attribute> DAMAGE_TYPE =
        ATTRIBUTES.register("damage_type",
            () -> new RangedAttribute("attribute.yizmodqzk.damage_type", 0.0, 0.0, 5.0)
                .setSyncable(true));

    /**
     * 回血系数(攻) — 攻击强度×系数% 转化为回复量。值域 0~100。
     */
    public static final Holder<Attribute> HEAL_ATK_COEFF =
        ATTRIBUTES.register("heal_atk_coeff",
            () -> new RangedAttribute("attribute.yizmodqzk.heal_atk_coeff", 0.0, 0.0, 100.0)
                .setSyncable(true));
    /**
     * 回血系数(法) — 法术强度×系数% 转化为回复量。值域 0~100。
     */
    public static final Holder<Attribute> HEAL_SPELL_COEFF =
        ATTRIBUTES.register("heal_spell_coeff",
            () -> new RangedAttribute("attribute.yizmodqzk.heal_spell_coeff", 0.0, 0.0, 100.0)
                .setSyncable(true));
    /**
     * 回血系数(命) — 最大生命值×系数% 转化为回复量。值域 0~100。
     */
    public static final Holder<Attribute> HEAL_HP_COEFF =
        ATTRIBUTES.register("heal_hp_coeff",
            () -> new RangedAttribute("attribute.yizmodqzk.heal_hp_coeff", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 基础伤害 — 技能固定伤害值（公式：base + spell_power×spell_coeff/100）。值域 ≥0。
     */
    public static final Holder<Attribute> DAMAGE_BASE =
        ATTRIBUTES.register("damage_base",
            () -> new RangedAttribute("attribute.yizmodqzk.damage_base", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));
    /**
     * 法术伤害系数 — 法术强度转化为伤害的比例（%）。值域 0~100。
     */
    public static final Holder<Attribute> DAMAGE_SPELL_COEFF =
        ATTRIBUTES.register("damage_spell_coeff",
            () -> new RangedAttribute("attribute.yizmodqzk.damage_spell_coeff", 0.0, 0.0, 100.0)
                .setSyncable(true));
    /**
     * 基础回血 — 技能固定回血量。值域 ≥0。
     */
    public static final Holder<Attribute> HEAL_BASE =
        ATTRIBUTES.register("heal_base",
            () -> new RangedAttribute("attribute.yizmodqzk.heal_base", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  移动属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 移动速度 — 值域 ≥0。 */
    public static final Holder<Attribute> MOVE_SPEED =
        ATTRIBUTES.register("move_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.move_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大奔跑速度 — 值域 ≥0。 */
    public static final Holder<Attribute> MAX_RUN_SPEED =
        ATTRIBUTES.register("max_run_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.max_run_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 跳跃力度 — 值域 ≥0。 */
    public static final Holder<Attribute> JUMP_STRENGTH =
        ATTRIBUTES.register("jump_strength",
            () -> new RangedAttribute("attribute.yizmodqzk.jump_strength", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 空中移速 — 值域 ≥0。 */
    public static final Holder<Attribute> AIR_SPEED =
        ATTRIBUTES.register("air_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.air_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  跳跃属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 跳跃次数 — 值域 ≥0，1 = 多 1 次跳跃。 */
    public static final Holder<Attribute> JUMP_COUNT =
        ATTRIBUTES.register("jump_count",
            () -> new RangedAttribute("attribute.yizmodqzk.jump_count", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 跳跃高度 — 值域 ≥0。 */
    public static final Holder<Attribute> JUMP_HEIGHT =
        ATTRIBUTES.register("jump_height",
            () -> new RangedAttribute("attribute.yizmodqzk.jump_height", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 跌落保护（安全距离）— 值域 ≥0，格。 */
    public static final Holder<Attribute> FALL_SAFE =
        ATTRIBUTES.register("fall_safe",
            () -> new RangedAttribute("attribute.yizmodqzk.fall_safe", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 跌落减免 — 值域 ≥0。 */
    public static final Holder<Attribute> FALL_REDUCE =
        ATTRIBUTES.register("fall_reduce",
            () -> new RangedAttribute("attribute.yizmodqzk.fall_reduce", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  生存属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 闪避几率 — 值域 ≥0，1 = 1%。 */
    public static final Holder<Attribute> DODGE_CHANCE =
        ATTRIBUTES.register("dodge_chance",
            () -> new RangedAttribute("attribute.yizmodqzk.dodge_chance", 0.0, 0.0, 100.0).setSyncable(true));
    /** 无敌帧倍率 — 值域 ≥0，tick。 */
    public static final Holder<Attribute> INVINCIBILITY_MULT =
        ATTRIBUTES.register("invincibility_mult",
            () -> new RangedAttribute("attribute.yizmodqzk.invincibility_mult", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 熔岩免疫时间(百分比) — 值域 0~100，延长免疫时间的百分比。 */
    public static final Holder<Attribute> LAVA_IMMUNE_TIME =
        ATTRIBUTES.register("lava_immune_time",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_immune_time", 0.0, 0.0, 100.0).setSyncable(true));
    /** 熔岩免疫时间(固定) — 值域 ≥0，直接增加的免疫 tick 数。 */
    public static final Holder<Attribute> LAVA_IMMUNE_TIME_FLAT =
        ATTRIBUTES.register("lava_immune_time_flat",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_immune_time_flat", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 熔岩减伤(百分比) — 值域 0~100，先算百分比。 */
    public static final Holder<Attribute> LAVA_DAMAGE_REDUCTION =
        ATTRIBUTES.register("lava_damage_reduction",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_damage_reduction", 0.0, 0.0, 100.0).setSyncable(true));
    /** 熔岩减伤(固定) — 值域 ≥0，百分比后再减固定值。 */
    public static final Holder<Attribute> LAVA_DAMAGE_REDUCTION_FLAT =
        ATTRIBUTES.register("lava_damage_reduction_flat",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_damage_reduction_flat", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 生命恢复(定点) — 值域 ≥0。 */
    public static final Holder<Attribute> LIFE_REGEN_RATE =
        ATTRIBUTES.register("life_regen_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.life_regen_rate", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 生命恢复(百分比) — 值域 ≥0。 */
    public static final Holder<Attribute> LIFE_REGEN_PCT =
        ATTRIBUTES.register("life_regen_pct",
            () -> new RangedAttribute("attribute.yizmodqzk.life_regen_pct", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  伤害属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 近战伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> MELEE_DAMAGE =
        ATTRIBUTES.register("melee_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.melee_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 远程伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> RANGED_DAMAGE =
        ATTRIBUTES.register("ranged_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.ranged_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 法术伤害增幅 — 值域 ≥0。 */
    public static final Holder<Attribute> MAGIC_DAMAGE =
        ATTRIBUTES.register("magic_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.magic_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 召唤伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> SUMMON_DAMAGE =
        ATTRIBUTES.register("summon_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.summon_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 护甲穿透 — 值域 ≥0。 */
    /** 护甲穿透百分比 — 值域 0~100，穿透目标护甲的百分比。 */
    public static final Holder<Attribute> ARMOR_PENETRATION =
        ATTRIBUTES.register("armor_penetration",
            () -> new RangedAttribute("attribute.yizmodqzk.armor_penetration", 0.0, 0.0, 100.0).setSyncable(true));
    /** 护甲穿透固定值 — 值域 ≥0，百分比穿透后再扣固定值。 */
    public static final Holder<Attribute> ARMOR_PENETRATION_FLAT =
        ATTRIBUTES.register("armor_penetration_flat",
            () -> new RangedAttribute("attribute.yizmodqzk.armor_penetration_flat", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 交互距离 — 值域 ≥0，格。 */
    public static final Holder<Attribute> ATTACK_RANGE =
        ATTRIBUTES.register("attack_range",
            () -> new RangedAttribute("attribute.yizmodqzk.attack_range", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    /**
     * 自动攻击 — 值 > 0 时，按住攻击键 + 冷却满即自动攻击，无需 {@code yizmodqzk:auto_attack} 附魔。
     * <p>由装备（如疾射火炮）授予。值域 0~1（二元开关，>0 即生效）。</p>
     */
    public static final Holder<Attribute> AUTO_ATTACK =
        ATTRIBUTES.register("auto_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.auto_attack", 0.0, 0.0, 1.0).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  特殊属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 飞行时间 — 值域 ≥0，tick。 */
    public static final Holder<Attribute> FLIGHT_TIME =
        ATTRIBUTES.register("flight_time",
            () -> new RangedAttribute("attribute.yizmodqzk.flight_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 步高 — 值域 ≥0，玩家可直接走上不高于此值的方块。 */
    public static final Holder<Attribute> JUMP_SPEED =
        ATTRIBUTES.register("jump_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.jump_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大仆从数 — 值域 ≥0。 */
    public static final Holder<Attribute> MAX_MINIONS =
        ATTRIBUTES.register("max_minions",
            () -> new RangedAttribute("attribute.yizmodqzk.max_minions", 1.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大哨兵数 — 值域 ≥0。 */
    public static final Holder<Attribute> MAX_SENTRIES =
        ATTRIBUTES.register("max_sentries",
            () -> new RangedAttribute("attribute.yizmodqzk.max_sentries", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 水下呼吸时间(百分比) — 值域 0~100，延长憋气时间的百分比。 */
    public static final Holder<Attribute> WATER_BREATH_TIME =
        ATTRIBUTES.register("water_breath_time",
            () -> new RangedAttribute("attribute.yizmodqzk.water_breath_time", 0.0, 0.0, 100.0).setSyncable(true));
    /** 水下呼吸时间(固定) — 值域 ≥0，直接增加的憋气 tick 数。 */
    public static final Holder<Attribute> WATER_BREATH_TIME_FLAT =
        ATTRIBUTES.register("water_breath_time_flat",
            () -> new RangedAttribute("attribute.yizmodqzk.water_breath_time_flat", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  状态效果属性 — 攻方（攻击时对目标施加效果）
    // ═══════════════════════════════════════════════════════════

    /** 眩晕(攻) — 触发概率%。值域 [0, 100]，1=1%。 */
    public static final Holder<Attribute> STUN_ATTACK =
        ATTRIBUTES.register("stun_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.stun_attack", 0.0, 0.0, 100.0).setSyncable(true));
    /** 减速(攻) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> SLOW_ATTACK =
        ATTRIBUTES.register("slow_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.slow_attack", 0.0, 0.0, 100.0).setSyncable(true));
    /** 冰冻(攻) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> FREEZE_ATTACK =
        ATTRIBUTES.register("freeze_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.freeze_attack", 0.0, 0.0, 100.0).setSyncable(true));
    /** 感电(攻) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> SHOCK_ATTACK =
        ATTRIBUTES.register("shock_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_attack", 0.0, 0.0, 100.0).setSyncable(true));
    /** 击飞(攻) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> KNOCKBACK_ATTACK =
        ATTRIBUTES.register("knockback_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.knockback_attack", 0.0, 0.0, 100.0).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  状态效果属性 — 防方（受击时对攻击者施加效果）
    // ═══════════════════════════════════════════════════════════

    /** 眩晕(防) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> STUN_DEFENSE =
        ATTRIBUTES.register("stun_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.stun_defense", 0.0, 0.0, 100.0).setSyncable(true));
    /** 减速(防) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> SLOW_DEFENSE =
        ATTRIBUTES.register("slow_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.slow_defense", 0.0, 0.0, 100.0).setSyncable(true));
    /** 冰冻(防) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> FREEZE_DEFENSE =
        ATTRIBUTES.register("freeze_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.freeze_defense", 0.0, 0.0, 100.0).setSyncable(true));
    /** 感电(防) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> SHOCK_DEFENSE =
        ATTRIBUTES.register("shock_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_defense", 0.0, 0.0, 100.0).setSyncable(true));
    /** 击飞(防) — 触发概率%。值域 [0, 100]。 */
    public static final Holder<Attribute> KNOCKBACK_DEFENSE =
        ATTRIBUTES.register("knockback_defense",
            () -> new RangedAttribute("attribute.yizmodqzk.knockback_defense", 0.0, 0.0, 100.0).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  状态效果属性 — 共享（攻击/防御 共用时间与伤害）
    // ═══════════════════════════════════════════════════════════

    /** 眩晕时间 — 共享，单位 tick。默认 20。 */
    public static final Holder<Attribute> STUN_TIME =
        ATTRIBUTES.register("stun_time",
            () -> new RangedAttribute("attribute.yizmodqzk.stun_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 减速时间 — 共享，单位 tick。默认 20。 */
    public static final Holder<Attribute> SLOW_TIME =
        ATTRIBUTES.register("slow_time",
            () -> new RangedAttribute("attribute.yizmodqzk.slow_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 冰冻时间 — 共享，单位 tick。默认 20。 */
    public static final Holder<Attribute> FREEZE_TIME =
        ATTRIBUTES.register("freeze_time",
            () -> new RangedAttribute("attribute.yizmodqzk.freeze_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 感电时间 — 共享，单位 tick，持续时长。默认 20。 */
    public static final Holder<Attribute> SHOCK_TIME =
        ATTRIBUTES.register("shock_time",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 感电范围 — 共享，单位 格，连锁半径。默认 2。 */
    public static final Holder<Attribute> SHOCK_RANGE =
        ATTRIBUTES.register("shock_range",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_range", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 感电间隔 — 共享，单位 tick，AoE 爆发间隔。默认 10。 */
    public static final Holder<Attribute> SHOCK_INTERVAL =
        ATTRIBUTES.register("shock_interval",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_interval", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 击飞时间 — 共享，单位 tick。默认 20。 */
    public static final Holder<Attribute> KNOCKBACK_TIME =
        ATTRIBUTES.register("knockback_time",
            () -> new RangedAttribute("attribute.yizmodqzk.knockback_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    /** 眩晕伤害 — 共享，默认 2 点。 */
    public static final Holder<Attribute> STUN_DAMAGE =
        ATTRIBUTES.register("stun_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.stun_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 减速伤害 — 共享，默认 2 点。 */
    public static final Holder<Attribute> SLOW_DAMAGE =
        ATTRIBUTES.register("slow_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.slow_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 冰冻伤害 — 共享，默认 2 点。 */
    public static final Holder<Attribute> FREEZE_DAMAGE =
        ATTRIBUTES.register("freeze_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.freeze_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 感电伤害 — 共享，默认 2 点。 */
    public static final Holder<Attribute> SHOCK_DAMAGE =
        ATTRIBUTES.register("shock_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.shock_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 击飞伤害 — 共享，默认 2 点。 */
    public static final Holder<Attribute> KNOCKBACK_DAMAGE =
        ATTRIBUTES.register("knockback_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.knockback_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  触发器属性（后续大部分效果通过这三个入口驱动）
    // ═══════════════════════════════════════════════════════════

    /** 受击 — 受到伤害时通知次数。值域 ≥0。 */
    public static final Holder<Attribute> ON_HURT =
        ATTRIBUTES.register("on_hurt",
            () -> new RangedAttribute("attribute.yizmodqzk.on_hurt", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  反击效果属性
    // ═══════════════════════════════════════════════════════════

    /** 反击率 — 受击时触发反击的概率。值域 [0, 100]，1 = 1%。 */
    public static final Holder<Attribute> COUNTER_RATE =
        ATTRIBUTES.register("counter_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.counter_rate", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /** 反击值 — 攻击属性折扣百分比。值域 ≥0，1 = 1%，默认 50%。 */
    public static final Holder<Attribute> COUNTER_VALUE =
        ATTRIBUTES.register("counter_value",
            () -> new RangedAttribute("attribute.yizmodqzk.counter_value", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // 反击次数已移除（原 COUNTER_COUNT），固定为每次触发打 1 次。
    // ═══════════════════════════════════════════════════════════
    //  连击效果属性
    // ═══════════════════════════════════════════════════════════

    /** 连击 — 攻击时触发额外连击的概率。值域 [0, 100]，1 = 1%。 */
    public static final Holder<Attribute> COMBO_RATE =
        ATTRIBUTES.register("combo_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.combo_rate", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /** 连击倍率 — 连击伤害百分比。值域 ≥0，1 = 1%，默认 100%。 */
    public static final Holder<Attribute> COMBO_VALUE =
        ATTRIBUTES.register("combo_value",
            () -> new RangedAttribute("attribute.yizmodqzk.combo_value", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 连击次数 — 每次触发连击的攻击次数。值域 ≥0，无物品时 mixin 默认 1。 */
    public static final Holder<Attribute> COMBO_COUNT =
        ATTRIBUTES.register("combo_count",
            () -> new RangedAttribute("attribute.yizmodqzk.combo_count", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 复活 — 致死时消耗 1 点阻止死亡并恢复生命。
     * <p>值域 ≥0。1 = 1 次复活。</p>
     */
    public static final Holder<Attribute> UNDYING =
        ATTRIBUTES.register("undying",
            () -> new RangedAttribute("attribute.yizmodqzk.undying", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 破时 — 攻击时触发"破时"（清无敌帧 + Agent 绕过 Boss 自定义 hurt）的概率。
     * <p>值域 0~100，1 = 1%。与破时附魔等级的概率独立叠加：附魔命中后，
     * 再以本属性的概率决定是否真正激活 PoshiBypassBridge。</p>
     */
    public static final Holder<Attribute> POSHI =
        ATTRIBUTES.register("poshi",
            () -> new RangedAttribute("attribute.yizmodqzk.poshi", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 破限 — 攻击时触发"破限"（恢复被 cap 的原始伤害，穿透伤害上限）的概率。
     * <p>值域 0~100，1 = 1%。与破限附魔等级的概率独立叠加：附魔命中后，
     * 再以本属性的概率决定是否真正写入 PoxianDamageTracker。</p>
     */
    public static final Holder<Attribute> POXIAN =
        ATTRIBUTES.register("poxian",
            () -> new RangedAttribute("attribute.yizmodqzk.poxian", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /** 投射物反弹 — 以玩家为中心的反弹半径（格）。值域 ≥0。 */
    public static final Holder<Attribute> PROJECTILE_REFLECTION =
        ATTRIBUTES.register("projectile_reflection",
            () -> new RangedAttribute("attribute.yizmodqzk.projectile_reflection", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 穿过实体 — 1 = 穿过。值域 ≥0。 */
    public static final Holder<Attribute> NO_COLLISION =
        ATTRIBUTES.register("no_collision",
            () -> new RangedAttribute("attribute.yizmodqzk.no_collision", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 击退免疫 — 1 = 免疫。值域 ≥0。 */
    public static final Holder<Attribute> KNOCKBACK_IMMUNITY =
        ATTRIBUTES.register("knockback_immunity",
            () -> new RangedAttribute("attribute.yizmodqzk.knockback_immunity", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 投射物免疫 — 1 = 免疫。值域 ≥0。 */
    public static final Holder<Attribute> PROJECTILE_IMMUNITY =
        ATTRIBUTES.register("projectile_immunity",
            () -> new RangedAttribute("attribute.yizmodqzk.projectile_immunity", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  挖掘属性
    // ═══════════════════════════════════════════════════════════

    /**
     * 挖掘等级 — 决定可挖掘方块是否掉落。
     * <p>值域 ≥0。0=木, 1=石, 2=铁, 3=钻石, 4=下界合金。</p>
     */
    public static final Holder<Attribute> MINING_LEVEL =
        ATTRIBUTES.register("mining_level",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_level", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 挖掘类：镐 — 1=可挖掘镐类方块（石/矿等），无视手持物品。
     * <p>值域 0~1，二元开关。</p>
     */
    public static final Holder<Attribute> MINING_PICKAXE =
        ATTRIBUTES.register("mining_pickaxe",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_pickaxe", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 挖掘类：斧 — 1=可挖掘斧类方块（原木/木板等），无视手持物品。
     * <p>值域 0~1，二元开关。</p>
     */
    public static final Holder<Attribute> MINING_AXE =
        ATTRIBUTES.register("mining_axe",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_axe", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 挖掘类：铲 — 1=可挖掘铲类方块（泥土/沙子等），无视手持物品。
     * <p>值域 0~1，二元开关。</p>
     */
    public static final Holder<Attribute> MINING_SHOVEL =
        ATTRIBUTES.register("mining_shovel",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_shovel", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 挖掘类：全 — 1=可挖掘所有方块类别（含锄/剪刀等），无视手持物品。
     * <p>值域 0~1，二元开关。覆盖镐/斧/铲之外的所有类型。</p>
     */
    public static final Holder<Attribute> MINING_ALL =
        ATTRIBUTES.register("mining_all",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_all", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 免疫挖掘惩罚 — 1=免疫所有挖掘速度负面效果。
     * <p>包括：空中挖掘减速、水中挖掘减速、挖掘疲劳效果。
     * 值域 0~1，二元开关。</p>
     */
    public static final Holder<Attribute> MINING_PENALTY_IMMUNITY =
        ATTRIBUTES.register("mining_penalty_immunity",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_penalty_immunity", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /**
     * 挖掘效率 — 百分比加快挖掘速度。
     * <p>值域 ≥0，1 = 1%。100 = 速度翻倍。</p>
     */
    public static final Holder<Attribute> MINING_EFFICIENCY =
        ATTRIBUTES.register("mining_efficiency",
            () -> new RangedAttribute("attribute.yizmodqzk.mining_efficiency", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  堆叠模式 — 每个伤害增幅属性标记为乘法(MULTIPLY)或加法(ADD)
    // ═══════════════════════════════════════════════════════════

    public enum StackMode {
        /** 乘法叠加：amount *= (1 + Σ值)，各属性独立乘算 */
        MULTIPLY,
        /** 加法叠加：amount *= (1 + Σ值)，所有加法属性求和后一次乘 */
        ADD
    }

    private static final java.util.Map<Holder<Attribute>, StackMode> STACK_MODES = new java.util.HashMap<>();

    public static void setStackMode(Holder<Attribute> attr, StackMode mode) {
        STACK_MODES.put(attr, mode);
    }

    public static StackMode getStackMode(Holder<Attribute> attr) {
        return STACK_MODES.getOrDefault(attr, StackMode.MULTIPLY);
    }

    // ═══════════════════════════════════════════════════════════
    //  禁疗 + 特殊伤害属性（2026-08-05 新增，攻方消费于 hurt RETURN）
    // ═══════════════════════════════════════════════════════════

    /**
     * 禁疗率 — 攻击者拥有该属性时，目标每次治疗被削减等量百分比。
     * <p>值域 0~100，1 = 1%。消费：{@code LivingEntityMixin.onHurtReturn} → VitalitySeveranceConfig 治疗削减。</p>
     */
    public static final Holder<Attribute> VITALITY_SEVERANCE_RATE =
        ATTRIBUTES.register("vitality_severance_rate",
            () -> new RangedAttribute("attribute.yizmodqzk.vitality_severance_rate", 0.0, 0.0, 100.0)
                .setSyncable(true));

    /**
     * 禁疗时间 — 攻击者拥有该属性时，目标被完全禁疗该秒数；连续攻击刷新时长（取最新）。
     * <p>值域 ≥0，秒。消费：{@code LivingEntityMixin.onHurtReturn} → VitalitySeveranceHandler.addTempBan（put 覆盖天然刷新式）。</p>
     */
    public static final Holder<Attribute> VITALITY_SEVERANCE_TIME =
        ATTRIBUTES.register("vitality_severance_time",
            () -> new RangedAttribute("attribute.yizmodqzk.vitality_severance_time", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 最初梦幻 — 攻击者拥有该属性时，每次攻击用 {@code EntityASMUtil.modifyHealth} 对目标额外扣等量血
     * （Delta 通道，绕过 hurt / 无敌帧 / 减伤）。
     * <p>值域 ≥0，点。消费：{@code LivingEntityMixin.onHurtReturn}。</p>
     */
    public static final Holder<Attribute> FIRST_DREAM =
        ATTRIBUTES.register("first_dream",
            () -> new RangedAttribute("attribute.yizmodqzk.first_dream", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  传导限伤属性（2026-08-07 新增，目标方消费于 hurt 层，参考旧项目 SecureConductionCore）
    //  仅做「单发上限」限伤；CD = INVINCIBILITY_MULT 无敌帧（受击结算后 N tick 全挡 = 传导 CD）。
    // ═══════════════════════════════════════════════════════════

    /**
     * 单发伤害上限 — 目标拥有该属性时，单次有攻击者的伤害被限制为
     * {@code max(3, maxHealth × value/100)}（value = 最大生命值百分比，固定比例不随当前血量变化）。
     * <p>值域 ≥0。0 = 禁用单发限。消费：{@code LivingEntityMixin.modifyHurtAmount} 尾部 → ConductionDamageLimiter.conduct。</p>
     */
    public static final Holder<Attribute> CONDUCTION_CAP =
        ATTRIBUTES.register("conduction_cap",
            () -> new RangedAttribute("attribute.yizmodqzk.conduction_cap", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 传导受击间隔（CD）— 目标每次实际扣血后 N tick 内不再接受任何伤害（防连点快速耗血）。
     * <p>值域 ≥0，tick（20 = 1 秒）。0 = 禁用 CD。消费：辖界者实体 override hurt（写死保底 20）。</p>
     */
    public static final Holder<Attribute> CONDUCTION_INTERVAL =
        ATTRIBUTES.register("conduction_interval",
            () -> new RangedAttribute("attribute.yizmodqzk.conduction_interval", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 血量隐匿开关 — 目标拥有该属性（&gt;0）时，真实血量藏在 Lambda 闭包 + XOR 噪音，
     * vanilla 血量字段写随机游走诱饵（防外部内存扫描读取真实血量）。
     * <p>值域 0~1（二元开关）。0 = 关闭（默认，Delta/EntityHealthLocator 等主路径零改动）；
     * &gt;0 = 启用（该实体从上述系统显式排除）。消费：{@code SecureHealthClosure} + {@code LivingEntityMixin} 门控。</p>
     */
    public static final Holder<Attribute> SECURE_PULSE =
        ATTRIBUTES.register("secure_pulse",
            () -> new RangedAttribute("attribute.yizmodqzk.secure_pulse", 0.0, 0.0, 1.0)
                .setSyncable(true));

    /** 获取经过法术提升加成后的有效法术强度 = SPELL_POWER × (1 + MAGIC_DAMAGE/100)。 */
    public static double getEffectiveSpellPower(net.minecraft.world.entity.LivingEntity entity) {
        var sp = entity.getAttribute(SPELL_POWER);
        double base = sp != null ? sp.getValue() : 0;
        var md = entity.getAttribute(MAGIC_DAMAGE);
        double boost = md != null ? md.getValue() : 0;
        return base * (1.0 + boost / 100.0);
    }
}
