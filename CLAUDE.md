# YizMod QZK — CLAUDE.md

NeoForge 1.21.1 **库模组 (Library Mod)**，MODID=`yizmodqzk`，为下游模组提供伤害/效果/健康修改框架。

## 协作守则（用户明确要求遵守）

### 1. 先验证再相信

用户描述的是**症状或猜测**，文件里的代码才是**事实**。两者经常不一致。

- 收到 bug 报告或修改请求，**先把用户的描述当作"待验证的假设"**，不要当作"既定事实直接动手"
- 动手改之前，至少完成：读相关文件、grep 调用方、确认改动不会破其他路径
- 用户说"是不是 X"——按这是问题来验证，验证完再回话；不要直接当结论执行
- 用户说"删掉 Y"——先查谁在引用 Y。如果有引用，先指出再问要不要一起改

反模式：用户说"修 A"，AI 直接改 A 不问 A 是不是真的根因；用户给一个推测，AI 直接按推测改代码。

### 2. 捕捉描述里的灰色地带，用 AskUserQuestion 反问

用户的话几乎总有歧义。AI 默认填进去的解释经常错，与其赌不如反问。**但反问要给带描述的选项卡，不要空白提问**。

值得反问的信号：
- 代词/泛指（"这里"、"那个"、"差不多"），且上下文有 ≥2 候选
- "一定要" 和 "大概" 共存，硬要求和软要求边界不清
- 用户的两个目标有冲突但他没意识到——主动指出冲突让他选

反问形式：`AskUserQuestion` + 选项卡，每个选项带一句话描述，让用户点选不打字。

反模式："您具体是什么意思呢？"这种空白问题；猜一个解释直接做。

### 3. 不陷入修补循环

同一思路失败 2 次以上，停。退到上一层重新分析根因，而不是在症状层继续微调。第三次还是同一思路 = 90% 还会失败。

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

## WindowMapper / 手持面板模块（QQ 等外部窗口投影）

`net.minecraft.client.yiz.client.render.*` + `windowmapper.*` + native `WindowCapture.dll`（DXGI Desktop Duplication 抓帧 + InputInjector 注入鼠标键盘）。

### 架构核心：PanelLifecycle 4 状态机

每个 `Panel` 持有一个 `PanelLifecycle`，是"是否抓帧/渲染/转发"的唯一权威源：

- **LIVE**：MC 正常运行，玩家可控（`mc.player/level != null && screen == null && !isPaused && window focused`）
- **DORMANT**：MC 暂停 / 打开 GUI / 失焦 — 保留资源，停止一切 native I/O（captureFrame 在 native 端直接 return null）
- **DEAD**：HWND 失效或主动移除 — 触发资源清理
- DETACHED 是初始态，实际 panel 创建后立即进入 LIVE

### 必守规则

- **状态判断只走 lifecycle**：所有 PanelInteractionManager 的 mouse/key/scroll 事件路径开头查 `lc.canForward()`/`canReceiveKey()`，渲染路径查 `lc.canCapture()`。不要在多处复制 `mc.screen != null` 判断
- **renderOne 入口的双重 gate 不可去掉**：`!mcReady || !canCapture()` 必须在 capture+update 之前。ESC 事件可能发生在 client tick 之间，单靠 lifecycle 状态会有一帧滞后窗口
- **LIVE → DORMANT/DEAD 转换必须 flush 已按下的输入**：通过 `sendKeyEventForce`/`sendMouseButtonForce` JNI（绕过 paused gate）补发 release，否则 QQ 会卡键
- **不要每帧 sendMouseMove**：MC 准星位置就是"光标位置"的天然映射，hover 反馈会在每帧 native 调用上无意义放大崩溃面。仅在点击/滚轮发生那一刻调用 `syncCursorToHit` 一次
- **Native/Java buffer 边界要双向校验**：native 端 `capture(buf, maxBytes, ...)` 拒绝超出 maxBytes，Java 侧 `WindowTexture.update` 校验 `buffer.remaining() >= W*H*4`，OpenGL 越界读会让驱动崩溃且 hs_err 落在 `nglTexSubImage2D` 难以定位
- **captureFrame 与 getSize 不是原子的**：renderOne 必须从 ByteBuffer.capacity 推 W*H*4 校验，不能直接信 getSize 返回的尺寸

### 部署流程

mod 通过本地 maven 发布到 `repo/`，下游 `yizxian1.21.1` 通过 `implementation "net.minecraft.client.yiz:yizmodqzk:1.0.0"` 依赖：

```bash
# 重建 native + jar 后部署：
cd native/windowcapture && ./build.bat                            # → WindowCapture.dll
cd ../.. && ./gradlew --offline jar                                # → build/libs/yizmodqzk-1.0.0.jar
cp build/libs/yizmodqzk-1.0.0.jar repo/net/minecraft/client/yiz/yizmodqzk/1.0.0/
cp native/windowcapture/WindowCapture.dll ../yizxian1.21.1/run/    # native dll 跑游戏时从 run/ 加载
```

`./gradlew publish` 在该工程上有 URL 解析 bug（`file://D:\...` 路径），用 cp 替代。

---

## Unsafe klass swap 的 GC 安全前提

`PlayerClassSwapper` 用 Unsafe 改对象头里的 klass 指针——但**只在源类和目标类有完全相同的字段布局时安全**。原因：

GC 工作线程（G1 Conc）扫堆时按对象头里的 klass 推断该对象占多少字节、有哪些引用字段。klass 一换，GC 按"新 klass 的布局"解析这个对象。如果新 klass 字段大小与原 klass 不同（对象实际占用与 GC 算出的不同），下一个对象的位置就错位 → `EXCEPTION_ACCESS_VIOLATION` 在 G1 Conc 线程崩溃，hs_err 里只有 jvm.dll native frame，没有 Java 栈。

**已踩坑案例**：尝试把 `InfinitySwordItem`（继承 Item，自己加了字段）的单例 klass 换到 `Item.class`，希望禁用后所有 virtual dispatch 走基类。结果几十秒后 G1 Conc 必崩。`ItemKlassSwapper.swapToBase` 因此**不要再调用**——文件作为反面教材保留在 `core/`。

`PlayerClassSwapper` 能用是因为 `ProtectedServerPlayer` 是为此专门设计的同结构子类（继承 ServerPlayer 但不加字段）。

**禁用 mod 物品的右键效果**：正确路径是事件层拦截（监听 `PlayerInteractEvent.RightClickItem` 等，按 AbolitionStateManager 状态 cancel），不是改 klass。VTableReplace 反射+vtable index 那条路对走 Item.use 的子类 override 有效，对走事件回调或 Mixin 的 mod 无效。
