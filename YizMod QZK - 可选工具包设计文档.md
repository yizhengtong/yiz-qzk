# YizMod QZK - 可选工具包设计文档

> 本文档定义前置库提供的**可选工具包**，包括伤害计算、属性修改等通用逻辑。  
> 这些工具完全可选，开发者可以自由选择使用或完全自定义。

---

## 📌 设计原则

| 原则 | 说明 |
|------|------|
| **提供但不强制** | 所有默认实现都是 `protected` 方法，开发者可以不调用 |
| **可覆盖** | 开发者可以继承后 Override 默认行为 |
| **文档清晰** | 明确标注"可选工具"和"必须实现" |
| **零依赖** | 不使用默认实现的模组完全不受影响 |

---

## 📁 目录结构

```
src/main/java/net/minecraft/client/yiz/
│
└── tool/                                        # 可选工具包
    ├── damage/                                  # 伤害计算工具
    │   ├── DamageFormula.java                   # 伤害计算公式父类（定义数值接口）
    │   ├── DamageValueProvider.java             # 伤害数值提供接口（3个接口）
    │   ├── DamageTag.java                       # 伤害标签系统（真实/破甲/破无敌）
    │   ├── CustomDamageCalculator.java          # 自定义伤害计算器
    │   ├── DamageResult.java                    # 伤害结果数据模型
    │   └── DamageType.java                      # 伤害类型枚举
    │
    ├── health/                                  # 健康值修改工具
    │   ├── HealthModificationEvent.java         # 健康值修改事件
    │   ├── HealthModifier.java                  # 健康值修正器接口
    │   ├── HealthModificationManager.java       # 健康值修改管理器（汇总计算）
    │   ├── HealthModificationTrigger.java       # 健康值修改触发器
    │   └── HealthModificationResult.java        # 健康值修改结果
    │
    ├── attribute/                               # 属性修改工具
    │   └── AttributeModificationHelper.java     # 属性修改辅助工具
    │
    └── helper/                                  # 通用辅助工具
        ├── EffectContextHelper.java             # 上下文辅助工具
        └── ParticleEffectHelper.java            # 粒子效果辅助工具
```

---

## 💥 伤害接口系统设计

> **核心设计**：提供伤害计算公式父类和数值接口，支持自定义伤害和特殊标签。

---

### 1. DamageValueProvider.java（伤害数值提供接口）

**职责**：定义三个数值接口，影响最终伤害计算。

```java
/**
 * 伤害数值提供接口
 * 开发者实现此接口来提供伤害计算的三个关键数值
 */
public interface DamageValueProvider {
    
    /**
     * 接口1：目标最大生命值百分比
     * 
     * 说明：
     * - 基于目标最大生命值的百分比伤害
     * - 影响计算结果（通常作为独立乘区或最终修正）
     * - 示例：返回 0.1 表示造成目标最大生命值 10% 的伤害
     * 
     * @param target 目标实体
     * @return 百分比值（0.0 - 1.0）
     */
    double getTargetMaxHealthPercentage(Entity target);
    
    /**
     * 接口2：固定数值
     * 
     * 说明：
     * - 可以是正数（增加伤害）或负数（减少伤害）
     * - 影响计算结果（通常在基础值上加减）
     * - 示例：返回 5.0 表示增加5点固定伤害
     * 
     * @param context 效果上下文
     * @return 固定数值（可正可负）
     */
    double getFixedValue(EffectContext context);
    
    /**
     * 接口3：最终数值百分比提升
     * 
     * 说明：
     * - 对最终伤害进行百分比提升或降低
     * - 影响计算结果（作为最终乘区）
     * - 示例：返回 0.5 表示最终伤害提升50%
     * 
     * @param context 效果上下文
     * @return 百分比值（-1.0 到 +∞，0表示无变化）
     */
    double getFinalPercentageMultiplier(EffectContext context);
    
    /**
     * 默认实现：无百分比伤害
     */
    default double getTargetMaxHealthPercentage(Entity target) {
        return 0.0;  // 默认不使用百分比伤害
    }
    
    /**
     * 默认实现：无固定数值修正
     */
    default double getFixedValue(EffectContext context) {
        return 0.0;  // 默认无固定修正
    }
    
    /**
     * 默认实现：无最终百分比提升
     */
    default double getFinalPercentageMultiplier(EffectContext context) {
        return 0.0;  // 默认无最终修正
    }
}
```

---

### 2. DamageFormula.java（伤害计算公式父类）

**职责**：定义伤害计算的数值结构和计算流程。

```java
/**
 * 伤害计算公式父类
 * 定义数值结构和标准计算流程
 */
public abstract class DamageFormula {
    
    // 伤害标签集合
    protected final Set<DamageTag> tags = new HashSet<>();
    
    // 数值提供者
    protected DamageValueProvider valueProvider;
    
    /**
     * 计算最终伤害
     * 
     * 计算公式：
     * finalDamage = (baseDamage + fixedValue) 
     *              × (1 + maxHealthPercentage)
     *              × (1 + finalPercentageMultiplier)
     * 
     * @param context 效果上下文
     * @param baseDamage 基础伤害值
     * @return 伤害结果
     */
    public DamageResult calculate(EffectContext context, double baseDamage) {
        // 1. 获取固定数值（接口2）
        double fixedValue = valueProvider.getFixedValue(context);
        
        // 2. 基础伤害 + 固定修正
        double damageAfterFixed = baseDamage + fixedValue;
        
        // 3. 获取目标最大生命值百分比（接口1）
        double maxHealthPercentage = 0.0;
        if (context.target instanceof LivingEntity livingTarget) {
            maxHealthPercentage = valueProvider.getTargetMaxHealthPercentage(livingTarget);
            
            // 百分比伤害基于目标最大生命值
            if (maxHealthPercentage > 0) {
                double percentageDamage = livingTarget.getMaxHealth() * maxHealthPercentage;
                damageAfterFixed += percentageDamage;
            }
        }
        
        // 4. 获取最终百分比提升（接口3）
        double finalMultiplier = valueProvider.getFinalPercentageMultiplier(context);
        double finalDamage = damageAfterFixed * (1.0 + finalMultiplier);
        
        // 5. 确保伤害不为负
        finalDamage = Math.max(0, finalDamage);
        
        // 6. 构建结果
        return new DamageResult(
            finalDamage,
            baseDamage,
            context.effect.getId(),
            tags  // 附加标签
        );
    }
    
    /**
     * 添加伤害标签
     */
    public DamageFormula withTag(DamageTag tag) {
        this.tags.add(tag);
        return this;
    }
    
    /**
     * 检查是否包含特定标签
     */
    public boolean hasTag(DamageTag tag) {
        return tags.contains(tag);
    }
    
    /**
     * 设置数值提供者
     */
    public DamageFormula withValueProvider(DamageValueProvider provider) {
        this.valueProvider = provider;
        return this;
    }
}
```

---

### 3. DamageTag.java（伤害标签系统）

**职责**：定义三种特殊伤害标签，改变伤害的工作方式。

```java
/**
 * 伤害标签枚举
 * 三种标签可以独立使用，也可以组合使用
 */
public enum DamageTag {
    
    /**
     * 真实伤害标签
     * 
     * 特性：
     * - 伤害数值直接修改目标 Health
     * - 天生具备穿透无敌帧效果
     * - 不影响其他方面（仍然会受到其他标签影响）
     * 
     * 工作流程：
     * 1. 跳过 LivingEntity.hurt()
     * 2. 直接调用 target.setHealth(target.getHealth() - damage)
     * 3. 触发受伤动画和音效
     */
    TRUE_DAMAGE("true_damage", "真实伤害"),
    
    /**
     * 破甲伤害标签
     * 
     * 特性：
     * - 跳过护甲减伤计算
     * - 跳过 CombatRules.getDamageAfterAbsorb()
     * - 跳过韧性减伤
     * 
     * 工作流程：
     * 1. 不经过 LivingEntity.hurt() 的护甲减免
     * 2. 直接应用伤害数值
     * 3. 但仍然会受到无敌帧限制（除非同时有 PIERCE_INVULNERABILITY）
     */
    ARMOR_PIERCING("armor_piercing", "破甲伤害"),
    
    /**
     * 破除无敌帧标签
     * 
     * 特性：
     * - 无视目标的无敌帧（hurtTime）
     * - 不会重置无敌帧计时器
     * - 只有这部分伤害能够穿透无敌帧
     * 
     * 工作流程：
     * 1. 临时保存 hurtTime
     * 2. 设置 hurtTime = 0（允许伤害）
     * 3. 应用伤害
     * 4. 恢复 hurtTime（不重置）
     */
    PIERCE_INVULNERABILITY("pierce_invulnerability", "破除无敌帧");
    
    private final String id;
    private final String displayName;
    
    DamageTag(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取本地化显示名称
     */
    public Component getLocalizedDisplayName() {
        return Component.translatable("damage_tag.yizmodqzk." + id);
    }
}
```

---

### 4. CustomDamageCalculator.java（自定义伤害计算器）

**职责**：根据伤害标签应用不同的伤害逻辑。

```java
/**
 * 自定义伤害计算器
 * 根据伤害标签执行不同的伤害应用逻辑
 */
public class CustomDamageCalculator {
    
    /**
     * 应用伤害（自动根据标签选择应用方式）
     * 
     * @param context 效果上下文
     * @param damage 伤害结果
     */
    public static void applyDamage(EffectContext context, DamageResult damage) {
        if (context.target instanceof LivingEntity livingTarget) {
            // 检查标签组合
            boolean isTrueDamage = damage.hasTag(DamageTag.TRUE_DAMAGE);
            boolean isArmorPiercing = damage.hasTag(DamageTag.ARMOR_PIERCING);
            boolean pierceInvulnerability = damage.hasTag(DamageTag.PIERCE_INVULNERABILITY);
            
            // 根据标签选择应用方式
            if (isTrueDamage) {
                applyTrueDamage(context, livingTarget, damage);
            } else if (isArmorPiercing && pierceInvulnerability) {
                applyArmorPiercingAndPierceInvulnerability(context, livingTarget, damage);
            } else if (isArmorPiercing) {
                applyArmorPiercingDamage(context, livingTarget, damage);
            } else if (pierceInvulnerability) {
                applyPierceInvulnerabilityDamage(context, livingTarget, damage);
            } else {
                // 默认伤害（经过护甲和无敌帧）
                applyNormalDamage(context, livingTarget, damage);
            }
        }
    }
    
    /**
     * 真实伤害应用
     * - 直接修改 Health
     * - 穿透无敌帧
     */
    private static void applyTrueDamage(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        // 1. 保存当前生命值
        float currentHealth = target.getHealth();
        
        // 2. 直接设置生命值（跳过 hurt()）
        float newHealth = Math.max(0, currentHealth - (float)damage.finalDamage());
        target.setHealth(newHealth);
        
        // 3. 触发受伤动画
        target.hurtMarked = true;
        target.level().broadcastEntityEvent(target, (byte)2);
        
        // 4. 检查死亡
        if (newHealth <= 0) {
            target.die(context.entity.damageSources().playerAttack(context.entity));
        }
        
        // 5. 显示伤害数字
        showDamageNumber(target, damage.finalDamage(), DamageTag.TRUE_DAMAGE);
    }
    
    /**
     * 破甲伤害应用
     * - 跳过护甲减伤
     * - 仍然受无敌帧限制
     */
    private static void applyArmorPiercingDamage(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        // 1. 检查无敌帧
        if (target.invulnerableTime > 0) {
            return;  // 无敌帧期间不造成伤害
        }
        
        // 2. 创建伤害源（标记为魔法伤害，跳过护甲）
        DamageSource source = context.entity.damageSources().magic();
        
        // 3. 应用伤害（跳过护甲减伤）
        target.hurt(source, (float)damage.finalDamage());
        
        // 4. 显示伤害数字
        showDamageNumber(target, damage.finalDamage(), DamageTag.ARMOR_PIERCING);
    }
    
    /**
     * 破甲+破无敌帧伤害应用
     * - 跳过护甲减伤
     * - 无视无敌帧
     */
    private static void applyArmorPiercingAndPierceInvulnerability(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        // 1. 保存无敌帧计时器
        int savedInvulnerableTime = target.invulnerableTime;
        
        try {
            // 2. 临时清除无敌帧
            target.invulnerableTime = 0;
            
            // 3. 创建伤害源（跳过护甲）
            DamageSource source = context.entity.damageSources().magic();
            
            // 4. 应用伤害
            target.hurt(source, (float)damage.finalDamage());
            
        } finally {
            // 5. 恢复无敌帧计时器（不重置）
            target.invulnerableTime = savedInvulnerableTime;
        }
        
        // 6. 显示伤害数字
        showDamageNumber(target, damage.finalDamage(), 
            DamageTag.ARMOR_PIERCING, DamageTag.PIERCE_INVULNERABILITY);
    }
    
    /**
     * 仅破无敌帧伤害应用
     * - 经过护甲减伤
     * - 无视无敌帧
     */
    private static void applyPierceInvulnerabilityDamage(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        // 1. 保存无敌帧计时器
        int savedInvulnerableTime = target.invulnerableTime;
        
        try {
            // 2. 临时清除无敌帧
            target.invulnerableTime = 0;
            
            // 3. 创建普通伤害源（经过护甲）
            DamageSource source = context.entity.damageSources().playerAttack(context.entity);
            
            // 4. 应用伤害（经过护甲减伤）
            target.hurt(source, (float)damage.finalDamage());
            
        } finally {
            // 5. 恢复无敌帧计时器（不重置）
            target.invulnerableTime = savedInvulnerableTime;
        }
        
        // 6. 显示伤害数字
        showDamageNumber(target, damage.finalDamage(), DamageTag.PIERCE_INVULNERABILITY);
    }
    
    /**
     * 默认伤害应用
     * - 经过护甲减伤
     * - 受无敌帧限制
     */
    private static void applyNormalDamage(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        // 1. 创建伤害源
        DamageSource source = context.entity.damageSources().playerAttack(context.entity);
        
        // 2. 应用伤害（标准流程）
        target.hurt(source, (float)damage.finalDamage());
        
        // 3. 显示伤害数字
        showDamageNumber(target, damage.finalDamage());
    }
    
    /**
     * 显示伤害数字（粒子效果）
     */
    private static void showDamageNumber(
        LivingEntity target,
        double damage,
        DamageTag... tags
    ) {
        // 实现伤害数字显示逻辑
        // 可以使用粒子效果或自定义渲染
    }
}
```

---

### 5. DamageResult.java（更新版）

**职责**：支持伤害标签的伤害结果数据模型。

```java
public record DamageResult(
    double finalDamage,        // 最终伤害值
    double baseDamage,         // 基础伤害值
    ResourceLocation source,   // 伤害来源
    Set<DamageTag> tags,       // 伤害标签集合
    double knockback,          // 击退强度（默认0）
    double stunDuration,       // 硬直时长（秒，默认0）
    Map<String, Object> metadata  // 附加元数据
) {
    
    /**
     * 简化构造函数
     */
    public DamageResult(double finalDamage, double baseDamage, ResourceLocation source) {
        this(finalDamage, baseDamage, source, new HashSet<>(), 0.0, 0.0, new HashMap<>());
    }
    
    /**
     * 简化构造函数（带标签）
     */
    public DamageResult(
        double finalDamage, 
        double baseDamage, 
        ResourceLocation source,
        Set<DamageTag> tags
    ) {
        this(finalDamage, baseDamage, source, tags, 0.0, 0.0, new HashMap<>());
    }
    
    /**
     * 检查是否包含特定标签
     */
    public boolean hasTag(DamageTag tag) {
        return tags.contains(tag);
    }
    
    /**
     * 链式方法：添加标签
     */
    public DamageResult withTag(DamageTag tag) {
        Set<DamageTag> newTags = new HashSet<>(tags);
        newTags.add(tag);
        return new DamageResult(
            finalDamage, baseDamage, source,
            newTags, knockback, stunDuration, metadata
        );
    }
    
    // ... 其他链式方法（withKnockback, multiply 等） ...
}
```

---

## 📖 伤害接口使用示例

### 示例1：百分比伤害效果

```java
public class PercentDamageEffect extends AbstractEffect {
    public PercentDamageEffect() {
        super(ResourceLocation.fromNamespaceAndPath("mymod", "percent_damage"));
    }
    
    @Override
    public void execute(EffectContext context) {
        // 创建伤害公式
        DamageFormula formula = new DamageFormula() {
            {
                // 设置数值提供者
                withValueProvider(new DamageValueProvider() {
                    @Override
                    public double getTargetMaxHealthPercentage(Entity target) {
                        return 0.15;  // 造成目标最大生命值 15% 的伤害
                    }
                    
                    @Override
                    public double getFixedValue(EffectContext ctx) {
                        return 5.0;   // 额外增加5点固定伤害
                    }
                    
                    @Override
                    public double getFinalPercentageMultiplier(EffectContext ctx) {
                        return 0.2;   // 最终伤害提升20%
                    }
                });
            }
        };
        
        // 计算伤害
        DamageResult damage = formula.calculate(context, 10.0);
        
        // 应用伤害
        CustomDamageCalculator.applyDamage(context, damage);
    }
}
```

### 示例2：真实伤害效果

```java
public class TrueDamageEffect extends AbstractEffect {
    @Override
    public void execute(EffectContext context) {
        DamageFormula formula = new DamageFormula() {
            {
                withValueProvider(new DamageValueProvider() {
                    @Override
                    public double getFixedValue(EffectContext ctx) {
                        return 50.0;  // 50点真实伤害
                    }
                });
                
                // 添加真实伤害标签
                withTag(DamageTag.TRUE_DAMAGE);
            }
        };
        
        DamageResult damage = formula.calculate(context, 0);
        CustomDamageCalculator.applyDamage(context, damage);
    }
}
```

### 示例3：破甲+破无敌帧伤害

```java
public class UltimateSkillEffect extends AbstractEffect {
    @Override
    public void execute(EffectContext context) {
        DamageFormula formula = new DamageFormula() {
            {
                withValueProvider(new DamageValueProvider() {
                    @Override
                    public double getFixedValue(EffectContext ctx) {
                        return 100.0;  // 100点基础伤害
                    }
                    
                    @Override
                    public double getFinalPercentageMultiplier(EffectContext ctx) {
                        return 1.0;    // 最终伤害翻倍（+100%）
                    }
                });
                
                // 添加破甲和破无敌帧标签
                withTag(DamageTag.ARMOR_PIERCING);
                withTag(DamageTag.PIERCE_INVULNERABILITY);
            }
        };
        
        DamageResult damage = formula.calculate(context, 0);
        CustomDamageCalculator.applyDamage(context, damage);
    }
}
```

---

## 🎯 核心组件详细设计

### 1. DefaultDamageCalculator.java（默认伤害计算器）

**职责**：提供标准的伤害计算和应用逻辑，集成多乘区属性计算系统。

**设计目标**：
- ✅ 自动使用 `ModifierStack` 多乘区计算
- ✅ 自动处理护甲减伤
- ✅ 自动处理伤害类型
- ✅ 提供快捷方法一键计算并应用

---

#### 1.1 核心方法

```java
public class DefaultDamageCalculator {
    
    /**
     * 计算最终伤害（使用多乘区系统）
     * 
     * @param context 效果上下文
     * @param baseDamage 基础伤害值
     * @return 伤害结果（包含最终伤害、基础伤害、伤害来源）
     */
    public static DamageResult calculateDamage(
        EffectContext context, 
        double baseDamage
    ) {
        // 1. 获取效果的修正器
        List<AttributeModifier> additive = context.effect.getAdditiveModifiers();
        List<AttributeModifier> multiplicative = context.effect.getMultiplicativeModifiers();
        List<AttributeModifier> independent = context.effect.getIndependentModifiers();
        
        // 2. 使用多乘区计算
        double finalDamage = ModifierStack.calculate(
            baseDamage, additive, multiplicative, independent
        );
        
        // 3. 应用目标护甲减伤（可选开关）
        if (context.target instanceof LivingEntity livingTarget) {
            double armor = livingTarget.getArmorValue();
            finalDamage = applyArmorReduction(finalDamage, armor);
            
            // 处理护甲韧性（如果有）
            if (NeoForgeMod.isArmorToughnessEnabled()) {
                double toughness = livingTarget.getArmorToughness();
                finalDamage = applyArmorToughness(finalDamage, armor, toughness);
            }
        }
        
        // 4. 构建结果
        return new DamageResult(
            finalDamage,           // 最终伤害
            baseDamage,            // 基础伤害
            context.effect.getId() // 伤害来源
        );
    }
    
    /**
     * 应用伤害到目标
     * 
     * @param context 效果上下文
     * @param damage 伤害结果
     */
    public static void applyDamage(
        EffectContext context,
        DamageResult damage
    ) {
        if (context.target instanceof LivingEntity livingTarget) {
            // 创建伤害源
            DamageSource source = createDamageSource(context);
            
            // 应用伤害
            livingTarget.hurt(source, (float)damage.finalDamage());
            
            // 触发击退（可选）
            if (damage.knockback() > 0) {
                applyKnockback(context, livingTarget, damage);
            }
            
            // 触发硬直（可选）
            if (damage.stunDuration() > 0) {
                applyStun(context, livingTarget, damage.stunDuration());
            }
        }
    }
    
    /**
     * 快捷方法：计算并应用伤害
     * 
     * @param context 效果上下文
     * @param baseDamage 基础伤害值
     */
    public static void calculateAndApply(
        EffectContext context,
        double baseDamage
    ) {
        DamageResult result = calculateDamage(context, baseDamage);
        applyDamage(context, result);
    }
    
    /**
     * 快捷方法：计算并应用伤害（带击退）
     */
    public static void calculateAndApplyWithKnockback(
        EffectContext context,
        double baseDamage,
        double knockbackStrength
    ) {
        DamageResult result = calculateDamage(context, baseDamage)
            .withKnockback(knockbackStrength);
        applyDamage(context, result);
    }
    
    /**
     * 护甲减伤公式
     */
    private static double applyArmorReduction(double damage, double armor) {
        // Minecraft 原版护甲减伤公式
        double reduction = Math.min(armor / 5.0, 0.8);  // 最高减免80%
        return damage * (1.0 - reduction);
    }
    
    /**
     * 护甲韧性修正公式
     */
    private static double applyArmorToughness(
        double damage, 
        double armor, 
        double toughness
    ) {
        // NeoForge 护甲韧性公式
        double reduction = armor / (armor + toughness + 4.0);
        return damage * (1.0 - reduction);
    }
    
    /**
     * 创建伤害源
     */
    private static DamageSource createDamageSource(EffectContext context) {
        // 根据效果类型创建不同的伤害源
        return switch (context.effect.getParentType()) {
            case ECHO -> context.entity.damageSources().playerAttack(context.entity);
            case MANIFESTATION -> context.entity.damageSources().magic();
            case ORIGIN -> context.entity.damageSources().explosion(null);
            default -> context.entity.damageSources().generic();
        };
    }
    
    /**
     * 应用击退效果
     */
    private static void applyKnockback(
        EffectContext context,
        LivingEntity target,
        DamageResult damage
    ) {
        double knockback = damage.knockback();
        Vec3 direction = target.position()
            .subtract(context.entity.position())
            .normalize();
        
        target.push(
            direction.x * knockback,
            0.4,  // 垂直击退
            direction.z * knockback
        );
    }
}
```

---

#### 1.2 使用示例

```java
// 示例1：最简单的使用
@Override
public void execute(EffectContext context) {
    // 一行代码完成伤害计算和应用
    DefaultDamageCalculator.calculateAndApply(context, 10.0);
}

// 示例2：带击退
@Override
public void execute(EffectContext context) {
    DefaultDamageCalculator.calculateAndApplyWithKnockback(
        context, 
        15.0,   // 基础伤害
        1.5     // 击退强度
    );
}

// 示例3：分步使用（自定义中间处理）
@Override
public void execute(EffectContext context) {
    // 1. 计算伤害
    DamageResult damage = DefaultDamageCalculator.calculateDamage(context, 10.0);
    
    // 2. 自定义修正
    if (context.target.isOnFire()) {
        damage = damage.multiply(1.5);  // 燃烧目标受到1.5倍伤害
    }
    
    // 3. 应用伤害
    DefaultDamageCalculator.applyDamage(context, damage);
}
```

---

### 2. DamageResult.java（伤害结果数据模型）

**职责**：封装伤害计算的结果，支持链式修改。

```java
public record DamageResult(
    double finalDamage,        // 最终伤害值
    double baseDamage,         // 基础伤害值
    ResourceLocation source,   // 伤害来源
    double knockback,          // 击退强度（默认0）
    double stunDuration,       // 硬直时长（秒，默认0）
    Map<String, Object> metadata  // 附加元数据
) {
    
    /**
     * 简化构造函数
     */
    public DamageResult(double finalDamage, double baseDamage, ResourceLocation source) {
        this(finalDamage, baseDamage, source, 0.0, 0.0, new HashMap<>());
    }
    
    /**
     * 链式方法：设置击退
     */
    public DamageResult withKnockback(double knockback) {
        return new DamageResult(
            finalDamage, baseDamage, source,
            knockback, stunDuration, metadata
        );
    }
    
    /**
     * 链式方法：设置硬直
     */
    public DamageResult withStun(double durationSeconds) {
        return new DamageResult(
            finalDamage, baseDamage, source,
            knockback, durationSeconds, metadata
        );
    }
    
    /**
     * 链式方法：伤害倍率修正
     */
    public DamageResult multiply(double multiplier) {
        return new DamageResult(
            finalDamage * multiplier, baseDamage, source,
            knockback, stunDuration, metadata
        );
    }
    
    /**
     * 链式方法：添加元数据
     */
    public DamageResult withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return new DamageResult(
            finalDamage, baseDamage, source,
            knockback, stunDuration, newMetadata
        );
    }
    
    /**
     * 获取伤害类型标签
     */
    public String getDamageType() {
        return (String) metadata.getOrDefault("damage_type", "generic");
    }
}
```

---

### 3. DamageType.java（伤害类型枚举）

**职责**：定义标准化的伤害类型，用于伤害源创建和统计。

```java
public enum DamageType {
    // 物理伤害
    PHYSICAL("physical", "物理伤害"),
    SLASHING("slashing", "斩击伤害"),
    PIERCING("piercing", "穿刺伤害"),
    BLUNT("blunt", "钝击伤害"),
    
    // 元素伤害
    FIRE("fire", "火焰伤害"),
    ICE("ice", "冰霜伤害"),
    LIGHTNING("lightning", "闪电伤害"),
    POISON("poison", "毒素伤害"),
    
    // 魔法伤害
    MAGIC("magic", "魔法伤害"),
    ARCANE("arcane", "奥术伤害"),
    VOID("void", "虚空伤害"),
    
    // 特殊伤害
    TRUE("true", "真实伤害（无视护甲）"),
    PERCENTAGE("percentage", "百分比伤害"),
    REFLECT("reflect", "反伤");
    
    private final String id;
    private final String displayName;
    
    DamageType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取本地化显示名称
     */
    public Component getLocalizedDisplayName() {
        return Component.translatable("damage_type.yizmodqzk." + id);
    }
}
```

---

### 4. AttributeModificationHelper.java（属性修改辅助工具）

**职责**：提供标准化的实体属性修改方法。

```java
public class AttributeModificationHelper {
    
    /**
     * 修改实体属性（临时）
     * 
     * @param entity 目标实体
     * @param attribute 要修改的属性
     * @param value 修改值
     * @param operation 操作类型（ADDITION, MULTIPLY_BASE, MULTIPLY_TOTAL）
     * @param duration 持续时间（刻）
     */
    public static void modifyAttribute(
        LivingEntity entity,
        Attribute attribute,
        double value,
        AttributeModifier.Operation operation,
        int duration
    ) {
        // 创建属性修正器
        UUID modifierId = UUID.randomUUID();
        AttributeModifier modifier = new AttributeModifier(
            modifierId,
            "yizmodqzk_effect_modifier",
            value,
            operation
        );
        
        // 应用修正器
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifierId)) {
            instance.addTransientModifier(modifier);
        }
        
        // 设置定时器移除修正器
        if (duration > 0) {
            scheduleModifierRemoval(entity, attribute, modifierId, duration);
        }
    }
    
    /**
     * 修改实体属性（永久）
     */
    public static void modifyAttributePermanent(
        LivingEntity entity,
        Attribute attribute,
        double value,
        AttributeModifier.Operation operation
    ) {
        modifyAttribute(entity, attribute, value, operation, -1);
    }
    
    /**
     * 批量修改属性
     */
    public static void modifyMultipleAttributes(
        LivingEntity entity,
        List<AttributeModifierData> modifiers
    ) {
        for (AttributeModifierData data : modifiers) {
            modifyAttribute(
                entity,
                data.attribute(),
                data.value(),
                data.operation(),
                data.duration()
            );
        }
    }
    
    /**
     * 移除特定修正器
     */
    public static void removeModifier(
        LivingEntity entity,
        Attribute attribute,
        UUID modifierId
    ) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(modifierId);
        }
    }
    
    /**
     * 设置定时器移除修正器
     */
    private static void scheduleModifierRemoval(
        LivingEntity entity,
        Attribute attribute,
        UUID modifierId,
        int duration
    ) {
        // 使用 NeoForge 的定时器
        entity.getLevel().getServer().executeDelayed(() -> {
            removeModifier(entity, attribute, modifierId);
        }, duration);
    }
    
    /**
     * 属性修正器数据记录
     */
    public record AttributeModifierData(
        Attribute attribute,
        double value,
        AttributeModifier.Operation operation,
        int duration
    ) {}
}
```

---

#### 4.1 使用示例

```java
// 示例1：临时增加攻击力
@Override
public void execute(EffectContext context) {
    AttributeModificationHelper.modifyAttribute(
        context.entity,
        Attributes.ATTACK_DAMAGE,
        5.0,                          // 增加5点攻击力
        AttributeModifier.Operation.ADDITION,
        200                           // 持续200刻（10秒）
    );
}

// 示例2：百分比增加移动速度
@Override
public void execute(EffectContext context) {
    AttributeModificationHelper.modifyAttribute(
        context.entity,
        Attributes.MOVEMENT_SPEED,
        0.2,                          // 增加20%移动速度
        AttributeModifier.Operation.MULTIPLY_TOTAL,
        100                           // 持续100刻（5秒）
    );
}

// 示例3：批量修改
@Override
public void execute(EffectContext context) {
    List<AttributeModifierData> modifiers = List.of(
        new AttributeModifierData(
            Attributes.ATTACK_DAMAGE, 5.0, 
            AttributeModifier.Operation.ADDITION, 200
        ),
        new AttributeModifierData(
            Attributes.ARMOR, 3.0, 
            AttributeModifier.Operation.ADDITION, 200
        ),
        new AttributeModifierData(
            Attributes.MOVEMENT_SPEED, 0.1, 
            AttributeModifier.Operation.MULTIPLY_TOTAL, 100
        )
    );
    
    AttributeModificationHelper.modifyMultipleAttributes(
        context.entity, modifiers
    );
}
```

---

### 5. AbstractEffect.java 中的快捷方法

**职责**：在效果基类中提供便捷方法，让开发者可以快速调用工具包。

```java
public abstract class AbstractEffect {
    // ... 六大维度属性 ...
    
    /**
     * 效果执行方法（必须由开发者实现）
     */
    public abstract void execute(EffectContext context);
    
    // ==================== 可选工具：伤害计算 ====================
    
    /**
     * 【可选工具】使用默认伤害计算
     * 
     * 开发者可以在 execute() 中调用此方法快速应用伤害
     * 
     * @param context 效果上下文
     * @param baseDamage 基础伤害值
     */
    protected void executeDefaultDamage(
        EffectContext context, 
        double baseDamage
    ) {
        DefaultDamageCalculator.calculateAndApply(context, baseDamage);
    }
    
    /**
     * 【可选工具】使用默认伤害计算（带击退）
     */
    protected void executeDefaultDamageWithKnockback(
        EffectContext context,
        double baseDamage,
        double knockbackStrength
    ) {
        DefaultDamageCalculator.calculateAndApplyWithKnockback(
            context, baseDamage, knockbackStrength
        );
    }
    
    // ==================== 可选工具：属性修改 ====================
    
    /**
     * 【可选工具】使用默认属性修改（临时）
     */
    protected void executeDefaultAttributeModification(
        EffectContext context,
        Attribute attribute,
        double value,
        AttributeModifier.Operation operation,
        int durationTicks
    ) {
        AttributeModificationHelper.modifyAttribute(
            context.entity, attribute, value, operation, durationTicks
        );
    }
    
    /**
     * 【可选工具】使用默认属性修改（永久）
     */
    protected void executeDefaultAttributeModificationPermanent(
        EffectContext context,
        Attribute attribute,
        double value,
        AttributeModifier.Operation operation
    ) {
        AttributeModificationHelper.modifyAttributePermanent(
            context.entity, attribute, value, operation
        );
    }
    
    /**
     * 【可选工具】批量修改属性
     */
    protected void executeDefaultMultipleAttributeModifications(
        EffectContext context,
        List<AttributeModificationHelper.AttributeModifierData> modifiers
    ) {
        AttributeModificationHelper.modifyMultipleAttributes(
            context.entity, modifiers
        );
    }
    
    // ==================== 必须实现：自定义逻辑 ====================
    
    /**
     * 【必须实现】自定义效果执行逻辑
     * 
     * 开发者必须实现此方法，可以选择：
     * 1. 调用上述快捷方法（使用默认实现）
     * 2. 完全自定义逻辑（不使用默认实现）
     * 3. 混合使用（部分默认 + 部分自定义）
     */
    @Override
    public abstract void execute(EffectContext context);
}
```

---

## 💚 健康值修改接口系统设计

> **核心设计**：提供健康值修改的事件总线、修正器接口和汇总计算机制，支持多模组协同修改。

---

### 1. 设计理念

#### 问题场景：
```
假设有 3 个模组同时想修改玩家健康值：
- 模组A：生命偷取效果，+10 健康值
- 模组B：中毒效果，-5 健康值
- 模组C：再生效果，+8 健康值

如果各自独立修改：
❌ 可能造成冲突
❌ 可能重复计算
❌ 无法预测最终结果

使用前置库的汇总机制：
✅ 自动收集所有修改
✅ 统一计算：10 + (-5) + 8 = 13
✅ 一次性应用最终结果
```

#### 核心流程：
```
1. 效果触发
   ↓
2. 发送 HealthModificationEvent
   ↓
3. 所有模组监听并添加修正器
   ↓
4. HealthModificationManager 汇总所有修正
   ↓
5. 计算最终结果
   ↓
6. 应用到实体健康值
```

---

### 2. HealthModifier.java（健康值修正器接口）

**职责**：定义健康值修改的数值来源，开发者实现此接口提供修改值。

```java
/**
 * 健康值修正器接口
 * 开发者实现此接口来提供健康值修改
 */
public interface HealthModifier {
    
    /**
     * 获取修正器唯一标识
     * 用于去重和调试
     */
    ResourceLocation getId();
    
    /**
     * 获取健康值修改量
     * 
     * @param entity 目标实体
     * @param context 效果上下文
     * @return 修改量（正数=治疗，负数=伤害）
     */
    double getModificationAmount(LivingEntity entity, EffectContext context);
    
    /**
     * 获取修正器优先级
     * 
     * 说明：
     * - 优先级高的修正器先计算
     * - 相同优先级按注册顺序计算
     * 
     * @return 优先级（数字越大越先计算）
     */
    default int getPriority() {
        return 0;  // 默认优先级
    }
    
    /**
     * 修正器类型
     * 
     * @return 类型（用于分类和统计）
     */
    default ModifierType getType() {
        return ModifierType.CUSTOM;
    }
    
    /**
     * 修正器类型枚举
     */
    enum ModifierType {
        HEALING("healing", "治疗"),
        DAMAGE("damage", "伤害"),
        REGEN("regen", "再生"),
        POISON("poison", "中毒"),
        LIFESTEAL("lifesteal", "生命偷取"),
        CUSTOM("custom", "自定义");
        
        private final String id;
        private final String displayName;
        
        ModifierType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() {
            return id;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}
```

---

### 3. HealthModificationEvent.java（健康值修改事件）

**职责**：携带修改上下文，收集所有修正器。

```java
/**
 * 健康值修改事件
 * 用于在模组间传递健康值修改请求
 */
public class HealthModificationEvent {
    
    // 目标实体
    private final LivingEntity targetEntity;
    
    // 效果上下文
    private final EffectContext context;
    
    // 修正器列表（所有模组添加的修正器都在这里）
    private final List<HealthModifier> modifiers = new ArrayList<>();
    
    // 事件是否已取消
    private boolean canceled = false;
    
    // 取消原因
    private String cancelReason = "";
    
    public HealthModificationEvent(LivingEntity targetEntity, EffectContext context) {
        this.targetEntity = targetEntity;
        this.context = context;
    }
    
    /**
     * 添加修正器
     * 
     * @param modifier 修正器实例
     */
    public void addModifier(HealthModifier modifier) {
        // 去重：相同ID的修正器只添加一次
        if (modifiers.stream().noneMatch(m -> m.getId().equals(modifier.getId()))) {
            modifiers.add(modifier);
        }
    }
    
    /**
     * 添加多个修正器
     */
    public void addModifiers(List<HealthModifier> modifiers) {
        for (HealthModifier modifier : modifiers) {
            addModifier(modifier);
        }
    }
    
    /**
     * 获取所有修正器
     */
    public List<HealthModifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }
    
    /**
     * 获取目标实体
     */
    public LivingEntity getTargetEntity() {
        return targetEntity;
    }
    
    /**
     * 获取效果上下文
     */
    public EffectContext getContext() {
        return context;
    }
    
    /**
     * 取消事件
     */
    public void cancel(String reason) {
        this.canceled = true;
        this.cancelReason = reason;
    }
    
    /**
     * 检查事件是否已取消
     */
    public boolean isCanceled() {
        return canceled;
    }
    
    /**
     * 获取取消原因
     */
    public String getCancelReason() {
        return cancelReason;
    }
}
```

---

### 4. HealthModificationManager.java（健康值修改管理器）

**职责**：汇总所有修正器，计算最终结果，应用到实体。

```java
/**
 * 健康值修改管理器
 * 负责收集、排序、汇总所有健康值修改
 */
public class HealthModificationManager {
    
    /**
     * 执行健康值修改
     * 
     * 完整流程：
     * 1. 创建事件
     * 2. 发布事件（所有模组监听）
     * 3. 收集所有修正器
     * 4. 按优先级排序
     * 5. 计算最终结果
     * 6. 应用到实体
     * 
     * @param entity 目标实体
     * @param context 效果上下文
     * @return 修改结果
     */
    public static HealthModificationResult executeModification(
        LivingEntity entity,
        EffectContext context
    ) {
        // 1. 创建事件
        HealthModificationEvent event = new HealthModificationEvent(entity, context);
        
        // 2. 发布事件（通过 EventBus）
        YizMod.EVENT_BUS.post(event);
        
        // 3. 检查事件是否被取消
        if (event.isCanceled()) {
            return HealthModificationResult.canceled(event.getCancelReason());
        }
        
        // 4. 获取所有修正器
        List<HealthModifier> modifiers = event.getModifiers();
        
        // 5. 按优先级排序（高优先级先计算）
        modifiers.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        // 6. 计算最终结果
        double totalModification = 0;
        List<ModificationDetail> details = new ArrayList<>();
        
        for (HealthModifier modifier : modifiers) {
            double amount = modifier.getModificationAmount(entity, context);
            
            // 记录详细信息
            details.add(new ModificationDetail(
                modifier.getId(),
                modifier.getType(),
                amount
            ));
            
            // 累加
            totalModification += amount;
        }
        
        // 7. 应用修改到实体
        applyToEntity(entity, totalModification);
        
        // 8. 返回结果
        return HealthModificationResult.success(
            totalModification,
            entity.getHealth(),
            details
        );
    }
    
    /**
     * 应用修改到实体
     * 
     * 注意：具体实现等你实际动工时再规划
     * 这里只提供接口，不写具体逻辑
     */
    private static void applyToEntity(LivingEntity entity, double modification) {
        // TODO: 实现具体健康值修改逻辑
        // - 如何修改 Health？
        // - 是否需要触发治疗/受伤动画？
        // - 是否需要检查上限/下限？
        // - 是否需要记录日志？
        
        // 临时实现（示例）
        if (modification > 0) {
            // 治疗
            float newHealth = Math.min(
                entity.getMaxHealth(),
                entity.getHealth() + (float)modification
            );
            entity.setHealth(newHealth);
        } else if (modification < 0) {
            // 伤害
            float newHealth = Math.max(
                0,
                entity.getHealth() + (float)modification
            );
            entity.setHealth(newHealth);
            
            // 检查死亡
            if (newHealth <= 0) {
                entity.die(entity.damageSources().generic());
            }
        }
    }
    
    /**
     * 快捷方法：触发健康值修改
     */
    public static HealthModificationResult triggerModification(
        LivingEntity entity,
        EffectContext context
    ) {
        return executeModification(entity, context);
    }
}
```

---

### 5. HealthModificationTrigger.java（健康值修改触发器）

**职责**：定义何时触发健康值修改。

```java
/**
 * 健康值修改触发器
 * 定义触发时机和条件
 */
public interface HealthModificationTrigger {
    
    /**
     * 检查是否应该触发健康值修改
     * 
     * @param entity 目标实体
     * @param context 效果上下文
     * @return true = 触发，false = 不触发
     */
    boolean shouldTrigger(LivingEntity entity, EffectContext context);
    
    /**
     * 获取触发器名称
     */
    String getName();
}

/**
 * 内置触发器实现
 */
public class BuiltInTriggers {
    
    /**
     * 定时触发器（每隔N刻触发一次）
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
    }
    
    /**
     * 条件触发器（满足条件时触发）
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
     * 事件触发器（特定事件发生时触发）
     */
    public static class EventTrigger implements HealthModificationTrigger {
        private final String triggerEvent;
        
        public EventTrigger(String triggerEvent) {
            this.triggerEvent = triggerEvent;
        }
        
        @Override
        public boolean shouldTrigger(LivingEntity entity, EffectContext context) {
            // 检查上下文是否包含特定事件
            return context.metadata.containsKey(triggerEvent);
        }
        
        @Override
        public String getName() {
            return "EventTrigger(" + triggerEvent + ")";
        }
    }
    
    /**
     * 立即触发器（总是触发）
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
}
```

---

### 6. HealthModificationResult.java（健康值修改结果）

**职责**：记录修改的详细信息，用于调试和统计。

```java
/**
 * 健康值修改结果
 * 记录修改的详细信息
 */
public record HealthModificationResult(
    boolean success,                    // 是否成功
    double totalModification,           // 总修改量
    float currentHealth,                // 当前健康值
    List<ModificationDetail> details,   // 详细修改记录
    String cancelReason                 // 取消原因（如果取消）
) {
    
    /**
     * 创建成功结果
     */
    public static HealthModificationResult success(
        double totalModification,
        float currentHealth,
        List<ModificationDetail> details
    ) {
        return new HealthModificationResult(
            true, totalModification, currentHealth, details, ""
        );
    }
    
    /**
     * 创建取消结果
     */
    public static HealthModificationResult canceled(String reason) {
        return new HealthModificationResult(
            false, 0, 0, List.of(), reason
        );
    }
    
    /**
     * 修改详细信息
     */
    public record ModificationDetail(
        ResourceLocation modifierId,    // 修正器ID
        HealthModifier.ModifierType type,  // 修正器类型
        double amount                   // 修改量
    ) {
        /**
         * 获取本地化描述
         */
        public Component getLocalizedDescription() {
            String sign = amount > 0 ? "+" : "";
            return Component.translatable(
                "health_modification.yizmodqzk.detail",
                modifierId.toString(),
                type.getDisplayName(),
                sign + amount
            );
        }
    }
}
```

---

## 📖 健康值修改使用示例

### 示例1：多模组协同修改

```java
// ===== 模组A：生命偷取效果 =====
public class LifeStealModifier implements HealthModifier {
    private static final ResourceLocation ID = 
        ResourceLocation.fromNamespaceAndPath("modA", "lifesteal");
    
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public double getModificationAmount(LivingEntity entity, EffectContext context) {
        // 偷取造成伤害的 20%
        double damageDealt = (double) context.metadata.getOrDefault("damage_dealt", 0.0);
        return damageDealt * 0.2;  // 返回正数（治疗）
    }
    
    @Override
    public int getPriority() {
        return 10;  // 高优先级
    }
    
    @Override
    public ModifierType getType() {
        return ModifierType.LIFESTEAL;
    }
}

// ===== 模组B：中毒效果 =====
public class PoisonModifier implements HealthModifier {
    private static final ResourceLocation ID = 
        ResourceLocation.fromNamespaceAndPath("modB", "poison");
    
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public double getModificationAmount(LivingEntity entity, EffectContext context) {
        // 每秒造成 5 点伤害
        return -5.0;  // 返回负数（伤害）
    }
    
    @Override
    public int getPriority() {
        return 5;  // 中优先级
    }
    
    @Override
    public ModifierType getType() {
        return ModifierType.POISON;
    }
}

// ===== 模组C：再生效果 =====
public class RegenModifier implements HealthModifier {
    private static final ResourceLocation ID = 
        ResourceLocation.fromNamespaceAndPath("modC", "regen");
    
    @Override
    public ResourceLocation getId() {
        return ID;
    }
    
    @Override
    public double getModificationAmount(LivingEntity entity, EffectContext context) {
        // 每秒恢复 8 点生命
        return 8.0;  // 返回正数（治疗）
    }
    
    @Override
    public int getPriority() {
        return 8;  // 中高优先级
    }
    
    @Override
    public ModifierType getType() {
        return ModifierType.REGEN;
    }
}

// ===== 前置库自动汇总 =====
// 当触发健康值修改时：
// 1. 发布 HealthModificationEvent
// 2. 模组A添加：+10（假设造成伤害50，20%=10）
// 3. 模组B添加：-5
// 4. 模组C添加：+8
// 5. HealthModificationManager 汇总：10 + (-5) + 8 = 13
// 6. 应用到实体：entity.setHealth(entity.getHealth() + 13)
```

---

### 示例2：效果中使用健康值修改

```java
public class RegenAuraEffect extends AbstractEffect {
    private final HealthModificationTrigger trigger = 
        new BuiltInTriggers.IntervalTrigger(20);  // 每20刻（1秒）触发一次
    
    @Override
    public void execute(EffectContext context) {
        // 检查是否应该触发
        if (!trigger.shouldTrigger(context.entity, context)) {
            return;
        }
        
        // 创建修正器
        HealthModifier regenModifier = new HealthModifier() {
            @Override
            public ResourceLocation getId() {
                return ResourceLocation.fromNamespaceAndPath(
                    "yizmodqzk", "regen_aura"
                );
            }
            
            @Override
            public double getModificationAmount(
                LivingEntity entity, 
                EffectContext ctx
            ) {
                // 每秒恢复 5 点生命
                return 5.0;
            }
            
            @Override
            public ModifierType getType() {
                return ModifierType.REGEN;
            }
        };
        
        // 触发修改
        HealthModificationResult result = 
            HealthModificationManager.triggerModification(
                context.entity, 
                context
            );
        
        // 处理结果（可选）
        if (result.success()) {
            System.out.println("健康值修改成功: " + result.totalModification());
        }
    }
}
```

---

### 示例3：监听事件添加修正器

```java
// 模组中注册事件监听器
public class MyModEventHandler {
    
    @SubscribeEvent
    public void onHealthModification(HealthModificationEvent event) {
        LivingEntity entity = event.getTargetEntity();
        
        // 检查是否是玩家
        if (entity instanceof Player player) {
            // 检查是否穿戴了特定装备
            if (hasRegenArmor(player)) {
                // 添加健康值修正器
                event.addModifier(new HealthModifier() {
                    @Override
                    public ResourceLocation getId() {
                        return ResourceLocation.fromNamespaceAndPath(
                            "mymod", "regen_armor_bonus"
                        );
                    }
                    
                    @Override
                    public double getModificationAmount(
                        LivingEntity target, 
                        EffectContext ctx
                    ) {
                        return 3.0;  // 额外恢复3点生命
                    }
                    
                    @Override
                    public ModifierType getType() {
                        return ModifierType.HEALING;
                    }
                });
            }
        }
    }
    
    private boolean hasRegenArmor(Player player) {
        // 检查装备逻辑
        return true;
    }
}
```

---

## ⚔️ 攻击强制执行与拦截系统

> **核心设计**：在攻击方法开头进行拦截，检测特殊标签（真实伤害、穿甲、健康值修改），若存在则强制执行，禁止被其他模组拦截或取消。

---

### 1. 设计理念

#### 问题场景：
某些特殊攻击（如真实伤害、穿甲、直接健康值修改）需要**绝对命中**且**无视规避**。
如果走标准 `hurt()` 流程，可能会被其他模组的护盾、闪避、无敌等机制拦截或取消。

#### 解决方案：
1. **前置拦截**：在 `LivingEntity.attack(Entity)` 方法开头注入。
2. **标签检测**：检测攻击方持有的物品或效果是否带有 `TRUE_DAMAGE`、`ARMOR_PIERCING` 或 `DIRECT_HEALTH_MOD` 标签。
3. **强制执行**：
   - 若带有**真实伤害/穿甲**标签：直接调用底层伤害逻辑，禁止 `AttackEvent` 取消，禁止目标闪避。
   - 若带有**健康值修改**标签：直接应用 `HealthModificationManager` 的计算结果，绕过 `hurt()` 流程。

---

### 2. AttackInterceptorMixin（攻击拦截 Mixin）

**职责**：在攻击发生时第一时间介入，判断是否需要强制执行。

```java
@Mixin(LivingEntity.class)
public abstract class AttackInterceptorMixin {

    /**
     * 在攻击方法开头注入
     * 拦截试图攻击的实体，并检查是否含有强制执行标签
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yizmodqzk$onAttackStart(Entity target, CallbackInfo ci) {
        LivingEntity attacker = (LivingEntity) (Object) this;
        
        // 1. 获取攻击上下文（包含手持物品、活跃效果等）
        AttackContext context = AttackContext.create(attacker, target);
        
        // 2. 检测是否包含“强制执行”标签（真实伤害 或 穿甲）
        if (context.hasEnforcementTag(DamageTag.TRUE_DAMAGE) || 
            context.hasEnforcementTag(DamageTag.ARMOR_PIERCING)) {
            
            // 3. 强制执行攻击
            DirectAttackExecutor.executeForcedAttack(attacker, target, context);
            
            // 4. 取消原版 attack 方法，防止被其他逻辑二次处理或拦截
            // 此时攻击已经由我们底层处理，不允许任何模组拦截返回
            ci.cancel();
            return;
        }
        
        // 5. 检测是否包含“直接健康值修改”逻辑
        if (context.hasEnforcementTag(DamageTag.DIRECT_HEALTH_MOD)) {
            // 直接作用于实体，不走整套伤害流程
            DirectHealthModExecutor.executeDirectHealthMod(attacker, target, context);
            ci.cancel();
            return;
        }
    }
}
```

---

### 3. DirectAttackExecutor.java（直接攻击执行器）

**职责**：处理真实伤害和穿甲标签的强制执行，确保不被拦截。

```java
public class DirectAttackExecutor {
    
    public static void executeForcedAttack(
        LivingEntity attacker, 
        Entity target, 
        AttackContext context
    ) {
        if (!(target instanceof LivingEntity livingTarget)) return;
        
        boolean isTrueDamage = context.hasEnforcementTag(DamageTag.TRUE_DAMAGE);
        boolean isArmorPiercing = context.hasEnforcementTag(DamageTag.ARMOR_PIERCING);
        
        // 计算伤害（使用之前的伤害公式）
        DamageResult damage = context.calculateDamage();
        
        if (isTrueDamage) {
            // 真实伤害：直接扣除 Health，无视无敌帧、护甲、闪避
            // 注意：这里直接修改数值，不触发 hurt() 的复杂判定
            float currentHealth = livingTarget.getHealth();
            livingTarget.setHealth(Math.max(0, currentHealth - (float)damage.finalDamage()));
            
            // 可选：触发生物受击动画，避免看起来像没打中
            livingTarget.level().broadcastEntityEvent(livingTarget, (byte)2);
            
        } else if (isArmorPiercing) {
            // 穿甲伤害：强制命中，跳过护甲减免
            // 绕过标准 hurt 的护甲计算，直接应用伤害
            
            // 标记为魔法伤害以跳过护甲（或者使用自定义 DamageSource）
            DamageSource source = attacker.damageSources().magic();
            
            // 确保此次攻击不会被 LivingEntity.hurt 中的其他检查拦截
            // 例如：通过 Mixin 临时禁用目标的 isInvulnerableTo 检查（需小心处理）
            // 或者使用一个特殊的 DamageSource 让 hurt 认为是“必中”的
            
            livingTarget.hurt(source, (float)damage.finalDamage());
        }
        
        // 处理击退等后续效果
        context.applyPostAttackEffects();
    }
}
```

---

### 4. DirectHealthModExecutor.java（直接健康值修改执行器）

**职责**：处理健康值修改标签，直接调用健康值修改管理器，不走伤害流程。

```java
public class DirectHealthModExecutor {
    
    public static void executeDirectHealthMod(
        LivingEntity attacker, 
        Entity target, 
        AttackContext context
    ) {
        if (!(target instanceof LivingEntity livingTarget)) return;
        
        // 构建效果上下文
        EffectContext effContext = EffectContext.create(attacker, target);
        
        // 直接调用健康值修改管理器
        // 这里不走 hurt 流程，因此不会被“伤害免疫”、“闪避”等机制影响
        HealthModificationResult result = HealthModificationManager.executeModification(
            livingTarget, 
            effContext
        );
        
        // 处理结果（可选）
        if (result.success()) {
            // 例如播放治疗音效或粒子
            if (result.totalModification() > 0) {
                // 治疗特效
            } else {
                // 扣血特效
            }
        }
    }
}
```

---

### 5. AttackContext.java（攻击上下文）

**职责**：收集攻击相关的所有数据，用于标签检测。

```java
public class AttackContext {
    public final LivingEntity attacker;
    public final Entity target;
    
    // 持有的物品
    public final ItemStack mainHandItem;
    public final ItemStack offHandItem;
    
    // 激活的效果列表
    public final List<AbstractEffect> activeEffects;
    
    private AttackContext(LivingEntity attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
        this.mainHandItem = attacker.getMainHandItem();
        this.offHandItem = attacker.getOffhandItem();
        // 获取所有相关效果...
        this.activeEffects = YizModRegistries.getActiveEffects(attacker);
    }
    
    public static AttackContext create(LivingEntity attacker, Entity target) {
        return new AttackContext(attacker, target);
    }
    
    /**
     * 检查是否包含特定强制执行标签
     * 逻辑：检查手持物品词缀 + 实体天赋 + 随影
     */
    public boolean hasEnforcementTag(DamageTag tag) {
        // 1. 检查主手物品词缀
        if (ItemEffectUtils.hasTag(mainHandItem, tag)) return true;
        
        // 2. 检查副手物品词缀
        if (ItemEffectUtils.hasTag(offHandItem, tag)) return true;
        
        // 3. 检查实体天赋
        for (AbstractEffect effect : activeEffects) {
            if (effect.getAssociatedTags().contains(tag)) return true;
        }
        
        return false;
    }
    
    public DamageResult calculateDamage() {
        // 调用伤害计算逻辑
        return DamageCalculator.calculate(this);
    }
    
    public void applyPostAttackEffects() {
        // 击退、触发其他效果等
    }
}
```

---

## 📖 开发者使用指南

### 场景1：新手开发者（使用默认实现）

```java
public class FireSwordAffix extends AbstractEffect {
    public FireSwordAffix() {
        super(ResourceLocation.fromNamespaceAndPath("mymod", "fire_sword"));
        this.parentType = ParentType.ECHO;
        this.level = 5;
        this.perceptionModes = Set.of(ItemPerception.MAIN_HAND);
        this.activationCondition = new EntityAttackCondition();
        this.rarity = Rarity.EPIC;
    }
    
    @Override
    public void execute(EffectContext context) {
        // ✅ 使用前置库的默认伤害计算
        executeDefaultDamage(context, 10.0);
        
        // 额外的火焰效果
        context.target.setSecondsOnFire(5);
    }
}
```

**优势**：
- 无需理解复杂的伤害系统
- 自动使用多乘区属性计算
- 自动处理护甲减伤
- 只需关注自定义效果（点燃）

---

### 场景2：高级开发者（完全自定义）

```java
public class CustomMagicDamage extends AbstractEffect {
    @Override
    public void execute(EffectContext context) {
        // ❌ 不使用前置库的默认伤害
        // executeDefaultDamage(context, 10.0);  // 不调用
        
        // 自定义魔法伤害系统
        double magicPower = getMagicPower(context.entity);
        double resistance = getMagicResistance(context.target);
        double finalDamage = magicPower * (1.0 - resistance);
        
        // 自定义伤害类型
        DamageSource source = new MagicDamageSource(context.entity);
        context.target.hurt(source, (float)finalDamage);
        
        // 自定义额外效果
        applyManaBurn(context.target, finalDamage * 0.5);
    }
    
    private double getMagicPower(LivingEntity entity) {
        // 自定义魔法值计算逻辑
        return 20.0;
    }
    
    private double getMagicResistance(Entity target) {
        // 自定义魔法抗性计算逻辑
        return 0.3;
    }
    
    private void applyManaBurn(Entity target, double manaDamage) {
        // 自定义法力燃烧逻辑
    }
}
```

**优势**：
- 完全控制伤害计算
- 可以使用自定义伤害类型
- 不受默认实现限制

---

### 场景3：混合使用（部分默认 + 部分自定义）

```java
public class LightningStrikeEffect extends AbstractEffect {
    @Override
    public void execute(EffectContext context) {
        // ✅ 使用默认伤害计算
        executeDefaultDamage(context, 15.0);
        
        // ❌ 自定义额外效果
        summonLightning(context.target.position());
        applyShockDebuff(context.target, Duration.ofSeconds(3));
        
        // ✅ 使用默认属性修改
        executeDefaultAttributeModification(
            context,
            Attributes.MOVEMENT_SPEED,
            -0.2,  // 减速20%
            AttributeModifier.Operation.MULTIPLY_TOTAL,
            60     // 持续3秒
        );
    }
    
    private void summonLightning(Vec3 position) {
        // 自定义闪电召唤逻辑
    }
    
    private void applyShockDebuff(Entity target, Duration duration) {
        // 自定义感电减益逻辑
    }
}
```

**优势**：
- 伤害计算使用默认实现（节省开发时间）
- 特殊效果自定义（保持灵活性）
- 属性修改使用默认实现（代码简洁）

---

## 🎯 职责分工总结

| 组件 | 前置库职责 | 开发者职责 | 是否强制使用 |
|------|-----------|---------------|-------------|
| `DefaultDamageCalculator` | 提供默认伤害计算 | 可选调用 | ❌ 不强制 |
| `DamageResult` | 提供数据模型 | 可选使用 | ❌ 不强制 |
| `DamageType` | 提供伤害类型枚举 | 可选使用 | ❌ 不强制 |
| `AttributeModificationHelper` | 提供属性修改工具 | 可选调用 | ❌ 不强制 |
| `executeDefaultDamage()` | 提供快捷方法 | 可选调用 | ❌ 不强制 |
| `execute()` | 定义抽象方法 | **必须实现** | ✅ 必须 |
| 自定义伤害逻辑 | - | 完全自定义 | 由开发者决定 |

---

## 📊 工具包使用决策树

```
开发者创建效果
    ↓
需要伤害计算吗？
    ├─ 是 → 使用 DefaultDamageCalculator？
    │         ├─ 是 → 调用 executeDefaultDamage()
    │         └─ 否 → 自定义伤害计算逻辑
    │
    └─ 否 → 需要属性修改吗？
              ├─ 是 → 使用 AttributeModificationHelper？
              │         ├─ 是 → 调用 executeDefaultAttributeModification()
              │         └─ 否 → 自定义属性修改逻辑
              │
              └─ 否 → 完全自定义效果逻辑
```

---

## 🚀 下一步计划

1. ✅ 创建本文档
2. ⏳ 创建 `tool/damage/DefaultDamageCalculator.java`
3. ⏳ 创建 `tool/damage/DamageResult.java`
4. ⏳ 创建 `tool/damage/DamageType.java`
5. ⏳ 创建 `tool/attribute/AttributeModificationHelper.java`
6. ⏳ 更新 `AbstractEffect.java` 添加快捷方法
7. ⏳ 创建使用示例和文档

---

**文档版本**: v1.0  
**创建日期**: 2026-05-11  
**作者**: YizMod QZK 架构团队
