# YizMod QZK — CLAUDE.md

NeoForge 1.21.1 **库模组 (Library Mod)**，MODID=`yizmodqzk`，为下游模组提供伤害/效果/健康修改/武器/天赋框架。

## 构建命令

```bash
./gradlew build                          # 构建主 JAR（含嵌入的 Agent JAR）
./gradlew publishToMavenLocal            # 发布到 ~/.m2/repository
./gradlew publish                        # 发布到项目内 repo/ 目录
./gradlew packageLib                     # 生成 build/distributions/ 分发包 zip
```

## 架构总览

```
src/main/java/net/minecraft/client/yiz/
├── tizMod.java              # 服务端入口 @Mod
├── tizModClient.java        # 客户端入口 @Mod(Dist.CLIENT)
├── api/                     # 公开 API — 下游模组唯一入口 (YizModQZKAPI)
├── effect/                  # ① 效果框架（6维系统）
│   ├── AbstractEffect.java        # 基类：构造函数自动注册到 ModRegistries
│   ├── EffectContext.java         # 效果执行上下文
│   ├── parent/ParentType.java     # 5大父类枚举
│   ├── perception/                # 感知方式（Item/Entity/Container/Custom）
│   ├── activation/                # 生效条件（EntityAttack/ProjectileHit/Passive）
│   ├── unlock/                    # 解锁管理器
│   └── rarity/Rarity.java         # 稀有度枚举
├── core/                    # ② 核心基础设施
│   ├── registry/ModRegistries.java    # 全局效果注册表
│   ├── event/EffectEventBus.java     # 效果事件分发
│   ├── data/EffectDataLoader.java    # JSON 数据驱动加载
│   ├── AttackTargetLock.java         # 攻击目标锁定（Agent 级，不被 Mixin 覆盖）
│   ├── FantasyEndingPlugin.java      # Mixin 插件
│   └── asm/                          # ASM Agent 引导（AgentLoaderProcess/AsmBootstrapper）
├── tool/damage/              # ③ 伤害系统（14种伤害类型，3种强制标签）
├── tool/health/              # ④ 健康修改系统（优先级管道）
├── tool/attribute/           # ⑤ 属性修改辅助 + 物品属性读写 (ItemAttributeHandler)
├── tool/SimpleCommandRegistry.java  # ⑥ 简易指令注册器
├── weapon/                   # ⑦ 武器系统
├── talent/                   # ⑧ 天赋系统
├── ui/                       # ⑨ 统一UI面板（所有下游模组的效果自动汇聚于此）
├── attribute/                # ⑩ 属性计算（ModifierStack多乘区引擎）
├── bridge/                   # ⑪ 数据桥接（NBT序列化）
└── mixin/                    # ⑫ Mixin（仅3个：Player/LivingEntity/AttackInterceptor）

agent/src/                    # 独立 Java Agent（ASM字节码改写）
└── net/minecraft/client/yiz/agent/
    ├── HealthAgent.java              # Premain-Class，attach 时注入 Transformer
    └── LivingHealthTransformer.java  # ClassFileTransformer
```

## 关键设计决策

### API 入口：全静态方法 Facade

`YizModQZKAPI` 是所有功能的唯一入口，全部 static 方法。下游模组不需要注入或持有引用。直接调用 `YizModQZKAPI.damage(...)`, `YizModQZKAPI.trueDamage(...)` 等。

### 统一 UI 面板（核心设计意图）

本库不仅是 API 框架，还提供**统一的用户体验入口**。所有下游模组注册的效果自动汇聚到库提供的面板中，无需各模组自行实现 UI：

- **`PlayerTalentUI`** — 在生存背包界面右侧展示玩家所有已解锁天赋。从 `ModRegistries` 读取全部已注册效果，过滤 `EntityPerception` 类型，交叉 `UnlockManager` 状态。下游模组只负责注册效果和解锁，UI 自动汇聚。
- **`ItemInfoUI`** — 替代原版物品悬浮提示，展示物品的所有 6 维效果（稀有度、父类型、等级、感知方式、生效条件、具体效果）。从 `ModRegistries` + 物品 NBT 自动读取。

设计类比：JEI 提供统一的物品列表界面，本库提供统一的天赋/词缀/随影展示面。删掉这些面板，下游模组就需要各自做 UI，体验割裂。

### 效果框架 6 维度

效果 = id + parentType(5种) + level + perceptionModes(OR逻辑) + activationCondition + rarity(5级) + execute()。

`AbstractEffect` 构造函数自动调用 `ModRegistries.registerEffect(this)`，无需手动注册。支持代码创建和 JSON 数据驱动两种方式。JSON 目录：`data/{modid}/yizmodqzk/effects/*.json`。

### 健康修改系统（三层管道）

```
Delta 系统 → ChannelScanner → DirectHealthFallback
```

绕过目标实体的 `hurt()` 方法，直接修改所有 Float 数据通道。通过 Java Agent (ASM) 在运行时注入字节码，拦截 `LivingEntity.hurt()` 等关键方法。

### Java Agent 机制

- Agent JAR 独立编译（`compileAgent` → `agentJar`），嵌入主 JAR 的 `/META-INF/jarjar/yizmodqzk-agent.jar`
- `Premain-Class` / `Agent-Class`: `net.minecraft.client.yiz.agent.HealthAgent`
- 开发环境通过 `yizmodqzk.agent.jar` 系统属性定位 Agent JAR
- Agent 依赖 ASM 9.8，通过 `agentLibs` 配置解压进 Agent JAR

### Mixin 策略：薄层

仅 3 个 Mixin：
- `PlayerMixin` / `LivingEntityMixin` — 生命值/伤害拦截
- `AttackInterceptorMixin` — 强制伤害执行

重逻辑在 Agent 层和事件系统中，Mixin 只做最小量的钩子注入。

### NBT 键名约定

- 物品效果：`yizmodqzk:effects` (ListTag, 结构 `{id, level, unlocked}`)
- 实体天赋：`yizmodqzk:talents` (CompoundTag → unlocked ListTag)
- 物品自定义属性：`yizmodqzk:item_stats` (CustomData, 结构 `{damage_amplification, damage_reduction, sweep_decay}`)

### 禁疗系统

叠加模型：先百分比削减，再减固定值。通过 ASM Agent + Mixin 三层保底拦截治疗。

### 攻击目标锁定

`AttackTargetLock` 在 `AttackInterceptorMixin` 的 `attack()` HEAD 处（最早时机）捕获原始 target，存入 `ConcurrentHashMap<UUID, Entity>`。下游通过 `YizModQZKAPI.getOriginalAttackTarget(player)` 取回"玩家真正想攻击的实体"——不受其他模组 Mixin 取消/偷换的影响。

### 物品属性修改（7 属性 × 3 操作）

`ItemAttributeHandler` 提供对 ItemStack 的 7 种属性读/写：

| # | 属性 | 存储位置 | API 方法前缀 |
|---|------|---------|-------------|
| 1 | 攻击力 | `ATTRIBUTE_MODIFIERS[ATTACK_DAMAGE]` | `get/set/addAttackDamage` |
| 2 | 攻击速度 | `ATTRIBUTE_MODIFIERS[ATTACK_SPEED]` | `get/set/addAttackSpeed` |
| 3 | 交互距离 | `ATTRIBUTE_MODIFIERS[entity_interaction_range]` | `get/set/addInteractionRange` |
| 4 | 横扫范围 | `ATTRIBUTE_MODIFIERS[sweeping_damage_ratio]` | `get/set/addSweepRatio` |
| 4b | 横扫衰减 | `yizmodqzk:item_stats.sweep_decay` (CustomData) | `is/setSweepDecay` |
| 5 | 耐久值 | `DataComponents.MAX_DAMAGE` | `get/set/addMaxDurability` |
| 6 | %伤害增幅 | `yizmodqzk:item_stats.damage_amplification` (CustomData) | `get/set/addDamageAmplification` |
| 7 | %伤害减免 | `yizmodqzk:item_stats.damage_reduction` (CustomData) | `get/set/addDamageReduction` |

- `set` 覆盖原值，`add` 在原值上累加
- 属性 1-5 通过原版 `DataComponents.ATTRIBUTE_MODIFIERS` 读写，`set` 移除旧 modifier 后写入新 `ADD_VALUE` modifier（UUID 基于 MODID+属性名 hash 稳定生成）
- 属性 6-7 通过 `DataComponents.CUSTOM_DATA` NBT 存储，在 `DefaultDamageCalculator` 中自动汇总攻击者主手+副手的 %增幅/%减免，传入 `ModifierStack` 乘法区参与伤害计算
- `ItemInfoUI` 自动显示全部 7 种属性（从 `ItemAttributeDisplay.getAvailableAttributes()` 读取）

### 简易指令注册

下游模组无需自行订阅 `RegisterCommandsEvent`，通过 API 一行提交指令：

```java
// 完整 builder（子命令、参数等）
YizModQZKAPI.registerCommand(Commands.literal("test").then(...));

// 最简：无参字面指令
YizModQZKAPI.registerSimpleCommand("heal", ctx -> { ... });
```

`SimpleCommandRegistry` 持有 pending 列表，在 `RegisterCommandsEvent` 时统一注册。调用时机不限。

## 开发环境

- Java 21，Parchment 映射 (`2024.11.17`)
- 运行配置需要 `--add-modules=jdk.attach` 和 `--add-opens=jdk.attach/sun.tools.attach=...`
- Gradle 配置了并行构建和缓存 (`org.gradle.parallel=true`, `org.gradle.caching=true`)

## 注意事项

- 包名 `net.minecraft.client.yiz` 虽含 `client`，但包含服务端代码 — 这是历史命名
- `tizMod` (不是 `YizMod`) 是服务端入口类名，不要"修正"它
- Effect 的 perceptionModes 是 OR 逻辑（满足任一即可），不是 AND
- `EntityPerception` = 天赋 (Talent)，`ItemPerception` = 词缀 (Affix)，`ContainerPerception` = 随影 (Shadow)
- 效果排序规则：稀有度降序 → 等级降序（Rarity ordinal 越小越稀有）
- **`PlayerTalentUI` 和 `ItemInfoUI` 是库的核心交付物，不是测试残留** — 它们是所有下游模组效果的统一展示入口，删掉会导致下游模组各自为政
- 1.21.1 中 `ItemStack.getOrCreateTag()` 已移除，自定义 NBT 必须走 `DataComponents.CUSTOM_DATA` + `CustomData` 组件
- 物品属性 `set` 使用固定 UUID（基于 `yizmodqzk:<属性名>` hash），多次 set 不会产生重复 modifier
- `ENTITY_INTERACTION_RANGE` 和 `SWEEPING_DAMAGE_RATIO` 通过 `BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation)` 查找，不要硬编码字段名
- `SimpleCommandRegistry` 调用 `init()` 后生效，下游注册指令无需 import `@SubscribeEvent` 或 `RegisterCommandsEvent`
