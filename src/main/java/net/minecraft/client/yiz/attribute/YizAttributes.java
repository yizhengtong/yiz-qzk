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
     * 渴攻 — 锁定充能时间（tick）。
     * <p>值域 ≥0，无上限。1 = 1 tick（0.05 秒）。</p>
     */
    public static final Holder<Attribute> KEGONG =
        ATTRIBUTES.register("kegong",
            () -> new RangedAttribute("attribute.yizmodqzk.kegong", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 格挡 — 固定值减免伤害（在百分比减免之后扣除）。
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
     * 防御力 — 每 1 点 = +1 护甲值 + +1 盔甲韧性。
     * <p>值域 ≥0。在每 tick 同步器中 1:1 镜像到原版 {@code ARMOR} 和 {@code ARMOR_TOUGHNESS}。</p>
     */
    public static final Holder<Attribute> ARMOR =
        ATTRIBUTES.register("armor",
            () -> new RangedAttribute("attribute.yizmodqzk.armor", 0.0, 0.0, Double.MAX_VALUE)
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
    /** 熔岩免疫时间 — 值域 ≥0，tick。 */
    public static final Holder<Attribute> LAVA_IMMUNE_TIME =
        ATTRIBUTES.register("lava_immune_time",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_immune_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 熔岩减伤 — 值域 ≥0，1 = 1%。 */
    public static final Holder<Attribute> LAVA_DAMAGE_REDUCTION =
        ATTRIBUTES.register("lava_damage_reduction",
            () -> new RangedAttribute("attribute.yizmodqzk.lava_damage_reduction", 0.0, 0.0, 100.0).setSyncable(true));
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
    /** 魔法伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> MAGIC_DAMAGE =
        ATTRIBUTES.register("magic_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.magic_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 召唤伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> SUMMON_DAMAGE =
        ATTRIBUTES.register("summon_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.summon_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 护甲穿透 — 值域 ≥0。 */
    public static final Holder<Attribute> ARMOR_PENETRATION =
        ATTRIBUTES.register("armor_penetration",
            () -> new RangedAttribute("attribute.yizmodqzk.armor_penetration", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 攻击距离 — 值域 ≥0，格。 */
    public static final Holder<Attribute> ATTACK_RANGE =
        ATTRIBUTES.register("attack_range",
            () -> new RangedAttribute("attribute.yizmodqzk.attack_range", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  特殊属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 飞行时间 — 值域 ≥0，tick。 */
    public static final Holder<Attribute> FLIGHT_TIME =
        ATTRIBUTES.register("flight_time",
            () -> new RangedAttribute("attribute.yizmodqzk.flight_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 跳跃速度 — 值域 ≥0。 */
    public static final Holder<Attribute> JUMP_SPEED =
        ATTRIBUTES.register("jump_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.jump_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大跌落保护 — 值域 ≥0，格。 */
    public static final Holder<Attribute> MAX_FALL_SAFE =
        ATTRIBUTES.register("max_fall_safe",
            () -> new RangedAttribute("attribute.yizmodqzk.max_fall_safe", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大仆从数 — 值域 ≥0。 */
    public static final Holder<Attribute> MAX_MINIONS =
        ATTRIBUTES.register("max_minions",
            () -> new RangedAttribute("attribute.yizmodqzk.max_minions", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 最大哨兵数 — 值域 ≥0。 */
    public static final Holder<Attribute> MAX_SENTRIES =
        ATTRIBUTES.register("max_sentries",
            () -> new RangedAttribute("attribute.yizmodqzk.max_sentries", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 水下呼吸时间 — 值域 ≥0，tick。 */
    public static final Holder<Attribute> WATER_BREATH_TIME =
        ATTRIBUTES.register("water_breath_time",
            () -> new RangedAttribute("attribute.yizmodqzk.water_breath_time", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  箭矢属性（迁自 EffectTag）
    // ═══════════════════════════════════════════════════════════

    /** 箭矢伤害 — 值域 ≥0。 */
    public static final Holder<Attribute> ARROW_DAMAGE =
        ATTRIBUTES.register("arrow_damage",
            () -> new RangedAttribute("attribute.yizmodqzk.arrow_damage", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 箭矢速度 — 值域 ≥0。 */
    public static final Holder<Attribute> ARROW_SPEED =
        ATTRIBUTES.register("arrow_speed",
            () -> new RangedAttribute("attribute.yizmodqzk.arrow_speed", 0.0, 0.0, Double.MAX_VALUE).setSyncable(true));
    /** 箭矢节省几率 — 值域 ≥0，1 = 1%。 */
    public static final Holder<Attribute> ARROW_SAVE_CHANCE =
        ATTRIBUTES.register("arrow_save_chance",
            () -> new RangedAttribute("attribute.yizmodqzk.arrow_save_chance", 0.0, 0.0, 100.0).setSyncable(true));

    // ═══════════════════════════════════════════════════════════
    //  触发器属性（后续大部分效果通过这三个入口驱动）
    // ═══════════════════════════════════════════════════════════

    /** 受击 — 受到伤害时通知次数。值域 ≥0。 */
    public static final Holder<Attribute> ON_HURT =
        ATTRIBUTES.register("on_hurt",
            () -> new RangedAttribute("attribute.yizmodqzk.on_hurt", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 攻击 — 造成攻击时通知次数。值域 ≥0。 */
    public static final Holder<Attribute> ON_ATTACK =
        ATTRIBUTES.register("on_attack",
            () -> new RangedAttribute("attribute.yizmodqzk.on_attack", 0.0, 0.0, Double.MAX_VALUE)
                .setSyncable(true));

    /** 时间 — 每 N tick 通知次数。值域 ≥0。 */
    public static final Holder<Attribute> ON_TICK =
        ATTRIBUTES.register("on_tick",
            () -> new RangedAttribute("attribute.yizmodqzk.on_tick", 0.0, 0.0, Double.MAX_VALUE)
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

    /** 反击数 — 每次触发连击次数。值域 ≥1。 */
    public static final Holder<Attribute> COUNTER_COUNT =
        ATTRIBUTES.register("counter_count",
            () -> new RangedAttribute("attribute.yizmodqzk.counter_count", 1.0, 1.0, Double.MAX_VALUE)
                .setSyncable(true));

    /**
     * 复活 — 致死时消耗 1 点阻止死亡并恢复生命。
     * <p>值域 ≥0。1 = 1 次复活。</p>
     */
    public static final Holder<Attribute> UNDYING =
        ATTRIBUTES.register("undying",
            () -> new RangedAttribute("attribute.yizmodqzk.undying", 0.0, 0.0, Double.MAX_VALUE)
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
}
