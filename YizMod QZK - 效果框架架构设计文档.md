# YizMod QZK - 效果框架架构设计文档

> 本文档定义了效果系统的完整文件架构和类职责分工。
>
> 📌 **相关文档**：
> - [可选工具包设计文档](OPTIONAL_TOOLS.md) - 伤害计算、属性修改等可选工具的详细设计

---

## 📁 目录结构

```
src/main/java/net/minecraft/client/yiz/
│
├── effect/                                    # 效果系统核心包
│   ├── AbstractEffect.java                    # 效果基类（六大维度统一抽象）
│   │
│   ├── parent/                                # 五大父类（分类标签）
│   │   └── ParentType.java                   # 父类枚举：残响/铭刻/显化/本形/升灵
│   │
│   ├── perception/                            # 被感知方式系统
│   │   ├── PerceptionMode.java               # 感知方式接口（基础抽象）
│   │   ├── ItemPerception.java               # 物品绑定感知 → 词缀(Affix)
│   │   ├── EntityPerception.java             # 实体绑定感知 → 天赋(Talent)
│   │   ├── ContainerPerception.java          # 容器绑定感知 → 随影(Shadow)
│   │   └── CustomPerception.java             # 自定义感知接口（开发者扩展）
│   │
│   ├── unlock/                                # 解锁方式系统
│   │   ├── UnlockManager.java                # 解锁打勾管理器（记录实体解锁状态）
│   │   └── UnlockEvent.java                  # 解锁事件（用于 EventBus 通知）
│   │
│   ├── activation/                            # 生效条件系统
│   │   ├── ActivationCondition.java          # 生效条件接口（基础抽象）
│   │   ├── EntityAttackCondition.java        # 实体攻击时生效
│   │   ├── ProjectileHitCondition.java       # 飞行物命中时生效
│   │   ├── PassiveCondition.java             # 无条件生效（常驻）
│   │   └── CustomActivationCondition.java    # 自定义生效接口（开发者扩展）
│   │
│   └── rarity/                                # 稀有度系统
│       └── Rarity.java                       # 稀有度枚举：神话/传说/史诗/精良/平凡
│
├── attribute/                                 # 属性计算系统
│   ├── EffectStats.java                      # 效果基础属性模型
│   ├── AttributeModifier.java                # 属性修正器
│   └── ModifierStack.java                    # 多乘区计算引擎
│
└── core/                                      # 核心工具包
    ├── registry/
    │   └── ModRegistries.java                # 统一注册表（效果）
    └── event/
        ├── EffectEvent.java                  # 效果事件基类（待创建）
        ├── PreAttackEvent.java               # 攻击前事件（待创建）
        ├── HitEvent.java                     # 命中事件（待创建）
        └── PostAttackEvent.java              # 攻击后事件（待创建）
│
└── ui/                                        # 用户界面系统
    ├── ItemInfoUI.java                        # 物品信息UI主类（自定义绘制）
    ├── UIConfig.java                          # UI配置（快捷键、显示设置）
    ├── ItemAttributeDisplay.java              # 属性显示管理器
    ├── EffectTooltipRenderer.java             # 效果文本渲染器（词缀/随影）
    ├── ItemNamePrefixHandler.java             # 物品名称前缀处理器
    │
    ├── PlayerTalentUI.java                    # 玩家实体天赋UI主类
    ├── TalentGridRenderer.java                # 天赋网格渲染器
    └── InventoryDetector.java                 # 背包界面检测器
│
└── tool/                                      # 可选工具包（详见 OPTIONAL_TOOLS.md）
    ├── damage/                                # 伤害计算工具
    │   ├── DefaultDamageCalculator.java       # 默认伤害计算器
    │   ├── DamageResult.java                  # 伤害结果数据模型
    │   └── DamageType.java                    # 伤害类型枚举
    │
    ├── attribute/                             # 属性修改工具
    │   └── AttributeModificationHelper.java   # 属性修改辅助工具
    │
    └── helper/                                # 通用辅助工具
        ├── EffectContextHelper.java           # 上下文辅助工具
        └── ParticleEffectHelper.java          # 粒子效果辅助工具
```

---

## 🏗️ 核心类详细设计

### 1. AbstractEffect.java（效果基类）

**职责**：统一所有特殊效果的六大维度，是词缀/天赋/随影的共同父类。

**核心属性**：
```java
- ResourceLocation id                        // 效果唯一标识
- String translationKey                      // 本地化键
- String displayName                         // 显示名称（词缀/天赋/随影/自定义名称）
- ParentType parentType                      // 所属父类（残响/铭刻/显化/本形/升灵）
- int level                                  // 等级（1-∞，上限由开发者决定）
- Set<PerceptionMode> perceptionModes        // 被感知方式集合（可多选）
- ActivationCondition activationCondition    // 生效条件
- Rarity rarity                              // 稀有度（必填）
- String customPerceptionName                // 自定义感知名称（开发者自定义）
```

**核心方法**：
```java
// 生命周期钩子
+ onInitialize()                             // 效果初始化
+ onScan(LivingEntity entity)                // 效果被扫描到时（检查感知方式）
+ onActivate(LivingEntity entity)            // 效果激活时
+ onDeactivate(LivingEntity entity)          // 效果失效时

// 解锁系统
+ isUnlocked(LivingEntity entity)            // 检查是否已解锁
+ unlock(LivingEntity entity)                // 调用打勾接口解锁
+ lock(LivingEntity entity)                  // 取消解锁（锁定）

// 感知系统
+ checkPerception(LivingEntity entity)       // 检查当前是否满足感知条件
+ getPerceptionTypeName()                    // 获取感知类型名称（词缀/天赋/随影）

// 生效系统
+ shouldActivate(LivingEntity entity, Event event)  // 判断是否应该生效
+ executeEffect(LivingEntity entity, Event event)   // 执行效果逻辑

// 属性系统
+ getModifiers()                             // 获取属性修正器列表
```

**关键设计**：
- ✅ `perceptionModes` 是 **Set 集合**，支持一个效果拥有多种感知方式
- ✅ 感知方式满足**任一**即可生效（OR 逻辑）
- ✅ 感知方式决定显示名称：物品绑定=词缀，实体绑定=天赋，容器绑定=随影

---

### 2. ParentType.java（五大父类枚举）

**职责**：定义效果的五大分类标签，仅作推荐用途。

```java
public enum ParentType {
    ECHO("残响", "effect.parent.echo", "推荐用于攻击类效果"),
    INSCRIPTION("铭刻", "effect.parent.inscription", "推荐用于回复类效果"),
    MANIFESTATION("显化", "effect.parent.manifestation", "推荐用于机制类效果"),
    ORIGIN("本形", "effect.parent.origin", "推荐用于防御类效果"),
    ASCENSION("升灵", "effect.parent.ascension", "推荐用于被动类效果");
    
    private final String chineseName;
    private final String translationKey;
    private final String recommendedUse;
}
```

---

### 3. 感知方式系统

#### PerceptionMode.java（感知方式接口）

```java
public interface PerceptionMode {
    // 检查当前实体是否满足此感知条件
    boolean check(LivingEntity entity);
    
    // 获取感知类型名称（词缀/天赋/随影/自定义）
    String getTypeName();
    
    // 获取感知方式标识
    PerceptionType getPerceptionType();
}

public enum PerceptionType {
    ITEM,        // 物品绑定
    ENTITY,      // 实体绑定
    CONTAINER,   // 容器绑定
    CUSTOM       // 自定义
}
```

#### ItemPerception.java（词缀 - 物品绑定感知）

```java
public class ItemPerception implements PerceptionMode {
    private final ItemSlot slot;  // 装备位置
    
    public enum ItemSlot {
        MAIN_HAND,        // 主手持有
        OFF_HAND,         // 副手持有
        EQUIPMENT_SLOT,   // 装备槽（盔甲栏）
        INVENTORY         // 背包内
    }
    
    @Override
    public boolean check(LivingEntity entity);
    
    @Override
    public String getTypeName() {
        return "词缀 (Affix)";
    }
}
```

#### EntityPerception.java（天赋 - 实体绑定感知）

```java
public class EntityPerception implements PerceptionMode {
    // 天赋直接绑定到实体，无需额外条件
    // 只需检查实体是否已解锁该天赋
    
    @Override
    public boolean check(LivingEntity entity);
    
    @Override
    public String getTypeName() {
        return "天赋 (Talent)";
    }
}
```

#### ContainerPerception.java（随影 - 容器绑定感知）

```java
public class ContainerPerception implements PerceptionMode {
    private final ContainerType containerType;
    private final BlockPos containerPosition;  // 可选：特定位置
    
    public enum ContainerType {
        SPECIFIC_CONTAINER,   // 特定位置的容器
        PERSONAL_CONTAINER    // 玩家专属容器（末影箱）
    }
    
    @Override
    public boolean check(LivingEntity entity);
    
    @Override
    public String getTypeName() {
        return "随影 (Shadow)";
    }
}
```

#### CustomPerception.java（自定义感知接口）

```java
public interface CustomPerception extends PerceptionMode {
    // 由第三方模组实现自定义感知逻辑
    // 例如：站在祭坛上、特定时间/天气、特定种族等
    
    @Override
    String getTypeName();  // 返回开发者自定义的名称
}
```

---

### 4. UnlockManager.java（解锁打勾管理器）

**职责**：记录实体的效果解锁状态，提供打勾接口。

```java
public class UnlockManager {
    // 数据存储：实体 UUID → 已解锁的效果 ID 集合
    private static final Map<UUID, Set<ResourceLocation>> unlockedEffects = new HashMap<>();
    
    // 解锁效果（打勾）
    public static void unlock(LivingEntity entity, ResourceLocation effectId);
    
    // 检查是否已解锁
    public static boolean isUnlocked(LivingEntity entity, ResourceLocation effectId);
    
    // 取消解锁（锁定）
    public static void lock(LivingEntity entity, ResourceLocation effectId);
    
    // 获取实体所有已解锁的效果
    public static Set<ResourceLocation> getUnlockedEffects(LivingEntity entity);
    
    // 数据持久化（NBT 保存/加载）
    public static void saveToNBT(CompoundTag nbt);
    public static void loadFromNBT(CompoundTag nbt);
}
```

**关键设计**：
- ✅ 前置库**只提供接口**，不写具体解锁条件
- ✅ 开发者自行编写条件判断逻辑，满足后调用 `unlock(entity, effectId)`
- ✅ 数据通过 NBT 持久化，支持存档保存/加载

---

### 5. ActivationCondition.java（生效条件接口）

```java
public interface ActivationCondition {
    // 判断是否满足生效条件
    boolean shouldActivate(LivingEntity entity, Event triggerEvent);
    
    // 获取生效条件类型名称
    String getConditionName();
}
```

#### EntityAttackCondition.java

```java
public class EntityAttackCondition implements ActivationCondition {
    // 实体直接攻击（近战）时生效
    
    @Override
    public boolean shouldActivate(LivingEntity entity, Event event);
    
    @Override
    public String getConditionName() {
        return "实体攻击时生效";
    }
}
```

#### ProjectileHitCondition.java

```java
public class ProjectileHitCondition implements ActivationCondition {
    // 实体发射的飞行物命中时生效
    
    @Override
    public boolean shouldActivate(LivingEntity entity, Event event);
    
    @Override
    public String getConditionName() {
        return "飞行物命中时生效";
    }
}
```

#### PassiveCondition.java

```java
public class PassiveCondition implements ActivationCondition {
    // 无条件生效（常驻增益）
    
    @Override
    public boolean shouldActivate(LivingEntity entity, Event event) {
        return true;  // 始终返回 true
    }
    
    @Override
    public String getConditionName() {
        return "常驻生效";
    }
}
```

#### CustomActivationCondition.java（自定义生效接口）

```java
public interface CustomActivationCondition extends ActivationCondition {
    // 由第三方模组实现自定义生效条件
    // 例如：同时持有特定物品、血量低于 30%、已解锁特定前缀等
}
```

---

### 6. Rarity.java（稀有度枚举）

```java
public enum Rarity {
    MYTHIC("神话", "effect.rarity.mythic", 0xFF5555),      // 红色
    LEGENDARY("传说", "effect.rarity.legendary", 0xFFAA00), // 橙色
    EPIC("史诗", "effect.rarity.epic", 0xAA00AA),           // 紫色
    RARE("精良", "effect.rarity.rare", 0x5555FF),           // 蓝色
    COMMON("平凡", "effect.rarity.common", 0xFFFFFF);       // 白色
    
    private final String chineseName;
    private final String translationKey;
    private final int displayColor;  // 显示颜色（用于 UI 渲染）
    
    // 仅提供定义，具体用途由开发者决定
}
```

---

## 🔄 效果生命周期流程

```
1. 效果注册
   ↓
   AbstractEffect 创建 → 自动注册到 ModRegistries
   
2. 效果扫描
   ↓
   checkPerception(entity) → 遍历 perceptionModes → 满足任一即进入下一步
   
3. 解锁检查
   ↓
   isUnlocked(entity) → 未解锁则跳过，已解锁则继续
   
4. 生效条件判断
   ↓
   shouldActivate(entity, event) → 不满足则跳过，满足则执行
   
5. 效果执行
   ↓
   executeEffect(entity, event) → 应用属性修正/触发特殊逻辑
```

---

## 💡 核心设计原则

| 维度 | 前置库职责 | 开发者职责 |
|------|-----------|-----------|
| **父类** | 提供 5 个推荐标签枚举 | 自由选择，可自定义含义 |
| **等级** | 提供 `int level` 字段 | 决定等级上限和升级逻辑 |
| **感知** | 提供 3 种内置 + 1 个扩展接口 | 组合使用/实现自定义感知 |
| **解锁** | 提供 `unlock(entity)` 打勾接口 | 编写解锁条件，调用接口 |
| **生效** | 提供 3 种内置 + 扩展接口 | 实现自定义生效条件 |
| **稀有度** | 提供 5 级枚举定义 | 决定稀有度用途（掉落率/颜色等） |

**核心原则**：
- ✅ 前置库提供**接口和框架**，不做强制限制
- ✅ 感知方式支持**多选**（OR 逻辑）
- ✅ 所有效果统一使用 `AbstractEffect` 基类
- ✅ 感知方式决定**显示名称**（词缀/天赋/随影）
- ✅ 稀有度与等级**完全独立**

---

## 🔄 数据流与自动注册机制

### 核心设计原则

**开发者创建自定义效果 → 游戏启动自动加载 → UI自动显示**

整个流程完全自动化，开发者只需编写效果定义（代码或JSON），无需手动注册到UI。

---

### 1. 完整数据流转图

```
开发者创建效果
    ↓
[方式A: 代码创建]           [方式B: JSON配置]
    ↓                            ↓
new MyCustomEffect()      DataLoader.loadJSON()
    ↓                            ↓
    └──────→ ModRegistries ←─────┘
              .registerEffect()
                    ↓
          效果注册表（内存）
                    ↓
    ┌───────────────┼───────────────┐
    ↓               ↓               ↓
物品UI扫描      玩家UI扫描      效果生效检测
    ↓               ↓               ↓
显示词缀/随影   显示天赋列表    触发天赋效果
```

---

### 2. 自动注册机制

#### 2.1 方式A：代码创建（开发者继承AbstractEffect）

**开发者在其模组中创建**：
```java
// 开发者模组：MyAddonMod
public class FlameTalent extends AbstractEffect {
    public FlameTalent() {
        super(
            ResourceLocation.parse("myaddonmod:flame_talent"),
            "flame_talent",
            "火焰天赋",
            ParentType.ECHO,           // 残响 · 攻击类
            10,                        // 等级 10
            Set.of(new EntityPerception()),  // 实体绑定（天赋）
            new EntityAttackCondition(),     // 实体攻击时生效
            Rarity.MYTHIC              // 神话稀有度
        );
    }
    
    @Override
    public void executeEffect(LivingEntity attacker, Entity target) {
        // 自定义效果逻辑
        target.setSecondsOnFire(5);
    }
}

// 在模组初始化时创建（自动注册到ModRegistries）
@Mod(MyAddonMod.MODID)
public class MyAddonMod {
    // 静态初始化时自动注册
    public static final FlameTalent FLAME_TALENT = new FlameTalent();
}
```

**自动注册流程**：
```java
// AbstractEffect 构造函数
protected AbstractEffect(...) {
    // ... 初始化属性
    
    // 自动注册到全局注册表
    ModRegistries.registerEffect(this);
}
```

#### 2.2 方式B：JSON配置（数据驱动）

**开发者创建JSON文件**：
```json
// data/myaddonmod/yizmodqzk/effects/ice_affix.json
{
  "id": "myaddonmod:ice_affix",
  "display_name": "冰霜词缀",
  "parent_type": "MANIFESTATION",
  "level": 8,
  "perception_modes": [
    {
      "type": "ITEM",
      "slot": "MAIN_HAND"
    }
  ],
  "activation_condition": {
    "type": "ENTITY_ATTACK"
  },
  "rarity": "LEGENDARY",
  "modifiers": [
    {
      "attribute": "attack_damage",
      "type": "MULTIPLICATIVE",
      "value": 0.15
    }
  ]
}
```

**JSON加载器自动处理**：
```java
public class EffectDataLoader {
    @SubscribeEvent
    public void onDataReload(AddReloadListenerEvent event) {
        event.addListener(new EffectReloadListener());
    }
    
    public static class EffectReloadListener implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(...) {
            // 1. 扫描所有 modid/yizmodqzk/effects/ 目录
            // 2. 解析 JSON 文件
            // 3. 创建 AbstractEffect 实例
            // 4. 自动注册到 ModRegistries
            for (JsonElement json : loadAllJSON()) {
                AbstractEffect effect = parseEffect(json);
                ModRegistries.registerEffect(effect);  // 自动注册
            }
        }
    }
}
```

---

### 3. UI自动发现机制

#### 3.1 物品UI自动扫描词缀/随影

```java
public class EffectTooltipRenderer {
    // 自动获取物品上的所有效果
    public List<AbstractEffect> getItemEffects(ItemStack stack) {
        List<AbstractEffect> effects = new ArrayList<>();
        
        // 遍历注册表中的所有效果
        for (AbstractEffect effect : ModRegistries.getAllEffects().values()) {
            // 检查该效果是否与物品关联
            if (isEffectAttachedToItem(effect, stack)) {
                effects.add(effect);
            }
        }
        
        return effects;  // 自动包含所有开发者创建的效果
    }
    
    private boolean isEffectAttachedToItem(AbstractEffect effect, ItemStack stack) {
        // 检查物品的 NBT 或附加数据
        // 或者检查物品的材质/类型是否匹配效果的感知方式
        return effect.getPerceptionModes().stream()
            .filter(mode -> mode instanceof ItemPerception)
            .anyMatch(mode -> checkItemMatch(mode, stack));
    }
}
```

**关键点**：
- ✅ UI 从 `ModRegistries` 获取效果列表
- ✅ 开发者注册的新效果**自动出现**在列表中
- ✅ **无需修改UI代码**

#### 3.2 玩家UI自动扫描天赋

```java
public class PlayerTalentUI {
    // 获取玩家所有已解锁的天赋
    public List<AbstractEffect> getPlayerTalents(LivingEntity player) {
        List<AbstractEffect> talents = new ArrayList<>();
        
        // 遍历所有已注册的效果
        for (AbstractEffect effect : ModRegistries.getAllEffects().values()) {
            // 筛选出实体绑定感知（天赋）
            boolean isEntityTalent = effect.getPerceptionModes().stream()
                .anyMatch(mode -> mode instanceof EntityPerception);
            
            if (!isEntityTalent) continue;
            
            // 检查是否已解锁
            if (UnlockManager.isUnlocked(player, effect.getId())) {
                talents.add(effect);  // 自动包含所有开发者创建的天赋
            }
        }
        
        // 排序（稀有度 → 等级）
        talents.sort(this::compareEffects);
        
        return talents;
    }
}
```

**关键点**：
- ✅ UI 从 `ModRegistries` 获取所有效果
- ✅ 自动过滤出 `EntityPerception` 类型（天赋）
- ✅ 自动检查解锁状态
- ✅ 开发者创建的新天赋**自动显示**

---

### 4. 开发者完整工作流示例

#### 场景：开发者创建新的神话天赋

**步骤 1：创建效果类**
```java
public class ThunderTalent extends AbstractEffect {
    public ThunderTalent() {
        super(
            ResourceLocation.parse("weathermod:thunder_talent"),
            "thunder_talent",
            "雷霆天赋",
            ParentType.ECHO,
            10,
            Set.of(new EntityPerception()),
            new EntityAttackCondition(),
            Rarity.MYTHIC
        );
    }
    
    @Override
    public void executeEffect(LivingEntity attacker, Entity target) {
        // 召唤闪电
        Level level = attacker.level();
        level.addFreshEntity(new LightningBolt(level, target.position()));
    }
}
```

**步骤 2：在模组初始化时创建**
```java
@Mod(WeatherMod.MODID)
public class WeatherMod {
    // 静态初始化 → 自动调用构造函数 → 自动注册到 ModRegistries
    public static final ThunderTalent THUNDER_TALENT = new ThunderTalent();
}
```

**步骤 3：游戏启动，自动完成以下流程**
```
1. WeatherMod 加载
   ↓
2. 静态字段初始化：new ThunderTalent()
   ↓
3. AbstractEffect 构造函数调用
   ↓
4. ModRegistries.registerEffect(this)  ← 自动注册
   ↓
5. 玩家UI从 ModRegistries 获取效果列表
   ↓
6. 如果玩家已解锁 "weathermod:thunder_talent"
   ↓
7. 天赋自动显示在玩家实体信息UI中  ← 无需额外代码
```

**步骤 4：玩家看到的效果**
```
┌────────────────────────┐
│ 已解锁天赋              │
├────────────────────────┤
│ 🔴 神话天赋·雷霆 Lv.10 │ ← 自动出现！
│    残响 · 攻击类        │
│    生效：实体攻击时     │
│    效果：召唤闪电       │
└────────────────────────┘
```

---

### 5. 关键连接点总结

| 环节 | 前置库职责 | 开发者职责 | 自动完成 |
|------|-----------|-----------|---------|
| **效果创建** | 提供 AbstractEffect 基类 | 继承并实现效果逻辑 | - |
| **效果注册** | 提供 ModRegistries | 在初始化时创建实例 | ✅ 构造函数自动注册 |
| **JSON加载** | 提供 EffectDataLoader | 编写JSON文件 | ✅ 游戏启动自动加载 |
| **物品UI** | 从 ModRegistries 获取效果 | - | ✅ 自动扫描并显示 |
| **玩家UI** | 从 ModRegistries 获取天赋 | - | ✅ 自动扫描并显示 |
| **效果触发** | 提供事件系统 | 实现 executeEffect() | ✅ 事件自动触发 |

**核心优势**：
- ✅ 开发者只需关注**效果逻辑**
- ✅ **无需手动注册**到UI
- ✅ **无需修改**前置库代码
- ✅ **完全解耦**，支持热插拔

---

## 🔗 跨模组接口与数据流规范

> 本节定义前置库与第三方模组之间的数据交互、自动注册和UI同步机制，确保所有依赖本前置库的模组能够无缝配合。

---

### 1. 核心设计原则

**前置库职责边界**：
- ✅ 提供效果基类和注册表
- ✅ 提供数据加载器（JSON → Effect）
- ✅ 提供UI自动扫描机制
- ✅ 提供NBT存储标准
- ❌ 不编写任何具体效果逻辑
- ❌ 不强制解锁条件
- ❌ 不限制稀有度用途

**第三方模组职责**：
- ✅ 创建具体效果（代码或JSON）
- ✅ 编写解锁条件逻辑
- ✅ 定义稀有度的实际用途
- ✅ 处理效果间的依赖关系

---

### 2. 数据加载完整流程

#### 2.1 加载时机

```
游戏启动流程：
1. Forge加载所有模组
   ↓
2. 模组静态字段初始化（代码创建的效果自动注册）
   ↓
3. 数据包加载（JSON效果加载并注册）
   ↓
4. 资源包加载（覆盖/合并效果数据）
   ↓
5. UI初始化（从注册表扫描所有效果）
   ↓
6. 玩家加入世界（加载NBT数据）
```

#### 2.2 目录结构标准

**第三方模组必须遵循的目录规范**：

```
模组资源结构：
src/main/resources/
├── data/
│   └── {modid}/
│       └── yizmodqzk/
│           └── effects/              ← 固定目录名
│               ├── affix_fire.json   ← 词缀
│               ├── talent_flame.json ← 天赋
│               └── shadow_ancient.json ← 随影
│
└── assets/
    └── {modid}/
        └── lang/
            └── en_us.json            ← 效果本地化
```

**关键规范**：
- ✅ 目录名必须是 `yizmodqzk/effects/`
- ✅ 文件名格式：`{type}_{name}.json`
- ✅ type 可选值：`affix`、`talent`、`shadow`、`custom`

#### 2.3 JSON Schema 完整定义

```json
{
  "$schema": "yizmodqzk://effect_schema_v1.json",
  
  // 基础信息（必填）
  "id": "mymod:fire_affix",
  "display_name": "火焰词缀",
  "translation_key": "effect.mymod.fire_affix",
  
  // 六大维度（必填）
  "parent_type": "ECHO",
  "level": 10,
  "rarity": "MYTHIC",
  
  // 感知方式（必填，至少一个）
  "perception_modes": [
    {
      "type": "ITEM",
      "slot": "MAIN_HAND"
    }
  ],
  
  // 生效条件（必填）
  "activation_condition": {
    "type": "ENTITY_ATTACK"
  },
  
  // 属性修正器（可选）
  "modifiers": [
    {
      "attribute": "attack_damage",
      "type": "MULTIPLICATIVE",
      "value": 0.25
    }
  ],
  
  // 依赖关系（可选）
  "requires": ["mymod:base_talent"],
  "conflicts": ["othermod:ice_affix"]
}
```

#### 2.4 数据加载器实现

```java
public class EffectDataLoader implements PreparableReloadListener {
    
    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, 
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, 
                                          Executor gameExecutor) {
        
        // 阶段1：收集所有JSON文件
        CompletableFuture<Map<ResourceLocation, JsonElement>> loadFuture = 
            CompletableFuture.supplyAsync(() -> {
                Map<ResourceLocation, JsonElement> effectData = new HashMap<>();
                
                // 扫描所有 modid/yizmodqzk/effects/ 目录
                for (String namespace : resourceManager.getNamespaces()) {
                    try {
                        Collection<Resource> resources = resourceManager
                            .getResourceStack(ResourceLocation.parse(
                                namespace + ":yizmodqzk/effects/"
                            ));
                        
                        for (Resource resource : resources) {
                            ResourceLocation id = parseResourceLocation(resource);
                            JsonElement json = parseJson(resource);
                            effectData.put(id, json);
                        }
                    } catch (IOException e) {
                        YizModQZK.LOGGER.error("Failed to load effects from " + namespace, e);
                    }
                }
                
                return effectData;
            }, backgroundExecutor);
        
        // 阶段2：解析并注册效果
        return loadFuture.thenAcceptAsync(effectData -> {
            for (Map.Entry<ResourceLocation, JsonElement> entry : effectData.entrySet()) {
                try {
                    // 解析JSON为Effect实例
                    AbstractEffect effect = EffectParser.parse(entry.getKey(), entry.getValue());
                    
                    // 验证数据完整性
                    EffectValidator.validate(effect);
                    
                    // 注册到全局注册表
                    ModRegistries.registerEffect(effect);
                    
                    YizModQZK.LOGGER.info("Loaded effect: {}", effect.getId());
                } catch (Exception e) {
                    YizModQZK.LOGGER.error("Failed to parse effect: " + entry.getKey(), e);
                }
            }
        }, gameExecutor);
    }
}
```

---

### 3. 自动注册机制

#### 3.1 代码创建效果（自动注册）

**第三方模组创建效果**：
```java
// MyAddonMod.java
@Mod(MyAddonMod.MODID)
public class MyAddonMod {
    public static final String MODID = "myaddonmod";
    
    // 静态初始化 → 自动调用构造函数 → 自动注册
    public static final AbstractEffect FLAME_TALENT = new AbstractEffect(
        ResourceLocation.parse(MODID + ":flame_talent"),
        "flame_talent",
        "火焰天赋",
        ParentType.ECHO,
        10,
        Set.of(new EntityPerception()),
        new EntityAttackCondition(),
        Rarity.MYTHIC
    ) {
        @Override
        public void executeEffect(LivingEntity attacker, Entity target) {
            target.setSecondsOnFire(5);
        }
    };
}
```

**AbstractEffect 构造函数**：
```java
protected AbstractEffect(...) {
    // ... 初始化属性
    
    // 自动注册到全局注册表
    ModRegistries.registerEffect(this);
}
```

#### 3.2 防止重复注册

```java
public class ModRegistries {
    private static final Map<ResourceLocation, AbstractEffect> EFFECT_REGISTRY = new HashMap<>();
    
    public static void registerEffect(AbstractEffect effect) {
        ResourceLocation id = effect.getId();
        
        // 检查是否已注册
        if (EFFECT_REGISTRY.containsKey(id)) {
            // 策略：跳过并警告
            YizModQZK.LOGGER.warn("Effect already registered: {}, skipping", id);
            return;
        }
        
        EFFECT_REGISTRY.put(id, effect);
        YizModQZK.LOGGER.debug("Registered effect: {}", id);
    }
}
```

---

### 4. UI自动发现机制

#### 4.1 物品UI自动扫描词缀/随影

```java
public class EffectTooltipRenderer {
    
    /**
     * 获取物品的所有效果（自动从注册表扫描）
     */
    public List<AbstractEffect> getItemEffects(ItemStack stack) {
        List<AbstractEffect> effects = new ArrayList<>();
        
        // 遍历注册表中的所有效果
        for (AbstractEffect effect : ModRegistries.getAllEffects().values()) {
            // 检查该效果是否与物品关联
            if (isEffectAttachedToItem(effect, stack)) {
                effects.add(effect);  // 自动包含所有模组创建的效果
            }
        }
        
        return effects;
    }
    
    private boolean isEffectAttachedToItem(AbstractEffect effect, ItemStack stack) {
        // 检查物品的 NBT 或附加数据
        return effect.getPerceptionModes().stream()
            .filter(mode -> mode instanceof ItemPerception)
            .anyMatch(mode -> checkItemMatch(mode, stack));
    }
}
```

**关键点**：
- ✅ UI 从 `ModRegistries` 获取效果列表
- ✅ 开发者注册的新效果**自动出现**在列表中
- ✅ **无需修改UI代码**

#### 4.2 玩家UI自动扫描天赋

```java
public class PlayerTalentUI {
    
    /**
     * 获取玩家所有已解锁的天赋（自动从注册表扫描）
     */
    public List<AbstractEffect> getPlayerTalents(LivingEntity player) {
        List<AbstractEffect> talents = new ArrayList<>();
        
        // 遍历所有已注册的效果
        for (AbstractEffect effect : ModRegistries.getAllEffects().values()) {
            // 筛选出实体绑定感知（天赋）
            boolean isEntityTalent = effect.getPerceptionModes().stream()
                .anyMatch(mode -> mode instanceof EntityPerception);
            
            if (!isEntityTalent) continue;
            
            // 检查是否已解锁
            if (UnlockManager.isUnlocked(player, effect.getId())) {
                talents.add(effect);  // 自动包含所有模组创建的天赋
            }
        }
        
        // 排序（稀有度 → 等级）
        talents.sort(this::compareEffects);
        
        return talents;
    }
}
```

**关键点**：
- ✅ UI 从 `ModRegistries` 获取所有效果
- ✅ 自动过滤出 `EntityPerception` 类型（天赋）
- ✅ 自动检查解锁状态
- ✅ 开发者创建的新天赋**自动显示**

---

### 5. NBT数据结构标准

#### 5.1 物品NBT结构

```java
public class EffectNBTHandler {
    
    /**
     * 为物品添加效果
     */
    public static void addEffectToItem(ItemStack stack, AbstractEffect effect) {
        CompoundTag nbt = stack.getOrCreateTag();
        
        // 效果列表
        ListTag effectsList = nbt.getList("yizmodqzk:effects", Tag.TAG_COMPOUND);
        
        CompoundTag effectTag = new CompoundTag();
        effectTag.putString("id", effect.getId().toString());
        effectTag.putInt("level", effect.getLevel());
        effectTag.putBoolean("unlocked", true);
        
        effectsList.add(effectTag);
        nbt.put("yizmodqzk:effects", effectsList);
    }
    
    /**
     * 获取物品的所有效果
     */
    public static List<AbstractEffect> getItemEffects(ItemStack stack) {
        List<AbstractEffect> effects = new ArrayList<>();
        
        if (!stack.hasTag()) return effects;
        
        ListTag effectsList = stack.getTag().getList("yizmodqzk:effects", Tag.TAG_COMPOUND);
        
        for (int i = 0; i < effectsList.size(); i++) {
            CompoundTag effectTag = effectsList.getCompound(i);
            ResourceLocation effectId = ResourceLocation.parse(
                effectTag.getString("id")
            );
            
            // 从注册表获取效果实例
            ModRegistries.getEffect(effectId).ifPresent(effects::add);
        }
        
        return effects;
    }
}
```

**NBT结构示例**：
```
Item NBT:
{
    "yizmodqzk:effects": [
        {
            "id": "mymod:fire_affix",
            "level": 10,
            "unlocked": true
        },
        {
            "id": "mymod:ice_affix",
            "level": 8,
            "unlocked": true
        }
    ]
}
```

#### 5.2 实体NBT结构（天赋数据）

```java
public class EntityEffectNBTHandler {
    
    /**
     * 保存实体已解锁的天赋
     */
    public static void saveEntityTalents(LivingEntity entity, CompoundTag nbt) {
        CompoundTag talentsTag = new CompoundTag();
        
        // 已解锁的天赋列表
        ListTag unlockedList = new ListTag();
        Set<ResourceLocation> unlockedTalents = 
            UnlockManager.getUnlockedEffects(entity);
        
        for (ResourceLocation talentId : unlockedTalents) {
            unlockedList.add(StringTag.valueOf(talentId.toString()));
        }
        
        talentsTag.put("unlocked", unlockedList);
        nbt.put("yizmodqzk:talents", talentsTag);
    }
    
    /**
     * 加载实体天赋数据
     */
    public static void loadEntityTalents(LivingEntity entity, CompoundTag nbt) {
        if (!nbt.contains("yizmodqzk:talents")) return;
        
        CompoundTag talentsTag = nbt.getCompound("yizmodqzk:talents");
        ListTag unlockedList = talentsTag.getList("unlocked", Tag.TAG_STRING);
        
        for (int i = 0; i < unlockedList.size(); i++) {
            ResourceLocation talentId = ResourceLocation.parse(
                unlockedList.getString(i)
            );
            UnlockManager.unlock(entity, talentId);
        }
    }
}
```

---

### 6. 跨模组API接口

#### 6.1 标准API

```java
/**
 * YizMod QZK 公共API
 * 第三方模组通过此接口与前置库交互
 */
public class YizModQZKAPI {
    
    // ==================== 效果注册 ====================
    
    public static void registerEffect(AbstractEffect effect) {
        ModRegistries.registerEffect(effect);
    }
    
    public static Optional<AbstractEffect> getEffect(ResourceLocation id) {
        return ModRegistries.getEffect(id);
    }
    
    // ==================== 解锁管理 ====================
    
    public static void unlockEffect(LivingEntity entity, ResourceLocation effectId) {
        UnlockManager.unlock(entity, effectId);
    }
    
    public static boolean isEffectUnlocked(LivingEntity entity, ResourceLocation effectId) {
        return UnlockManager.isUnlocked(entity, effectId);
    }
    
    // ==================== 效果查询 ====================
    
    public static List<AbstractEffect> getItemEffects(ItemStack stack) {
        return EffectNBTHandler.getItemEffects(stack);
    }
    
    public static List<AbstractEffect> getEntityTalents(LivingEntity entity) {
        return PlayerTalentUI.getPlayerTalents(entity);
    }
    
    // ==================== UI接口 ====================
    
    public static void refreshUI() {
        UIRefreshEvent.trigger();
    }
}
```

#### 6.2 第三方模组接入示例

```java
// MyAddonMod.java
@Mod(MyAddonMod.MODID)
public class MyAddonMod {
    public static final String MODID = "myaddonmod";
    
    // 创建自定义效果
    public static final AbstractEffect CUSTOM_TALENT = new AbstractEffect(
        ResourceLocation.parse(MODID + ":custom_talent"),
        "custom_talent",
        "自定义天赋",
        ParentType.ASCENSION,
        5,
        Set.of(new EntityPerception()),
        new PassiveCondition(),
        Rarity.EPIC
    ) {
        @Override
        public void executeEffect(LivingEntity attacker, Entity target) {
            attacker.heal(1.0f);
        }
    };
    
    // 在合适的时机解锁
    public static void unlockForPlayer(ServerPlayer player) {
        YizModQZKAPI.unlockEffect(player, CUSTOM_TALENT.getId());
    }
}
```

---

### 7. 完整数据流转图

```
┌─────────────────────────────────────────────────────────┐
│                    开发者创建效果                          │
└──────────────────┬──────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ↓                     ↓
   [代码创建]            [JSON配置]
        │                     │
        ↓                     ↓
  new Effect()         DataLoader.loadJSON()
        │                     │
        └──────────┬──────────┘
                   ↓
        ┌─────────────────────┐
        │  EffectValidator    │  ← 验证数据完整性
        │  .validate()        │
        └────────┬────────────┘
                 ↓
        ┌─────────────────────┐
        │  ModRegistries      │  ← 注册到全局注册表
        │  .registerEffect()  │
        └────────┬────────────┘
                 ↓
    ┌────────────┼────────────────┐
    ↓            ↓                ↓
┌──────┐   ┌────────┐      ┌──────────┐
│物品UI│   │玩家UI  │      │效果触发  │
│扫描  │   │扫描    │      │系统      │
└──┬───┘   └───┬────┘      └────┬─────┘
   │           │                │
   ↓           ↓                ↓
显示词缀    显示天赋         执行效果
/随影       列表             逻辑
```

---

### 8. 开发者检查清单

第三方模组开发时，必须确保以下所有项：

#### ✅ 效果创建
- [ ] 继承 `AbstractEffect` 或实现等效接口
- [ ] 提供唯一的 `ResourceLocation id`
- [ ] 设置所有必填字段（六大维度）
- [ ] 至少指定一个感知方式
- [ ] 实现 `executeEffect()` 方法

#### ✅ 数据加载
- [ ] JSON文件放置在正确目录：`data/{modid}/yizmodqzk/effects/`
- [ ] JSON格式符合Schema规范
- [ ] 文件名格式正确：`{type}_{name}.json`
- [ ] 测试 `/reload` 命令是否正常工作

#### ✅ 跨模组兼容
- [ ] 在 `neoforge.mods.toml` 中声明依赖 `yizmodqzk`
- [ ] 设置 `ordering = "AFTER"` 确保加载顺序
- [ ] 处理效果依赖和冲突
- [ ] 测试与其他模组的兼容性

#### ✅ NBT数据
- [ ] 使用标准NBT键名：`yizmodqzk:effects`
- [ ] 正确处理效果的保存和加载
- [ ] 测试存档重载后数据是否完整

#### ✅ UI显示
- [ ] 效果自动出现在物品UI中（无需额外代码）
- [ ] 天赋自动出现在玩家UI中（解锁后）
- [ ] 测试稀有度颜色是否正确
- [ ] 测试排序是否符合规则（稀有度 → 等级）

---

## 📝 JSON 配置示例（数据驱动）

```json
{
  "id": "mymod:flame_affix",
  "display_name": "神话词缀·烈焰",
  "parent_type": "ECHO",
  "level": 10,
  "perception_modes": [
    {
      "type": "ITEM",
      "slot": "MAIN_HAND"
    },
    {
      "type": "ITEM",
      "slot": "OFF_HAND"
    },
    {
      "type": "CONTAINER",
      "container_type": "PERSONAL_CONTAINER"
    }
  ],
  "activation_condition": {
    "type": "ENTITY_ATTACK"
  },
  "rarity": "MYTHIC",
  "modifiers": [
    {
      "attribute": "attack_damage",
      "type": "MULTIPLICATIVE",
      "value": 0.25
    }
  ]
}
```

---

## 👤 玩家实体天赋信息 UI 系统

### 1. PlayerTalentUI.java（玩家天赋UI主类）

**职责**：在生存背包界面中显示玩家已解锁的全部天赋信息。

**快捷键控制**：
- 开启/关闭：`CTRL + SHIFT`
- 状态持久化：配置文件中记录开关状态

**界面检测条件**：
```java
public class InventoryDetector {
    /**
     * 检测当前打开的界面是否为生存背包
     * 避免在创造模式、容器界面等情况下显示
     */
    public static boolean isSurvivalInventory(Screen screen) {
        // 仅当屏幕是 InventoryScreen 时才返回 true
        return screen instanceof InventoryScreen;
    }
}
```

**核心功能**：
```java
public class PlayerTalentUI {
    // 检测是否满足显示条件
    + boolean shouldShow(Minecraft mc);
    
    // 绘制天赋UI
    + void renderTalentUI(GuiGraphics graphics, int mouseX, int mouseY);
    
    // 获取玩家所有已解锁的天赋
    + List<AbstractEffect> getPlayerTalents(LivingEntity player);
    
    // 计算UI布局
    + void calculateLayout(int screenWidth, int screenHeight);
}
```

**显示条件**：
```java
public boolean shouldShow(Minecraft mc) {
    // 条件1：UI已开启
    if (!UIConfig.isPlayerTalentUIEnabled()) return false;
    
    // 条件2：当前界面是生存背包
    if (!(mc.screen instanceof InventoryScreen)) return false;
    
    // 条件3：玩家实体存在
    if (mc.player == null) return false;
    
    // 条件4：玩家已解锁至少一个天赋
    List<AbstractEffect> talents = getPlayerTalents(mc.player);
    if (talents.isEmpty()) return false;
    
    return true;
}
```

---

### 2. 显示位置与布局

**显示位置**：
- 在生存背包界面的**右侧空白区域**显示
- 避免遮挡原版背包槽位

**布局示例**：
```
┌──────────────────────────────────────────────────┐
│  原版背包界面               │  天赋信息面板         │
│  [物品槽位]                 │  ┌────────────────┐ │
│  [物品槽位]                 │  │ 已解锁天赋列表   │ │
│  [物品槽位]                 │  │                │ │
│  ...                        │  │ 神话天赋·火焰   │ │
│                             │  │ (Lv.10) 红色    │ │
│                             │  │                │ │
│                             │  │ 传说天赋·治疗   │ │
│                             │  │ (Lv.8) 金色     │ │
│                             │  │                │ │
│                             │  │ 史诗天赋·闪现   │ │
│                             │  │ (Lv.5) 紫色     │ │
│                             │  └────────────────┘ │
└──────────────────────────────────────────────────┘
```

---

### 3. TalentGridRenderer.java（天赋网格渲染器）

**职责**：渲染天赋列表，使用原版物品面板纹理。

**纹理来源**：
- 使用原版 `textures/gui/container/inventory.png` 纹理
- 与原版背包界面视觉风格一致

**核心方法**：
```java
public class TalentGridRenderer {
    // 绘制背景面板（使用原版纹理）
    + void renderBackground(GuiGraphics graphics, int x, int y, int width, int height);
    
    // 绘制单个天赋卡片
    + void renderTalentCard(GuiGraphics graphics, AbstractEffect talent, int x, int y);
    
    // 绘制天赋图标
    + void renderTalentIcon(GuiGraphics graphics, AbstractEffect talent, int x, int y);
    
    // 绘制天赋文本信息
    + void renderTalentText(GuiGraphics graphics, AbstractEffect talent, int x, int y);
    
    // 绘制滚动条（天赋过多时）
    + void renderScrollbar(GuiGraphics graphics, int currentScroll, int maxScroll);
}
```

**纹理绘制示例**：
```java
public void renderBackground(GuiGraphics graphics, int x, int y, int width, int height) {
    Minecraft mc = Minecraft.getInstance();
    ResourceLocation texture = ResourceLocation.parse("textures/gui/container/inventory.png");
    
    // 使用原版背包纹理
    // 纹理区域：原版背包面板的坐标范围
    int textureU = 0;
    int textureV = 0;
    int textureWidth = 176;
    int textureHeight = 166;
    
    graphics.blit(texture, x, y, 0, textureU, textureV, width, height, 256, 256);
}
```

---

### 4. 天赋列表排序与显示

**排序规则**：与物品UI相同
```
优先级 1：稀有度降序（神话 > 传说 > 史诗 > 精良 > 平凡）
优先级 2：等级降序（同稀有度时，等级高的在上）
```

**显示格式**：
```
┌────────────────────────┐
│ 已解锁天赋              │
├────────────────────────┤
│ 🔴 神话天赋·火焰 Lv.10 │ ← 红色文本
│    残响 · 攻击类        │
│    生效：实体攻击时     │
│    效果：命中时点燃     │
├────────────────────────┤
│ 🟡 传说天赋·治疗 Lv.8  │ ← 金色文本
│    铭刻 · 回复类        │
│    生效：常驻           │
│    效果：每秒回复生命   │
├────────────────────────┤
│ 🟣 史诗天赋·闪现 Lv.5  │ ← 紫色文本
│    显化 · 机制类        │
│    生效：双击空格时     │
│    效果：短距离闪现     │
└────────────────────────┘
```

**渲染逻辑**：
```java
public void renderTalentUI(GuiGraphics graphics, int mouseX, int mouseY) {
    Minecraft mc = Minecraft.getInstance();
    List<AbstractEffect> talents = getPlayerTalents(mc.player);
    
    // 排序
    talents.sort((a, b) -> {
        int rarityCompare = Integer.compare(a.getRarity().ordinal(), b.getRarity().ordinal());
        if (rarityCompare != 0) return rarityCompare;
        return Integer.compare(b.getLevel(), a.getLevel());
    });
    
    // 计算UI位置（背包界面右侧）
    int uiX = calculateUIX();
    int uiY = calculateUIY();
    
    // 绘制背景
    renderer.renderBackground(graphics, uiX, uiY, UI_WIDTH, UI_HEIGHT);
    
    // 绘制标题
    graphics.drawString(mc.font, "已解锁天赋", uiX + 8, uiY + 8, 0xFFFFFF);
    
    // 绘制天赋列表
    int currentY = uiY + 20;
    for (AbstractEffect talent : talents) {
        renderer.renderTalentCard(graphics, talent, uiX + 4, currentY);
        currentY += TALENT_CARD_HEIGHT + 4;
        
        // 超过面板高度则显示滚动条
        if (currentY > uiY + UI_HEIGHT - 10) {
            renderer.renderScrollbar(graphics, scrollOffset, maxScroll);
            break;
        }
    }
}
```

---

### 5. UI配置更新

**新增配置项**：
```java
public class UIConfig {
    // 物品UI快捷键
    private static KeyMapping toggleItemUIKey = new KeyMapping(
        "key.yizmodqzk.toggle_item_ui",
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.yizmodqzk"
    );
    
    // 天赋UI快捷键（新增）
    private static KeyMapping toggleTalentUIKey = new KeyMapping(
        "key.yizmodqzk.toggle_talent_ui",
        GLFW.GLFW_KEY_LEFT_SHIFT,
        "key.categories.yizmodqzk"
    );
    
    // UI开关状态
    private static boolean customItemUIEnabled = false;
    private static boolean playerTalentUIEnabled = false;  // 新增
    
    // 天赋UI显示设置
    private static int talentPanelWidth = 200;             // 天赋面板宽度
    private static int talentPanelHeight = 166;            // 天赋面板高度
    private static int talentCardHeight = 60;              // 单个天赋卡片高度
    
    // 快捷键检测
    + static boolean checkItemUIToggle();
    + static boolean checkTalentUIToggle();
}
```

---

### 6. 关键实现细节

#### 6.1 界面检测逻辑

```java
@SubscribeEvent
public void onScreenRender(ScreenEvent.Render.Post event) {
    Screen screen = event.getScreen();
    
    // 仅在生存背包界面显示
    if (!(screen instanceof InventoryScreen)) return;
    
    // 检查天赋UI是否开启
    if (!UIConfig.isPlayerTalentUIEnabled()) return;
    
    // 绘制天赋UI
    PlayerTalentUI renderer = new PlayerTalentUI();
    renderer.renderTalentUI(event.getGuiGraphics(), 
                           event.getMouseX(), 
                           event.getMouseY());
}
```

#### 6.2 快捷键检测

```java
@SubscribeEvent
public void onKeyInput(InputEvent.Key event) {
    Minecraft mc = Minecraft.getInstance();
    
    // CTRL + ALT：切换物品UI
    if (KeyMapping.isDown(GLFW.GLFW_KEY_LEFT_ALT) && Screen.hasControlDown()) {
        UIConfig.toggleItemUI();
        mc.player.sendSystemMessage(Component.literal(
            UIConfig.isItemUIEnabled() ? 
                "§a自定义物品UI已开启" : 
                "§c自定义物品UI已关闭"
        ));
    }
    
    // CTRL + SHIFT：切换天赋UI
    if (KeyMapping.isDown(GLFW.GLFW_KEY_LEFT_SHIFT) && Screen.hasControlDown()) {
        UIConfig.toggleTalentUI();
        mc.player.sendSystemMessage(Component.literal(
            UIConfig.isPlayerTalentUIEnabled() ? 
                "§a天赋信息UI已开启" : 
                "§c天赋信息UI已关闭"
        ));
    }
}
```

#### 6.3 获取玩家已解锁的天赋

```java
public List<AbstractEffect> getPlayerTalents(LivingEntity player) {
    List<AbstractEffect> talents = new ArrayList<>();
    
    // 遍历所有已注册的效果
    for (AbstractEffect effect : ModRegistries.getAllEffects().values()) {
        // 检查是否为实体绑定感知（天赋）
        boolean isEntityTalent = effect.getPerceptionModes().stream()
            .anyMatch(mode -> mode instanceof EntityPerception);
        
        if (!isEntityTalent) continue;
        
        // 检查是否已解锁
        if (UnlockManager.isUnlocked(player, effect.getId())) {
            talents.add(effect);
        }
    }
    
    return talents;
}
```

#### 6.4 原版纹理使用

```java
public class TextureReferences {
    // 原版背包纹理
    public static final ResourceLocation INVENTORY_TEXTURE = 
        ResourceLocation.parse("textures/gui/container/inventory.png");
    
    // 原版物品提示框纹理
    public static final ResourceLocation TOOLTIP_TEXTURE = 
        ResourceLocation.parse("textures/gui/tooltip.png");
    
    // 使用示例
    public void renderPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        // 绘制九宫格背景（使用原版纹理）
        RenderSystem.setShaderTexture(0, INVENTORY_TEXTURE);
        GuiComponent.blit(matrixStack, x, y, 0, u, v, width, height, textureWidth, textureHeight);
    }
}
```

---

## 🖥️ 物品信息显示 UI 系统

### 1. 物品名称前缀显示逻辑

**职责**：当物品带有词缀或随影效果时，在物品名称最前方添加类型标识。

**显示规则**：
```
仅有词缀：    [词缀] 烈焰之剑
仅有随影：    [随影] 远古之盾
同时具备：    [词缀/随影] 神秘法杖
均无：        普通铁剑（不添加前缀）
```

**关键逻辑**：
- ✅ 前缀仅提示玩家该物品**具备效果类型**，不显示具体效果内容
- ✅ 具体效果信息在**自定义物品信息UI**中显示
- ✅ 前缀颜色可根据稀有度变化（由开发者决定）

**实现位置**：`ItemNamePrefixHandler.java`

---

### 2. ItemInfoUI.java（物品信息UI主类）

**职责**：完全替代原版指针悬浮物品提示，通过自定义绘制显示新的物品信息。

**快捷键控制**：
- 开启/关闭：`CTRL + ALT`
- 状态持久化：配置文件中记录开关状态

**核心功能**：
```java
// 禁用原版悬浮提示
@SubscribeEvent
public void onRenderTooltip(RenderTooltipEvent.Pre event) {
    if (UIConfig.isCustomUIEnabled()) {
        event.setCanceled(true);  // 完全关闭原版显示
    }
}

// 绘制自定义UI
@SubscribeEvent
public void onRenderCustomUI(RenderGuiEvent.Post event) {
    if (UIConfig.isCustomUIEnabled() && hoveredItem != null) {
        renderCustomItemInfo(hoveredItem, mouseX, mouseY);
    }
}
```

---

### 3. UI 显示格式

**显示层级结构**：
```
1. 物品名称                        ← 始终显示
   1.1 攻击力                      ← 仅当物品具备该属性时显示
       1.1.1 护甲值                ← 仅当物品具备该属性时显示
   1.2 攻击速度                    ← 仅当物品具备该属性时显示
       1.2.1 护甲韧性              ← 仅当物品具备该属性时显示
   1.3 耐久值                      ← 仅当物品具备该属性时显示
   1.4 交互距离                    ← 仅当物品具备该属性时显示
   1.5 [词缀] 或 [随影] 具体文本   ← 仅当物品有效果时显示
```

**条件显示逻辑**：
```java
public void renderCustomItemInfo(ItemStack stack, int x, int y) {
    // 1. 始终显示物品名称
    renderItemName(stack, x, y);
    
    // 2. 条件显示属性（不存在的属性跳过）
    if (hasAttackDamage(stack)) {
        renderAttackDamage(stack, x, y + lineHeight);
    }
    if (hasArmorValue(stack)) {
        renderArmorValue(stack, x, y + lineHeight * 2);
    }
    if (hasAttackSpeed(stack)) {
        renderAttackSpeed(stack, x, y + lineHeight * 3);
    }
    if (hasArmorToughness(stack)) {
        renderArmorToughness(stack, x, y + lineHeight * 4);
    }
    if (hasDurability(stack)) {
        renderDurability(stack, x, y + lineHeight * 5);
    }
    if (hasInteractionRange(stack)) {
        renderInteractionRange(stack, x, y + lineHeight * 6);
    }
    
    // 3. 显示效果文本（词缀/随影详情）
    if (hasEffects(stack)) {
        renderEffectTooltips(stack, x, y + nextLine);
    }
}
```

**示例显示**：
```
烈焰之剑
  攻击力：12.0
  攻击速度：1.2
  耐久值：1562/1562
  
  [词缀]
  └─ 神话词缀·烈焰 (Lv.10)
     └─ 残响 · 攻击类
     └─ 命中时：25% 概率点燃目标
     └─ 攻击力 +25%（乘法修正）
```

---

### 4. ItemAttributeDisplay.java（属性显示管理器）

**职责**：管理物品属性的检测和格式化显示。

**核心方法**：
```java
public class ItemAttributeDisplay {
    // 检测物品是否具备某属性
    + boolean hasAttribute(ItemStack stack, Attribute attribute);
    
    // 获取属性值（已应用所有修正器）
    + double getAttributeValue(ItemStack stack, Attribute attribute);
    
    // 格式化属性显示文本
    + String formatAttribute(Attribute attribute, double value);
    
    // 获取所有可用属性列表
    + List<AttributeInfo> getAvailableAttributes(ItemStack stack);
}
```

**支持的属性列表**：
| 属性 | 内部标识 | 显示名称 | 适用物品类型 |
|------|---------|---------|------------|
| 攻击力 | `attack_damage` | 攻击力 | 武器、工具 |
| 护甲值 | `armor` | 护甲值 | 盔甲 |
| 攻击速度 | `attack_speed` | 攻击速度 | 武器 |
| 护甲韧性 | `armor_toughness` | 护甲韧性 | 高级盔甲 |
| 耐久值 | `durability` | 耐久值 | 可损坏物品 |
| 交互距离 | `block_interaction_range` | 交互距离 | 工具 |

---

### 5. EffectTooltipRenderer.java（效果文本渲染器）

**职责**：渲染物品的词缀/随影效果详情。

**核心功能**：
```java
public class EffectTooltipRenderer {
    // 渲染效果列表（已排序）
    + void renderEffects(ItemStack stack, int x, int y);
    
    // 获取物品的所有效果并排序
    + List<AbstractEffect> getSortedEffects(ItemStack stack);
    
    // 渲染单个效果
    + void renderSingleEffect(AbstractEffect effect, int x, int y);
    
    // 根据稀有度设置颜色
    + int getRarityColor(Rarity rarity);
}
```

**稀有度颜色规范**：
| 稀有度 | 颜色名称 | 颜色值 (RGB) | 颜色值 (Hex) | Minecraft 颜色码 |
|--------|---------|-------------|-------------|----------------|
| 神话 | 红色 | `(255, 85, 85)` | `#FF5555` | `§c` |
| 传说 | 金色 | `(255, 170, 0)` | `#FFAA00` | `§6` |
| 史诗 | 紫色 | `(170, 0, 170)` | `#AA00AA` | `§5` |
| 精良 | 蓝色 | `(85, 85, 255)` | `#5555FF` | `§3` |
| 平凡 | 白色 | `(255, 255, 255)` | `#FFFFFF` | `§f` |

**效果排序规则**：
```
排序优先级：
1. 稀有度降序（神话 > 传说 > 史诗 > 精良 > 平凡）
2. 等级降序（同稀有度时，等级高的在上）
```

**排序算法**：
```java
public List<AbstractEffect> getSortedEffects(ItemStack stack) {
    List<AbstractEffect> effects = getItemEffects(stack);
    
    effects.sort((a, b) -> {
        // 第一优先级：稀有度降序
        int rarityCompare = b.getRarity().ordinal() - a.getRarity().ordinal();
        if (rarityCompare != 0) {
            return rarityCompare;
        }
        
        // 第二优先级：等级降序
        return b.getLevel() - a.getLevel();
    });
    
    return effects;
}
```

**效果文本格式**：
```
[词缀]
└─ 神话词缀·烈焰 (Lv.10)          ← 红色文本，稀有度最高
   └─ 残响 · 攻击类
   └─ 感知方式：主手持有 / 副手持有
   └─ 生效条件：实体攻击时
   └─ 效果：
      ├─ 命中时：25% 概率点燃目标（3秒）
      └─ 攻击力 +25%（乘法修正）

└─ 神话词缀·冰霜 (Lv.8)           ← 红色文本，同稀有度但等级低
   └─ 显化 · 机制类
   └─ 感知方式：主手持有
   └─ 生效条件：实体攻击时
   └─ 效果：
      └─ 命中时：15% 概率减速目标（2秒）

└─ 传说词缀·风暴 (Lv.10)          ← 金色文本，稀有度较低
   └─ 升灵 · 被动类
   └─ 感知方式：背包内
   └─ 生效条件：常驻
   └─ 效果：
      └─ 移动速度 +10%
```

**渲染示例代码**：
```java
public void renderEffects(ItemStack stack, int x, int y) {
    List<AbstractEffect> sortedEffects = getSortedEffects(stack);
    
    int currentY = y;
    for (AbstractEffect effect : sortedEffects) {
        // 根据稀有度获取颜色
        int rarityColor = getRarityColor(effect.getRarity());
        
        // 渲染效果名称（带颜色）
        String effectName = String.format("%s %s (Lv.%d)",
            effect.getPerceptionTypeName(),  // [词缀] 或 [随影]
            effect.getDisplayName(),
            effect.getLevel()
        );
        renderText(effectName, x, currentY, rarityColor);
        currentY += lineHeight;
        
        // 渲染父类信息
        renderText("└─ " + effect.getParentType().getChineseName() + " · " + 
                   effect.getParentType().getRecommendedUse(), 
                   x + indent, currentY, 0x888888);
        currentY += lineHeight;
        
        // 渲染详细信息...
    }
}
```

---

### 6. 关键实现细节

#### 6.1 稀有度颜色映射

```java
public class RarityColors {
    public static final int MYTHIC_COLOR = 0xFFFF5555;      // 红色
    public static final int LEGENDARY_COLOR = 0xFFFFAA00;   // 金色
    public static final int EPIC_COLOR = 0xFFAA00AA;        // 紫色
    public static final int RARE_COLOR = 0xFF5555FF;        // 蓝色
    public static final int COMMON_COLOR = 0xFFFFFFFF;      // 白色
    
    public static int getRarityColor(Rarity rarity) {
        return switch (rarity) {
            case MYTHIC -> MYTHIC_COLOR;
            case LEGENDARY -> LEGENDARY_COLOR;
            case EPIC -> EPIC_COLOR;
            case RARE -> RARE_COLOR;
            case COMMON -> COMMON_COLOR;
        };
    }
}
```

#### 6.2 效果排序逻辑详解

**排序规则示例**：
```
假设物品拥有以下效果：
- 神话词缀·烈焰 (Lv.8)
- 传说词缀·风暴 (Lv.10)
- 神话词缀·冰霜 (Lv.10)
- 精良词缀·坚韧 (Lv.5)
- 史诗词缀·闪电 (Lv.7)

排序后显示顺序：
1. 神话词缀·冰霜 (Lv.10)     ← 神话稀有度，等级最高
2. 神话词缀·烈焰 (Lv.8)      ← 神话稀有度，等级较低
3. 传说词缀·风暴 (Lv.10)     ← 传说稀有度
4. 史诗词缀·闪电 (Lv.7)      ← 史诗稀有度
5. 精良词缀·坚韧 (Lv.5)      ← 精良稀有度
```

**实现代码**：
```java
public List<AbstractEffect> getSortedEffects(ItemStack stack) {
    List<AbstractEffect> effects = getItemEffects(stack);
    
    effects.sort((a, b) -> {
        // 第一优先级：稀有度降序（ordinal 越小越稀有）
        // Rarity 枚举定义顺序：MYTHIC(0), LEGENDARY(1), EPIC(2), RARE(3), COMMON(4)
        int rarityCompare = Integer.compare(a.getRarity().ordinal(), b.getRarity().ordinal());
        if (rarityCompare != 0) {
            return rarityCompare;  // 稀有度高的排前面
        }
        
        // 第二优先级：等级降序（等级高的排前面）
        return Integer.compare(b.getLevel(), a.getLevel());
    });
    
    return effects;
}
```

#### 6.3 物品名称前缀处理器

**逻辑**：
```java
public class ItemNamePrefixHandler {
    
    // 获取物品名称前缀
    public static String getNamePrefix(ItemStack stack) {
        List<AbstractEffect> effects = getItemEffects(stack);
        
        boolean hasAffix = false;   // 是否有词缀
        boolean hasShadow = false;  // 是否有随影
        
        for (AbstractEffect effect : effects) {
            for (PerceptionMode mode : effect.getPerceptionModes()) {
                if (mode instanceof ItemPerception) {
                    hasAffix = true;
                } else if (mode instanceof ContainerPerception) {
                    hasShadow = true;
                }
            }
        }
        
        // 根据感知方式组合返回前缀
        if (hasAffix && hasShadow) {
            return "[词缀/随影]";
        } else if (hasAffix) {
            return "[词缀]";
        } else if (hasShadow) {
            return "[随影]";
        }
        
        return "";  // 无前缀
    }
    
    // 修改物品显示名称（通过 Mixin 注入）
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void onGetHoverName(CallbackInfoReturnable<Component> cir) {
        String prefix = getNamePrefix(this);
        if (!prefix.isEmpty()) {
            Component originalName = cir.getReturnValue();
            Component prefixedName = Component.literal(prefix + " ")
                .withStyle(ChatFormatting.YELLOW)  // 前缀颜色
                .append(originalName);
            cir.setReturnValue(prefixedName);
        }
    }
}
```

---

### 7. UIConfig.java（UI配置）

**职责**：管理UI的快捷键和显示设置。

```java
public class UIConfig {
    // 快捷键配置
    private static KeyMapping toggleUIKey = new KeyMapping(
        "key.yizmodqzk.toggle_item_ui",
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.yizmodqzk"
    );
    
    // UI开关状态
    private static boolean customUIEnabled = false;
    
    // 显示设置
    private static int uiBackgroundColor = 0xCC000000;  // 半透明黑色
    private static int uiBorderColor = 0xFF888888;      // 灰色边框
    private static int lineHeight = 12;                 // 行高
    
    // 配置持久化
    + static void saveConfig();
    + static void loadConfig();
}
```

---

### 8. 关键实现细节

#### 8.1 快捷键检测
```java
@SubscribeEvent
public void onKeyInput(InputEvent.Key event) {
    if (toggleUIKey.isDown() && Screen.hasControlDown()) {
        UIConfig.toggleCustomUI();
        // 发送聊天消息提示
        player.sendSystemMessage(Component.literal(
            UIConfig.isCustomUIEnabled() ? 
                "§a自定义物品UI已开启" : 
                "§c自定义物品UI已关闭"
        ));
    }
}
```

#### 8.2 原版提示禁用
```java
@SubscribeEvent(priority = EventPriority.HIGHEST)
public void onCancelVanillaTooltip(RenderTooltipEvent.Pre event) {
    if (UIConfig.isCustomUIEnabled()) {
        event.setCanceled(true);
    }
}
```

#### 8.3 自定义UI绘制
```java
@SubscribeEvent
public void onRenderOverlay(RenderGuiEvent.Post event) {
    if (!UIConfig.isCustomUIEnabled()) return;
    
    ItemStack hoveredItem = getHoveredItem();
    if (hoveredItem.isEmpty()) return;
    
    // 绘制背景
    renderBackground(x, y, width, height);
    
    // 绘制边框
    renderBorder(x, y, width, height);
    
    // 绘制属性信息
    renderAttributes(hoveredItem, x + padding, y + padding);
    
    // 绘制效果信息
    renderEffects(hoveredItem, x + padding, nextY);
}
```

---

---

## 🎯 前置库职责边界：效果事件与生效判断

> **核心原则**：前置库只提供**事件分发机制**和**接口契约**，不写具体的生效判断逻辑。
>
> 📌 **可选工具包**：前置库还提供**伤害计算**、**属性修改**等可选工具，详见 [OPTIONAL_TOOLS.md](OPTIONAL_TOOLS.md)

---

### 1. 设计理念

#### ❌ 前置库不应该做的事：
```java
// ❌ 错误示例：前置库写死了具体的判断逻辑
public class EntityAttackCondition implements ActivationCondition {
    @Override
    public boolean shouldActivate(EffectContext context) {
        // 前置库不应该知道这些业务逻辑！
        if (context.attacker.hasEffect("strength")) {
            return true;
        }
        if (context.level.isRaining()) {
            return false;
        }
        return context.target.fireImmune();
    }
}
```

**问题**：
- 限制了第三方模组的灵活性
- 前置库无法预见所有可能的判断条件
- 违反了"高内聚低耦合"原则

---

#### ✅ 前置库应该做的事：

```java
// ✅ 正确示例：前置库只提供接口和空实现
public class EntityAttackCondition implements ActivationCondition {
    @Override
    public boolean shouldActivate(EffectContext context) {
        // 默认返回 true，由第三方模组 Override
        return true;
    }
}

// 第三方模组实现具体逻辑
public class MyFireSwordEffect extends AbstractEffect {
    public MyFireSwordEffect() {
        this.activationCondition = new EntityAttackCondition() {
            @Override
            public boolean shouldActivate(EffectContext context) {
                // 自定义判断：目标不能免疫火焰
                if (context.target.fireImmune()) return false;
                // 自定义判断：雨天效果减半
                if (context.level.isRaining()) {
                    return Math.random() < 0.5;
                }
                return true;
            }
        };
    }
}
```

---

### 2. 前置库提供的核心组件

#### 2.1 EffectEvent.java（效果事件基类）

**职责**：定义事件的数据结构，不包含具体业务逻辑。

```java
public abstract class EffectEvent {
    protected AbstractEffect effect;           // 触发事件的效果
    protected LivingEntity entity;             // 触发事件的实体
    protected Instant timestamp;               // 事件时间戳
    
    /**
     * 事件分发接口（由子类实现具体分发逻辑）
     */
    public abstract void dispatch();
    
    /**
     * 获取事件类型
     */
    public abstract EventType getEventType();
    
    public enum EventType {
        PRE_ATTACK,          // 攻击前
        CALCULATE_DAMAGE,    // 伤害计算
        HIT,                 // 命中
        POST_ATTACK,         // 攻击后
        KILL,                // 击杀
        EQUIP,               // 装备
        UNEQUIP              // 卸载
    }
}
```

---

#### 2.2 EffectContext.java（效果上下文）

**职责**：传递效果生效所需的所有信息，不判断是否生效。

```java
public class EffectContext {
    // 实体信息
    public LivingEntity entity;          // 效果持有者
    public Entity target;                // 目标实体（可能为null）
    public Level level;                  // 所在世界
    
    // 物品信息
    public ItemStack itemStack;          // 效果来源物品
    public EquipmentSlot slot;           // 装备槽位
    
    // 效果信息
    public AbstractEffect effect;        // 当前效果
    public PerceptionMode activeMode;    // 激活的感知方式
    
    // 环境信息
    public Vec3 position;                // 位置
    public boolean isRaining;            // 是否下雨
    public boolean isNight;              // 是否夜晚
    public int lightLevel;               // 光照等级
    
    /**
     * 创建上下文（工厂方法）
     */
    public static EffectContext create(LivingEntity entity, Entity target) {
        EffectContext ctx = new EffectContext();
        ctx.entity = entity;
        ctx.target = target;
        ctx.level = entity.level();
        ctx.position = entity.position();
        ctx.isRaining = entity.level().isRaining();
        ctx.isNight = entity.level().isNight();
        ctx.lightLevel = entity.level().getLightLevel(entity.blockPosition());
        return ctx;
    }
}
```

---

#### 2.3 EffectEventBus.java（效果事件总线）

**职责**：分发事件到所有监听器，不包含判断逻辑。

```java
public class EffectEventBus {
    private static final List<EffectListener> listeners = new ArrayList<>();
    
    /**
     * 注册事件监听器
     */
    public static void register(EffectListener listener) {
        listeners.add(listener);
    }
    
    /**
     * 分发事件（遍历所有监听器）
     */
    public static void post(EffectEvent event) {
        for (EffectListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                YizMod.LOGGER.error("Effect event dispatch error: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 分发上下文到效果系统（核心调度方法）
     */
    public static void dispatchContext(EffectContext context) {
        // 1. 获取所有已注册的效果
        List<AbstractEffect> effects = ModRegistries.getAllEffects();
        
        // 2. 遍历效果，检查感知和解锁条件
        for (AbstractEffect effect : effects) {
            // 检查是否满足感知方式
            if (!effect.checkPerception(context.entity, context.activeMode)) {
                continue;
            }
            
            // 检查是否已解锁
            if (!effect.isUnlocked(context.entity)) {
                continue;
            }
            
            // 检查生效条件（调用第三方模组的判断逻辑）
            if (effect.getActivationCondition().shouldActivate(context)) {
                // 生效条件满足，执行效果
                effect.execute(context);
            }
        }
    }
}
```

---

#### 2.4 ActivationCondition.java（生效条件接口）

**职责**：定义生效判断的契约，不包含具体逻辑。

```java
@FunctionalInterface
public interface ActivationCondition {
    /**
     * 判断效果是否应该生效
     * @param context 效果上下文（包含所有需要的信息）
     * @return true = 生效，false = 不生效
     */
    boolean shouldActivate(EffectContext context);
    
    /**
     * 组合多个条件（AND逻辑）
     */
    default ActivationCondition and(ActivationCondition other) {
        return (ctx) -> this.shouldActivate(ctx) && other.shouldActivate(ctx);
    }
    
    /**
     * 组合多个条件（OR逻辑）
     */
    default ActivationCondition or(ActivationCondition other) {
        return (ctx) -> this.shouldActivate(ctx) || other.shouldActivate(ctx);
    }
    
    /**
     * 取反条件
     */
    default ActivationCondition negate() {
        return (ctx) -> !this.shouldActivate(ctx);
    }
}
```

---

#### 2.5 内置生效条件（空实现）

**职责**：提供常用的生效条件模板，默认返回 true，由第三方模组 Override。

```java
// 实体攻击时生效（默认总是生效）
public class EntityAttackCondition implements ActivationCondition {
    @Override
    public boolean shouldActivate(EffectContext context) {
        return true;  // 空实现，第三方模组可Override
    }
}

// 飞行物命中时生效（默认总是生效）
public class ProjectileHitCondition implements ActivationCondition {
    @Override
    public boolean shouldActivate(EffectContext context) {
        return true;  // 空实现，第三方模组可Override
    }
}

// 无条件生效（常驻效果）
public class PassiveCondition implements ActivationCondition {
    @Override
    public boolean shouldActivate(EffectContext context) {
        return true;  // 总是返回true
    }
}

// 自定义生效条件（开发者扩展接口）
public abstract class CustomActivationCondition implements ActivationCondition {
    // 开发者继承此类，实现自己的判断逻辑
}
```

---

### 3. 完整工作流程示例

#### 场景：第三方模组创建"火焰剑"词缀

**第一步：创建效果类**
```java
// 第三方模组代码
public class FireSwordAffix extends AbstractEffect {
    public FireSwordAffix() {
        super(ResourceLocation.fromNamespaceAndPath("mymod", "fire_sword"));
        
        // 设置六大维度
        this.parentType = ParentType.ECHO;  // 残响（攻击类）
        this.level = 5;
        this.perceptionModes = Set.of(ItemPerception.MAIN_HAND);
        this.activationCondition = createActivationCondition();  // 自定义判断
        this.rarity = Rarity.EPIC;
    }
    
    /**
     * 创建自定义生效判断逻辑（第三方模组职责）
     */
    private ActivationCondition createActivationCondition() {
        return new EntityAttackCondition() {
            @Override
            public boolean shouldActivate(EffectContext context) {
                // 判断1：目标不能免疫火焰
                if (context.target.fireImmune()) {
                    return false;
                }
                
                // 判断2：雨天有50%概率失效
                if (context.isRaining && Math.random() < 0.5) {
                    return false;
                }
                
                // 判断3：目标在燃烧时效果翻倍（返回true但后续execute会处理）
                return true;
            }
        };
    }
    
    /**
     * 执行效果逻辑（第三方模组职责）
     */
    @Override
    public void execute(EffectContext context) {
        // 点燃目标3秒
        int duration = context.target.isOnFire() ? 6 : 3;
        context.target.setSecondsOnFire(duration);
        
        // 播放火焰粒子效果
        context.level.addParticle(
            ParticleTypes.FLAME,
            context.target.getX(),
            context.target.getY() + 1,
            context.target.getZ(),
            0, 0.1, 0
        );
    }
}
```

**第二步：效果自动注册**
```java
// 静态初始化时自动注册到 ModRegistries
static {
    ModRegistries.registerEffect(new FireSwordAffix());
}
```

**第三步：游戏运行时自动触发**
```java
// 前置库的 EffectEventBus 自动处理：

1. 玩家手持铁剑攻击僵尸
   ↓
2. Mixin 注入到 attack() 方法，创建 EffectContext
   ↓
3. EffectEventBus.dispatchContext(context)
   ↓
4. 遍历所有效果，检查 FireSwordAffix：
   - checkPerception() → ✅ 主手持有铁剑
   - isUnlocked() → ✅ 玩家已解锁
   - shouldActivate(context) → ✅ 调用第三方模组的判断逻辑
   ↓
5. 执行 FireSwordAffix.execute(context)
   ↓
6. 僵尸被点燃！
```

---

### 4. 职责分工总结

| 组件 | 前置库职责 | 第三方模组职责 | 是否包含判断逻辑 |
|------|-----------|---------------|-----------------|
| `EffectEvent` | 定义事件结构 | 继承创建具体事件 | ❌ 不包含 |
| `EffectContext` | 传递上下文信息 | 使用上下文数据 | ❌ 不包含 |
| `EffectEventBus` | 分发事件、调度效果 | 注册监听器 | ✅ 包含调度逻辑 |
| `ActivationCondition` | 定义接口契约 | 实现具体判断 | ❌ 不包含 |
| `EntityAttackCondition` | 提供空实现模板 | Override 判断逻辑 | ❌ 只返回true |
| `CustomEffect` | - | 完整实现效果和判断 | ✅ 包含所有逻辑 |

---

### 5. 设计优势

| 优势 | 说明 |
|------|------|
| **高扩展性** | 第三方模组可以自定义任何判断条件 |
| **低耦合** | 前置库不知道具体业务逻辑，只负责分发 |
| **灵活性** | 支持条件组合（AND/OR/NOT） |
| **可测试性** | 判断逻辑和执行逻辑分离，易于单元测试 |
| **职责清晰** | 前置库管框架，第三方模组管业务 |

---

## 🎯 下一步计划

### 核心框架
1. ✅ 创建本架构文档
2. ✅ 创建可选工具包设计文档 [OPTIONAL_TOOLS.md](OPTIONAL_TOOLS.md)
3. ⏳ 创建 `AbstractEffect.java` 基类
4. ⏳ 创建 `ParentType.java` 五大父类枚举
5. ⏳ 创建感知方式系统（5 个文件）
6. ⏳ 创建解锁系统（UnlockManager）
7. ⏳ 创建生效条件系统（5 个文件）
8. ⏳ 创建稀有度枚举

### 可选工具包（详见 OPTIONAL_TOOLS.md）
9. ⏳ 创建 `tool/damage/DefaultDamageCalculator.java`
10. ⏳ 创建 `tool/damage/DamageResult.java`
11. ⏳ 创建 `tool/damage/DamageType.java`
12. ⏳ 创建 `tool/attribute/AttributeModificationHelper.java`
13. ⏳ 更新 `AbstractEffect.java` 添加快捷方法

### UI系统
14. ⏳ 创建物品名称前缀处理器 `ItemNamePrefixHandler.java`
15. ⏳ 创建物品信息UI系统（4 个文件）
16. ⏳ 创建玩家实体天赋UI系统（3 个文件）

### 系统集成
17. ⏳ 更新 ModRegistries 注册表
18. ⏳ 更新 EffectStats 属性模型
19. ⏳ 编写数据加载器（JSON → Effect）
20. ⏳ 创建 Mixin 注入（禁用原版悬浮提示）
21. ⏳ 创建 Mixin 注入（修改物品显示名称）

---

---

**文档版本**: v1.0  
**创建日期**: 2026-05-11  
**作者**: YizMod QZK 架构团队
