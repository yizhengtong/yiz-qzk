# YizMod QZK — 前置库公开 API 文档

> 下游模组通过引用本前置库，直接调用 `YizModQZKAPI` 的静态方法完成所有功能开发。
> 模组本体只需处理**物品注册**和**属性注册**，业务逻辑全部委托给本前置库。

---

## 目录

1. [快速接入](#1-快速接入)
2. [伤害系统](#2-伤害系统)
3. [属性绑定伤害](#3-属性绑定伤害)
4. [直接健康值修改](#4-直接健康值修改)
5. [禁疗系统](#5-禁疗系统)
6. [效果框架](#6-效果框架)
7. [物品效果 NBT](#7-物品效果-nbt)
8. [天赋查询与 UI](#8-天赋查询与-ui)
9. [事件监听](#9-事件监听)
10. [JSON 数据驱动](#10-json-数据驱动)
11. [完整示例](#11-完整示例)
12. [FAQ](#faq)

---

## 1. 快速接入

### 1.1 发布到 Maven

在本项目目录执行以下命令，将前置库发布到本地 Maven 仓库：

```bash
# 发布到 ~/.m2/repository（推荐，IDE 自动识别）
gradle publishToMavenLocal

# 或发布到项目内 file://repo/ 目录
gradle publish
```

发布产物坐标（由 `mod_group_id` 和 `mod_id` 决定）：
```
net.minecraft.client.yiz:yizmodqzk:1.0.0
```

### 1.2 获取分发包（推荐）

在本项目目录执行：

```bash
gradle packageLib
```

在 `build/distributions/` 下生成 `yizmodqzk-{version}.zip`，解压后直接放入服务端/客户端的 `mods` 文件夹即可。

---

### 1.3 下游项目引入依赖

**`build.gradle`**：

```gradle
repositories {
    // 方式 A：本地 Maven 仓库（使用 publishToMavenLocal 后）
    mavenLocal()

    // 方式 B：Git 子模块的 file repo（使用 gradle publish 后）
    maven { url "file:///path/to/yiz-qzk/repo" }
}

dependencies {
    // 本前置库
    implementation "net.minecraft.client.yiz:yizmodqzk:1.0.0"
}
```

> **注意**：`build.gradle` 中的 `implementation` 配置确保编译期和运行期均可访问 API 类。
> 本前置库也是一个 NeoForge Mod，运行时需放在 `mods` 目录。

### 1.4 声明模组依赖

在 `neoforge.mods.toml` 中声明前置库依赖：

```toml
[[dependencies.yourmodid]]
    modId = "yizmodqzk"
    mandatory = true
    versionRange = "[1.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

### 1.4 自动复制前置库到 build/libs

下游模组 `gradle build` 时，想让 `yizmodqzk` 的 JAR 自动出现在 `build/libs/` 目录中，在 `build.gradle` 添加：

```gradle
dependencies {
    implementation "net.minecraft.client.yiz:yizmodqzk:1.0.0"
}

// build 时将前置库复制到 build/libs/（与模组 JAR 放一起）
tasks.register('collectLibs', Copy) {
    from configurations.runtimeClasspath
    into layout.buildDirectory.dir('libs')
}
tasks.named('build') { dependsOn 'collectLibs' }
```

执行 `gradle build` 后，`build/libs/` 中同时包含：
```
your-mod-1.0.0.jar        ← 你的模组
yizmodqzk-1.0.0.jar       ← 前置库（自动复制）
```

### 1.5 导入 API

```java
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.api.DamageResult;
import net.minecraft.client.yiz.api.DamageEvent;
import net.minecraft.client.yiz.api.DamageType;
```

---

## 2. 伤害系统

所有伤害方法发布 `DamageEvent` 到 NeoForge EventBus，可被监听和取消。

### 2.1 固定数值伤害

绕过目标自定义 `hurt()`，经 ASM Delta 三層系统应用。

```java
// 对 zombie 造成 50 点固定伤害
DamageResult result = YizModQZKAPI.damage(zombie, 50.0f, player);
```

### 2.2 百分比伤害

基于目标最大生命值计算伤害，同样绕过自定义 `hurt()`。

```java
// 造成最大生命值 10% 的伤害
DamageResult result = YizModQZKAPI.percentDamage(zombie, 10.0f, player);
```

### 2.3 真实伤害

直接 `setHealth()` 扣血，**无视护甲、无敌帧、伤害减免**。

```java
// 30 点真实伤害，无视一切防御
DamageResult result = YizModQZKAPI.trueDamage(zombie, 30.0f, player);
```

### 2.4 破甲伤害

跳过护甲减伤，使用魔法伤害源，**仍受无敌帧限制**。

```java
// 20 点破甲伤害
DamageResult result = YizModQZKAPI.armorPiercingDamage(zombie, 20.0f, player);
```

### 2.5 破无敌帧伤害

临时清除目标 `invulnerableTime`，**仍受护甲减伤**。

```java
// 无视无敌帧，但护甲正常计算
DamageResult result = YizModQZKAPI.pierceInvulnerabilityDamage(zombie, 20.0f, player);
```

### 2.6 破甲 + 破无敌帧（组合）

```java
// 同时跳过护甲和无敌帧
DamageResult result = YizModQZKAPI.armorPiercingAndPierceInvulnerabilityDamage(zombie, 20.0f, player);
```

### 2.7 DamageResult

所有伤害方法返回 `DamageResult` record：

```java
public record DamageResult(
    float applied,     // 实际应用的伤害值
    float delta,       // 目标剩余 delta 偏移量（仅 damage/percentDamage 有效）
    boolean canceled,  // 是否被取消
    String reason      // 取消原因
)
```

工厂方法：
```java
DamageResult.success(applied, delta);  // 成功
DamageResult.canceled("reason");       // 取消
```

---

## 3. 属性绑定伤害

注册原版 Attribute 作为伤害源，攻击者每次近战攻击**自动附加**等量伤害。

### 3.1 通用伤害属性

伤害经三層系统（Delta → ChannelScanner → DirectHealthFallback）施加，绕过目标自定义 `hurt()`。

```java
// 在你的模组初始化时注册
YizModQZKAPI.registerDamageAttribute(MyAttributes.FIRE_DAMAGE);

// 攻击者拥有 FIRE_DAMAGE = 5.0 → 每次攻击自动附加 5 点额外伤害
```

### 3.2 真实伤害属性

```java
// 每点属性值 × 缩放系数 = 真实伤害
YizModQZKAPI.registerTrueDamageAttribute(MyAttributes.TRUE_DAMAGE, 1.0f);
```

### 3.3 破甲伤害属性

```java
// 每点属性值 × 缩放系数 = 破甲伤害
YizModQZKAPI.registerArmorPiercingAttribute(MyAttributes.ARMOR_PIERCE, 1.5f);
```

### 3.4 破无敌帧属性

作为**开关**使用，属性总值 > 0 时，破甲伤害同时无视无敌帧。

```java
// scale 填任意正数即可（属性存在即激活）
YizModQZKAPI.registerPierceInvulnerabilityAttribute(MyAttributes.PIERCE_INVUL, 1.0f);
```

### 3.5 查询属性总值

```java
float total = YizModQZKAPI.getDamageAttributeValue(attacker);
```

---

## 4. 直接健康值修改

绕过目标实体的 `hurt()` 方法，直接修改所有 Float 数据通道。

### 4.1 按差值修改

```java
// 治疗 20 点（正数 = 治疗）
YizModQZKAPI.modifyHealth(target, 20.0f);

// 造成 15 点伤害（负数 = 伤害）
YizModQZKAPI.modifyHealth(target, -15.0f);
```

### 4.2 按绝对值设置

```java
// 直接设置生命值为 100
YizModQZKAPI.setHealth(target, 100.0f);
```

---

## 5. 禁疗系统

支持百分比 + 固定值两种禁疗方式，叠加计算：先百分比削减，再减固定值。

### 5.1 直接设置禁疗

```java
// 目标所有治疗削减 50%，每次治疗再减 10 点
YizModQZKAPI.setHealBan(target, 50.0f, 10.0f);

// 仅百分比禁疗（50%）
YizModQZKAPI.setHealBanPercent(target, 50.0f);

// 仅固定值禁疗（每次治疗减 10 点）
YizModQZKAPI.setHealBanFixed(target, 10.0f);
```

### 5.2 禁疗属性绑定

注册原版 Attribute 作为禁疗源，攻击者每次近战攻击自动为目标施加禁疗。

```java
// 注册百分比禁疗属性：每点属性值 × scale = 目标的禁疗百分比
YizModQZKAPI.registerHealBanPercentAttribute(MyAttributes.BAN_HEAL, 10.0f);
// 攻击者有 3 点 BAN_HEAL → 目标 30% 禁疗

// 注册固定值禁疗属性：每点属性值 × scale = 目标每次治疗的削减量
YizModQZKAPI.registerHealBanFixedAttribute(MyAttributes.BAN_FIXED, 5.0f);
// 攻击者有 3 点 BAN_FIXED → 目标每次治疗减 15 点
```

---

## 6. 效果框架

### 6.1 创建效果（代码方式）

继承 `AbstractEffect` 或直接匿名实现：

```java
public class FlameTalent extends AbstractEffect {
    public FlameTalent() {
        super(
            ResourceLocation.parse("mymod:flame_talent"),  // id
            "flame_talent",                                  // translationKey
            "火焰天赋",                                     // displayName
            ParentType.ECHO,                                 // parentType
            10,                                              // level
            Set.of(new EntityPerception()),                  // perceptionModes
            new EntityAttackCondition(),                     // activationCondition
            Rarity.MYTHIC                                    // rarity
        );
        // 构造函数自动注册到 ModRegistries！
    }

    @Override
    public void execute(EffectContext context) {
        // 效果逻辑：点燃目标 3 秒
        context.target().setSecondsOnFire(3);
    }
}
```

在模组初始化时创建实例：

```java
// 静态字段初始化 → 自动调用构造函数 → 自动注册
public static final FlameTalent FLAME_TALENT = new FlameTalent();
```

### 6.2 效果六大维度

| 维度 | 类型 | 说明 |
|------|------|------|
| id | `ResourceLocation` | 全局唯一标识 |
| parentType | `ParentType` | 分类标签：ECHO(残响攻击) / INSCRIPTION(铭刻回复) / MANIFESTATION(显化机制) / ORIGIN(本形防御) / ASCENSION(升灵被动) |
| level | `int` | 等级（1-∞） |
| perceptionModes | `Set<PerceptionMode>` | 感知方式（OR 逻辑，至少一个） |
| activationCondition | `ActivationCondition` | 生效条件 |
| rarity | `Rarity` | 稀有度：MYTHIC / LEGENDARY / EPIC / RARE / COMMON |

### 6.3 感知方式

```java
// 物品绑定 → 词缀 (Affix)
new ItemPerception(ItemPerception.ItemSlot.MAIN_HAND)
new ItemPerception(ItemPerception.ItemSlot.OFF_HAND)
new ItemPerception(ItemPerception.ItemSlot.HEAD, CHEST, LEGS, FEET)
new ItemPerception(ItemPerception.ItemSlot.INVENTORY)

// 实体绑定 → 天赋 (Talent)
new EntityPerception()

// 容器绑定 → 随影 (Shadow)
new ContainerPerception(ContainerPerception.ContainerType.PERSONAL_CONTAINER)

// 自定义感知
CustomPerception.of("我的感知", (entity, context) -> {
    return entity.isInWater(); // 示例：在水中时感知
});
```

### 6.4 生效条件

```java
// 实体攻击时（默认返回 true，可 override）
new EntityAttackCondition();

// 飞行物命中时（默认返回 true，可 override）
new ProjectileHitCondition();

// 常驻生效（始终返回 true）
new PassiveCondition();

// 自定义生效条件
new EntityAttackCondition() {
    @Override
    public boolean shouldActivate(EffectContext context) {
        if (context.target().fireImmune()) return false;
        return true;
    }
};

// 条件组合
condition1.and(condition2);   // AND
condition1.or(condition2);    // OR
condition1.negate();          // NOT
```

### 6.5 解锁管理

```java
// 为实体解锁效果
YizModQZKAPI.unlockEffect(player, effectId);

// 检查是否已解锁
boolean unlocked = YizModQZKAPI.isEffectUnlocked(player, effectId);
```

### 6.6 效果调度

```java
// 手动触发效果调度
EffectContext context = EffectContext.create(attacker, target);
YizModQZKAPI.dispatchContext(context);
```

### 6.7 查询所有注册效果

```java
List<AbstractEffect> all = YizModQZKAPI.getAllEffects();
Optional<AbstractEffect> effect = YizModQZKAPI.getEffect(ResourceLocation.parse("mymod:flame_talent"));
```

---

## 7. 物品效果 NBT

### 7.1 为物品附加效果

```java
ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
YizModQZKAPI.attachEffectToItem(sword, FLAME_TALENT);
// 物品 NBT 中写入 yizmodqzk:effects 数据
```

### 7.2 查询物品效果

```java
List<AbstractEffect> effects = YizModQZKAPI.getItemEffects(sword);
```

### 7.3 JSON 数据驱动

在 `data/{modid}/yizmodqzk/effects/` 目录下放置 JSON 文件，游戏自动加载：

```json
{
  "id": "mymod:ice_affix",
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
  "rarity": "LEGENDARY"
}
```

---

## 8. 天赋查询与 UI

### 8.1 查询实体已解锁天赋

```java
List<AbstractEffect> talents = YizModQZKAPI.getEntityTalents(player);
// 自动按稀有度 → 等级降序排列
```

### 8.2 刷新 UI

```java
// 解锁新天赋或变更效果后调用
YizModQZKAPI.refreshUI();
```

---

## 9. 事件监听

### 9.1 监听伤害事件

通过 NeoForge EventBus 监听 `DamageEvent`，可修改伤害值或取消伤害：

```java
@SubscribeEvent
public void onDamage(DamageEvent event) {
    // 检查伤害类型
    if (event.getType() == DamageType.TRUE) {
        // 修改伤害值
        event.setAmount(event.getAmount() * 0.5f);
    }

    // 取消伤害
    if (event.getTarget().getType() == EntityType.ENDER_DRAGON) {
        event.cancel("boss immune");
    }
}
```

### 9.2 注册监听器

```java
NeoForge.EVENT_BUS.register(new YourEventHandler());
```

---

## 10. JSON 数据驱动

### 10.1 目录规范

```
src/main/resources/
└── data/
    └── {modid}/
        └── yizmodqzk/
            └── effects/
                ├── affix_fire.json       // 词缀
                ├── talent_flame.json     // 天赋
                └── shadow_ancient.json   // 随影
```

### 10.2 JSON Schema

```json
{
  // 基础信息（必填）
  "id": "mymod:fire_affix",
  "display_name": "火焰词缀",

  // 六大维度（必填）
  "parent_type": "ECHO",
  "level": 10,
  "rarity": "MYTHIC",

  // 感知方式（必填，至少一个）
  "perception_modes": [
    { "type": "ITEM",  "slot": "MAIN_HAND" },
    { "type": "ENTITY" },
    { "type": "CONTAINER", "container_type": "PERSONAL_CONTAINER" }
  ],

  // 生效条件（必填）
  "activation_condition": { "type": "ENTITY_ATTACK" }
}
```

`type` 可选值：`ITEM` / `ENTITY` / `CONTAINER`

`activation_condition.type` 可选值：`ENTITY_ATTACK` / `PROJECTILE_HIT` / `PASSIVE`

---

## 11. 完整示例

### 完整模组示例：火焰剑 + 燃烧天赋

```java
@Mod(MyMod.MODID)
public class MyMod {
    public static final String MODID = "mymod";

    // ===== 1. 注册自定义属性 =====

    // 原版 Attribute（在你的 Attribute 注册表中定义）
    public static final Holder<Attribute> FIRE_DAMAGE = ...;
    public static final Holder<Attribute> BAN_HEAL = ...;

    public MyMod() {
        // 注册属性绑定
        YizModQZKAPI.registerDamageAttribute(FIRE_DAMAGE);
        YizModQZKAPI.registerHealBanPercentAttribute(BAN_HEAL, 10.0f);

        // 注册事件监听
        NeoForge.EVENT_BUS.register(this);
    }

    // ===== 2. 创建效果（自动注册） =====

    public static final AbstractEffect FLAME_TALENT = new AbstractEffect(
        ResourceLocation.parse(MODID + ":flame_talent"),
        "flame_talent", "火焰天赋",
        ParentType.ECHO, 10,
        Set.of(new EntityPerception()),
        new EntityAttackCondition(),
        Rarity.MYTHIC
    ) {
        @Override
        public void execute(EffectContext ctx) {
            ctx.target().setSecondsOnFire(5);
        }
    };

    // ===== 3. 监听伤害事件 =====

    @SubscribeEvent
    public void onDamage(DamageEvent event) {
        if (event.getType() == DamageType.FLAT) {
            event.setAmount(event.getAmount() * 1.2f); // 所有固定伤害 +20%
        }
    }

    // ===== 4. 给物品附加效果 =====

    public static ItemStack createFireSword() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        YizModQZKAPI.attachEffectToItem(sword, FLAME_TALENT);
        return sword;
    }

    // ===== 5. 解锁天赋 =====

    public static void unlockForPlayer(ServerPlayer player) {
        YizModQZKAPI.unlockEffect(player, FLAME_TALENT.getId());
        YizModQZKAPI.refreshUI();
    }

    // ===== 6. 使用伤害 API =====

    public static void attackBoss(LivingEntity boss, LivingEntity player) {
        // 真实伤害 50 点（无视一切防御）
        YizModQZKAPI.trueDamage(boss, 50.0f, player);

        // 施加 30% 禁疗
        YizModQZKAPI.setHealBanPercent(boss, 30.0f);
    }

    // ===== 7. 查询实体的天赋 =====

    public static void checkTalents(ServerPlayer player) {
        List<AbstractEffect> talents = YizModQZKAPI.getEntityTalents(player);
        for (AbstractEffect talent : talents) {
            player.sendSystemMessage(
                Component.literal("已解锁天赋: " + talent.getDisplayName())
            );
        }
    }
}
```

---

## API 方法速查表

| 类别 | 方法 | 说明 |
|------|------|------|
| **伤害** | `damage(target, amount, source)` | 固定数值伤害 |
| | `percentDamage(target, percent, source)` | 百分比伤害 |
| | `trueDamage(target, amount, source)` | 真实伤害（无视一切） |
| | `armorPiercingDamage(target, amount, source)` | 破甲伤害 |
| | `pierceInvulnerabilityDamage(target, amount, source)` | 破无敌帧 |
| | `armorPiercingAndPierceInvulnerabilityDamage(target, amount, source)` | 破甲+破无敌帧 |
| **属性绑定** | `registerDamageAttribute(holder)` | 注册伤害属性 |
| | `getDamageAttributeValue(attacker)` | 查询伤害属性总值 |
| | `registerTrueDamageAttribute(holder, scale)` | 注册真实伤害属性 |
| | `registerArmorPiercingAttribute(holder, scale)` | 注册破甲伤害属性 |
| | `registerPierceInvulnerabilityAttribute(holder, scale)` | 注册破无敌帧属性 |
| **健康值** | `modifyHealth(target, delta)` | 直接增减健康值 |
| | `setHealth(target, health)` | 直接设置健康值 |
| **禁疗** | `setHealBan(entity, percent, fixed)` | 设置禁疗（百分比+固定值） |
| | `setHealBanPercent(entity, percent)` | 百分比禁疗 |
| | `setHealBanFixed(entity, fixed)` | 固定值禁疗 |
| | `registerHealBanPercentAttribute(holder, scale)` | 注册百分比禁疗属性 |
| | `registerHealBanFixedAttribute(holder, scale)` | 注册固定值禁疗属性 |
| **效果** | `registerEffect(effect)` | 注册效果 |
| | `getEffect(id)` | 按 ID 查询效果 |
| | `getAllEffects()` | 获取所有注册效果 |
| | `unlockEffect(entity, effectId)` | 为实体解锁效果 |
| | `isEffectUnlocked(entity, effectId)` | 检查解锁状态 |
| | `dispatchContext(context)` | 调度效果上下文 |
| | `getEntityTalents(entity)` | 获取实体已解锁天赋 |
| **物品** | `attachEffectToItem(stack, effect)` | 为物品附加效果 |
| | `getItemEffects(stack)` | 获取物品上的所有效果 |
| **UI** | `refreshUI()` | 刷新 UI |

---

## FAQ

### 这个前置库可以被 AI（如 Claude）正确理解和使用吗？

**可以**。理由如下：

1. **单入口 API 类**：所有公开功能集中在 `YizModQZKAPI` 一个类中，全部为静态方法，AI 无需实例化即可调用。
2. **清晰的命名**：方法名自文档化 — `trueDamage()`、`armorPiercingDamage()`、`setHealBan()`、`unlockEffect()`。
3. **详细的 Javadoc**：每个方法都有中英文参数说明和示例。
4. **标准的 AbstractEffect 模式**：效果的六大维度集中在构造函数参数中，AI 能直接看出如何创建一个完整的效果。
5. **确定性 API 模式**：所有方法不依赖隐藏状态或复杂的 builder 模式，直接传参即可。

AI 在接入时：
- 看到 `YizModQZKAPI.damage(target, amount, source)` 就知道"对目标造成伤害"
- 看到 `AbstractEffect` 构造函数的 8 个参数就知道"这是创建效果的标准入口"
- 看到 `Set.of(new EntityPerception())` 就知道"这是天赋类型"
- 看到 `EntityAttackCondition` 就知道"攻击时触发"

### 如果我想将本前置库嵌入到一个更大的项目中使用？

方式一：**Git 子模块**

```bash
git submodule add https://github.com/你的仓库/yiz-qzk.git libraries/yiz-qzk
```

在父项目的 `settings.gradle` 中包含：
```gradle
include 'libraries:yiz-qzk'
```

方式二：**独立发布 + Maven 依赖**（推荐，解耦更彻底）

```bash
cd yiz-qzk
gradle publishToMavenLocal
```

然后在下游项目中声明依赖（见 [1.2 下游项目引入依赖](#12-下游项目引入依赖)）。

### 为什么所有方法都是静态的？不使用实例注入？

本前置库的设计定位是**工具类式前置库**，下游模组在任何地方都可以直接调用 `YizModQZKAPI.xxx()`，无需：
- 获取实例或持有引用
- 通过依赖注入获取 API 对象
- 在构造器中传入 API 实例

这种模式的最简接入成本，对下游模组开发者最友好。

### 本前置库与其他模组的兼容性如何？

- **伤害系统**：绕过目标实体的自定义 `hurt()`，不影响其他模组的伤害监听
- **禁疗系统**：通过 ASM Agent + Mixin + CoreMod 三层保底，对第三方模组的治疗行为生效
- **Attribute 绑定**：使用原版 `Holder<Attribute>`，与所有模组的属性系统兼容
- **效果框架**：通过 `ModRegistries` 独立注册，不冲突
