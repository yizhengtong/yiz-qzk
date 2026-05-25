# YizMod QZK — CLAUDE.md

NeoForge 1.21.1 **库模组 (Library Mod)**，MODID=`yizmodqzk`，为下游模组提供伤害/效果/健康修改框架。

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
├── ui/                       # ⑦ 统一UI面板（所有下游模组的效果自动汇聚于此）
├── attribute/                # ⑧ 属性计算（ModifierStack多乘区引擎）
├── bridge/                   # ⑨ 数据桥接（NBT序列化）
└── mixin/                    # 	└── mixin/                    # ⑩ Mixin（5个：Player/LivingEntity/AttackInterceptor/FlightOptimization/NoCollision）

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
  - **扩展点**：`AbstractEffect.getTalentDetailLines(entity)` — 效果子类可重写此方法返回额外详情行（支持 § 颜色代码），在天赋面板中自动渲染。
  - **实时刷新**：每帧重建天赋列表（无缓存），保证动态数据（如星光层数）实时反映在面板上。
  - **自适应裁剪**：窗口缩小时自动跳过无法完整显示的天赋行，避免文字溢出。
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
- **Agent 引导时机**：`FantasyEndingPlugin.onLoad()` 不再调用 `AsmBootstrapper.start()`，改为在 `tizMod` 构造器中延迟加载。避免在 Mixin 准备阶段触发 `LivingEntity` 过早加载，防止与 geckolib 等模组的 `MixinTargetAlreadyLoadedException` 冲突。
- **CoreMod JS 已禁用**：`coremods.json` 置空（`{}`），`yizmodqzk_healban.js` 不再加载。功能由 Mixin + ASM Agent 覆盖。

### Mixin 策略：薄层

5 个 Mixin：
- `PlayerMixin` / `LivingEntityMixin` — 生命值/伤害拦截
- `AttackInterceptorMixin` — 强制伤害执行
- `FlightOptimizationMixin` — 飞行惯性优化
- `NoCollisionMixin` — 碰撞免疫（推进和碰撞检测）

重逻辑在 Agent 层和事件系统中，Mixin 只做最小量的钩子注入。
`FlightOptimizationMixin` 和 `NoCollisionMixin` 需在 `mixins.json` 中注册（`src/main/resources/yizmodqzk.mixins.json` 为 5 mixin 完整版，根目录旧版仅 3 个）。

### NBT 键名约定

- 物品效果：`yizmodqzk:effects` (ListTag, 结构 `{id, level, unlocked}`)
- 实体天赋：`yizmodqzk:talents` (CompoundTag → unlocked ListTag)
- 物品自定义属性：`yizmodqzk:item_stats` (CustomData, 结构 `{damage_amplification, damage_reduction, sweep_decay}`)

### 禁疗系统

叠加模型：先百分比削减，再减固定值。通过 ASM Agent + Mixin 三层保底拦截治疗。

### 攻击目标锁定

`AttackTargetLock` 在 `AttackInterceptorMixin` 的 `attack()` HEAD 处捕获原始 target，存入 `ConcurrentHashMap<UUID, Entity>`。下游通过 `YizModQZKAPI.getOriginalAttackTarget(player)` 取回"玩家真正想攻击的实体"。

### Unsafe 实例级保护态

通过 `sun.misc.Unsafe` 替换单个 Player 实例的 class 指针 → `ProtectedServerPlayer`。**5 层防御**：

| 层 | 位置 | 机制 |
|---|------|------|
| Agent | `LivingEntity.setHealth()` 入口 | `health = max(1, NaN?1:health)` |
| Agent | `LivingEntity.die()` 入口 | `if protected → return` |
| Agent | `Entity.remove()` 入口 | `if protected → return` |
| Unsafe | `ProtectedServerPlayer` | 覆盖 `hurt/die/kill/remove/tick/setHealth` |
| Mixin | `LivingEntityMixin.die()` HEAD | `ci.cancel()` 最终兜底 |

API：
```java
YizModQZKAPI.enableProtection(player)   // 开启
YizModQZKAPI.disableProtection(player)  // 关闭
YizModQZKAPI.isProtected(player)        // 查询
```

### 注册表 API（供下游模组使用）

包路径 `net.minecraft.client.yiz.api`，全部为静态方法注册，下游在模组构造器中调用。

| 注册表 | 注册接口 | 作用 |
|--------|---------|------|
| `PlayerDataAPI` | `register(key, codec, default)` | 注册持久化键值对；`get/set/discard` 读写 |
| `DamageReductionRegistry` | `register(BiFunction<LivingEntity,Float,Float>)` | 伤害减免钩子，在 `setHealth()` 写入前拦截修改 |
| `CounterAttackRegistry` | `register(BiPredicate<Player,DamageSource>)` | 回击触发条件，命中时反弹伤害 |
| `UndyingRegistry` | `register(BiPredicate<LivingEntity,DamageSource>)` | 自定义复活（不死图腾路径），返回 true 则触发复活 |
| `ProjectileReflectionSystem` | `register(Predicate<LivingEntity>)` | 投射物返还，命中时反弹回攻击者 |
| `ProjectileImmunityRegistry` | `register(Predicate<LivingEntity>)` | 投射物免疫，返回 true 则该实体对投射物免疫 |
| `NoCollisionRegistry` | `register(Predicate<LivingEntity>)` | 碰撞免疫，返回 true 则实体穿过其他实体/推动 |
| `KnockbackImmunityRegistry` | `register(Predicate<LivingEntity>)` | 击退霸体，返回 true 则免疫击退 |
| `FlightOptimizationRegistry` | `register(Predicate<LivingEntity>)` | 飞行惯性优化条件 |
| `FlightAbilityRegistry` | `register(Predicate<LivingEntity>)` | 权限飞行权，返回 true 则强制允许飞行 |
| `DamageAttributeRegistry` | `register(Holder<Attribute>, float)` | 把属性值转为额外伤害（乘以缩放系数） |
| `AttributeBalanceRegistry` | `enableFor(LivingEntity)` | 启用属性正负锁定（每 tick 恢复属性底线） |

所有注册表使用 `CopyOnWriteArrayList` 保证并发安全，支持多模组同时注册。
下游在 `yizxgMod` 构造器中调用这些 `register()`，运行时由 Mixin/Agent 遍历列表执行。

### 简易指令注册

下游通过 API 一行提交指令，无需自行订阅 `RegisterCommandsEvent`：
```java
YizModQZKAPI.registerCommand(Commands.literal("test").then(...));
YizModQZKAPI.registerSimpleCommand("heal", ctx -> { ... });
```

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
- **数据自动同步**：`PlayerDataAPI.set()` 每次写入时自动触发 `SyncPlayerDataPayload` 网络包从服务端推送到客户端，下游模组无需手动实现同步。同步由 `NetworkHandler.registerPlayerDataSync()` 在 `tizMod` 初始化时注入 `PlayerDataAPI.onDataSet` 回调完成。
- **解锁数据持久化**：`UnlockManager` 的解锁状态通过 `UnlockSavedData` 保存到世界存档（`DimensionDataStorage`），在 `LevelEvent.Load` 时加载、`LevelEvent.Save` 时写入。`UnlockManager.unlock()` 自动标记 `setDirty()` 触发存档写入。
- 1.21.1 中 `ItemStack.getOrCreateTag()` 已移除，自定义 NBT 必须走 `DataComponents.CUSTOM_DATA` + `CustomData` 组件
- 物品属性 `set` 使用固定 UUID（基于 `yizmodqzk:<属性名>` hash），多次 set 不会产生重复 modifier
- `ENTITY_INTERACTION_RANGE` 和 `SWEEPING_DAMAGE_RATIO` 通过 `BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation)` 查找，不要硬编码字段名
- `SimpleCommandRegistry` 调用 `init()` 后生效，下游注册指令无需 import `@SubscribeEvent` 或 `RegisterCommandsEvent`

## AI 快速入口

- **完整架构蓝图**（整体框架）：`http://localhost:8080/architecture/blueprint/` 或 `D:\ZM\yizqzk-docs\docs\architecture\blueprint.md`
- **结构化 API 知识库**（AI 直接读取）：`D:\ZM\yizqzk-docs\docs\llm\knowledge.json`
- **本地文档网站**：`http://localhost:8080`（需运行 `serve.bat` 或 `mkdocs serve`）
- **更新文档站**：在会话中调用 `/YIZwikl` Skill 或直接运行 `serve.bat`
- **下游模组模板**：`D:\ZM\yizxgmod-template-1.21.1`

### 创造标签页自动注册（4 母页系统）

4 个标记接口 + 自动扫描 `BuiltInRegistries.ITEM`，按 (modId, 类别) 分组，为非空分组自动创建 `CreativeModeTab`。

| 母页 | 接口 | 标签页 ID 格式 |
|------|------|---------------|
| A 天赋页 | `ITalentItem` | `{modid}:talent` |
| B 技能页 | `ISkillItem` | `{modid}:skill` |
| C 物品页 | `IGeneralItem` | `{modid}:item` |
| D 武器装备页 | `IWeaponItem` | `{modid}:weapon` |

- 标签页标题格式：`{模组显示名}-天赋/技能/物品/武器装备`
- 空类别不注册标签页
- 下游模组只需让 Item 实现对应接口，无需手动注册 `CreativeModeTab`
- 入口：`CreativeTabAutoRegistry.init(modEventBus)`（在 `tizMod` 构造器中调用）
- API：`YizModQZKAPI.getCreativeTabCategory(item)` / `isCreativeTabRegistered(item)`

## 当前开发专注区域

**D. 天赋部分** ✅ — 效果框架 6 维度、12 种注册表、统一 UI 面板已实现。
**E. 创造标签页** ✅ — 4 母页系统 + 4 接口 API，自动注册。
A. 境界跨度 / B. 领域 / C. 道宫 为架构设计阶段，尚未编码。
详见 [整体蓝图](../architecture/blueprint.md)。
