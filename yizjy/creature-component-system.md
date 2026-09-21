---
name: creature-component-system
description: "生物配置组件化（对标 1.21 DataComponent 自建）：ComponentType/ComponentMap/ComponentPatch 三层容器 + InstanceEffectState 重构为通用容器。含四个已拍板决策、P1/P2 落地状态与后续分期"
metadata:
  type: project
---

# 生物配置组件化（Creature Component System）

## 用户已拍板的四个决策（2026-09-21）
1. 现有设施：**重构 `InstanceEffectState` 为通用组件容器**（不是外层包一层）。
2. 配置源：**双轨** —— 代码注册给默认值 + 数据包 JSON 覆盖/追加。
3. 粒度：**双轨** —— 代码中类级继承（原型）+ 配置可覆盖/添加（实例 patch）。
4. 范围：属性 + 生物效果 + **战斗参数**（攻击间隔 / 攻击范围 / 技能）。

计划全文：会话 `plan/creature-component-system.md`（含分批、风险、验收）。

## 前置事实
- **1.21 的 DataComponent 是 `ItemStack` 机制，实体用的是 `SynchedEntityData`**；1.20.1 更是没有 DataComponent（1.20.5 才引入）。所以这是**借鉴架构思想自建**，不是调原版 API。
- 项目里已有同样思路的先例：`ISkillItem` 注释「无 ATTRIBUTE_MODIFIERS → 改用 getAttributeModifiers」、`SkillConfigStorage` 注释「CUSTOM_DATA → ItemStack NBT」。

## P1 已完成：组件容器核心
新包 `yizmodqzk/.../client/yiz/creature`：

- `ComponentType<T>`：组件类型 = id + Codec + 可选缺省值；**相等性只按 id**。
- `ComponentMap`：不可变「类型→值」集合，用作**原型**；`with/without` 返回新实例。
- `ComponentPatch`：`added` + `removed` 增量，`apply(base)` 得最终集合，`merge` 叠加。
- `CreatureComponents`：注册表（id 唯一、保持注册顺序）+ 内置定义 —— 7 个布尔效果组件（clear_immunity / pullback / teleport_immunity / potion_immunity / knockback_immunity / physical_immunity / ride_immunity）+ `ATTRIBUTES`（`Map<ResourceLocation, Double>`）。

## P2 已完成：InstanceEffectState 重构为委托容器
- **对外 API 与 NBT 格式零变更**：`isEffectEnabled` / `setEffect` / `registerBaseEffects` / `isClearImmune` / `isPullback` / `getOwner` / `writeState` / `readState` 签名全部保留，5 个调用点（YizxianMob 16 处、YizEffectCommand 11 处、EntityAttributeEditorItem、EntityASMUtil、C2SEntityToggleEffectPayload）**无需改动**。
- 内部 `Entry` 从 `enabled/disabled: Set<String>` 改为 `volatile ComponentPatch patch`；效果开关 = 补丁里的布尔组件（开 = `with(type, true)`，关 = `without(type)` = removed）。
- **判定顺序不变**：实例补丁（removed → false / added → 值）→ 类级 `BASE_EFFECTS`（仍是 `Set<String>`，按 `type.id().getPath()` 比对）→ 默认 false。用快路径查补丁，避免热路径对象分配。
- **NBT 兼容**：仍写 `yiz_effects{owner, enabled[], disabled[]}`；只序列化效果类组件（非布尔组件如 attributes 不进这个 key）。`remove_immunity` → `clear_immunity` + `pullback` 的老档迁移逻辑保留。
- 新增通用能力供 P3 复用：`patchOf` / `get` / `set` / `mergePatch` / `knownEffectIds()`。
- `YizEffectCommand.KNOWN_EFFECTS` 硬编码数组已删除，改为 `InstanceEffectState.knownEffectIds()` —— **新增效果只需在 CreatureComponents 登记，指令补全自动跟随**。

验证：前置库与下游 `yizxianmod` 均编译通过；前置库已 `publishToMavenLocal`。

## 后续分期
- **P3 已完成**：属性/战斗组件接入。
  - `CombatSpec`（record，字段全可选：attackInterval / attackRange / skillId / skillParams）+ `COMBAT` 组件已注册；未配置的字段由调用方回退实体硬编码常量（渐进迁移，不改手感）。
  - `CreatureProfile`（id + 可选 parent + ComponentMap）+ `CreatureProfileRegistry`：**双表**——`CODE_PROFILES`（代码轨，不被清除）+ `DATA_PROFILES`（数据包轨，重载即重建，同 id 优先）。类级继承走 `bindClass` + 沿继承链查找，原型父链用 `resolvedComponents` 递归合并。
  - `YizxianMob.aiStep` 在 `applyEntityAttributes()` 之后调 `CreatureProfileRegistry.apply(this)`（未注册原型时空操作）。
  - 铁斗士 `getAttackInterval()` / `getAttackRange()` 已改为读 `combatOf(this)`（回退原常量）。
  - **辖界者暂未接入**：它的攻击间隔/范围是三阶段形态化的，简单覆盖会破坏形态差异，需要先设计 `combat.phases[]` 才行。
  - **属性组件只处理 `yizmodqzk:` 命名空间的属性**（它们走 `prot_` 修饰符语义）；vanilla 属性（最大生命/移速等基础值语义）仍由实体自身管理，避免叠加语义混乱。写入后必须同步 `AttributeStandardizer.registerStandard`。
- **P4 已完成**：数据包加载。
  - `CreatureProfileJson`（Codec：entity_type / parent / attributes / effects / combat）→ `CreatureProfile`。
  - `CreatureProfileReloadListener extends SimpleJsonResourceReloadListener`，目录常量 `yiz_creature`；每次重载先 `clearData()`。
  - 事件挂在 `tizMod.onAddReloadListener`（`AddReloadListenerEvent`，`MinecraftForge.EVENT_BUS` 已注册实例）。
  - 已放示例：`yizxianmod/src/main/resources/data/yizxianmod/yiz_creature/tiedoushi.json`（值与代码默认一致，零行为变化；改它再 `/reload` 即可看到覆盖效果）。
- **P5（待做）**：铁斗士 profile 全量迁移（把 `applyEntityAttributes` 里的 `setAttr` 搬进原型，删除硬编码）；辖界者 `combat.phases[]`。

## 形态（Phase）组件：多形态生物的切换管理

用户要求：用 1/2/3 的格式定义每个形态的具体变化，且**每个形态自带「进入下一形态的条件」与「回退条件」**。已落地：

- **`PhaseSpec`**（record）：`index`（形态序号，从 1 起）+ `attributes`（本形态属性）+ `combat`（本形态战斗参数）+ `advance`（进入下一形态的条件）+ `regress`（回退到上一形态的条件）。首形态的 regress、末形态的 advance 留空。
- **`PhaseTrigger`**（record）：`type` + `value` + `holdTicks`。类型有 `health_percent_below` / `health_percent_above` / `ticks_in_phase_above` / `combat_ticks_above`（枚举用 `StringRepresentable`，JSON 里写小写下划线名）。`holdTicks` 表达「条件需持续满足 N tick」，用于回退缓冲，防阈值抖动来回跳。
- **`PHASES` 组件**（`ComponentType<List<PhaseSpec>>`）已注册；`CreatureProfileJson` 支持 `phases` 字段；`CreatureProfileRegistry.phasesOf(entity)` 按 index 升序返回。

### 辖界者接入方式（`QuanshouzheEntity`）
- `applyFormPhase(phase)` 不再写 switch，改为从 `phaseSpec(phase)` 读四个值（`generic.attack_damage` 走 vanilla base；`attack_strength`/`life_steal`/`conduction_cap` 走 `setAttr` 受保护写入）。
- `updateFormPhase()` 改为通用判定：当前形态的 `advance` 成立 → `nextPhase()`；否则 `regress` 成立且满足 `holdTicks` → `prevPhase()`。新增 `phaseEnterTick` 供 `ticks_in_phase_above` 用；`combat_ticks_above` 复用已有 `combatStartTick`。
- `getAttackInterval()` / `getAttackRange()` / `getHeavyAttackRadius()` 改为读当前形态的 `combat`（重击半径放 `skill_params.heavy_radius`），未配回退旧值。
- **`DEFAULT_PHASES`** 内置默认表（= 原硬编码值），组件未配置时零行为变化。
- **关键：`applyEntityAttributes()` 末尾补调 `applyFormPhase(初始形态)`** —— 否则形态 1 的属性只在切形态时才写，数据包改形态 1 的数值不会生效。
- 示例：`yizxianmod/src/main/resources/data/yizxianmod/yiz_creature/quanshouzhe.json`（内容与默认一致）。

## 第二轮迭代（2026-09-21 凌晨）

### 诊断日志总开关
- 新增 `YizDiagnostics`（`tool/` 包）：每个诊断项登记 `id` + 默认开关 + “排查的是什么问题、是否已解决”；**已解决的默认关闭**，出问题按项打开即可。全局总开关可一次静默全部。`log` / `logThrottled`（带限频）两个入口；已有限频逻辑（TAB_JUMP 前20次、hurtLog 前60次等）保留在原地，只把输出改为走开关。
- 已迁移：`[COND-DIAG]`、`[CapD]`、`[QZK-REMOVE]`、`[QZK-DEATH]`、`[QZK-HURT]`（两处）、`[SecureHealthClosure] 表值跳变/大幅扣串`、`[AttributeStandardizer] 检测到外部篡改`。
- `/yiz diag list|on|off|<id> on|off|reset` 运行期开关（`YizDiagCommand`）。
- **新增排查日志的规矩：先在 `YizDiagnostics` 登记一项，不要在业务代码里直接调 logger.warn。**

### 属性组件支持 vanilla 属性
- `CreatureProfileRegistry.apply` 现在两种写入语义：`yizmodqzk:` → `EntityAttributeGate`（prot_ 修饰符）；`minecraft:` → 直接 `setBaseValue`（配置值即最终值，**不再叠难度缩放**）。两者都同步 `AttributeStandardizer.registerStandard`。
- idKey 规则：取 path 最后一段（`generic.max_health` → `max_health`），与现有实体注册的 idKey 对齐。

### 组件变更 → 存量实体刷新
- 新增 `CreatureComponentRefresher`：`refreshAll(server)` 遍历各维度“有配置”的实体重新 `apply`；血量按最大生命比例保留，且**只走治疗方向**（受保护实体扣血方向会被丢）。
- 触发点：`CreatureProfileReloadListener` 完成数据包加载后，通过 `ServerLifecycleHooks.getCurrentServer()` + `server.execute(...)` 切回主线程刷新；另提供 `/yiz creature refresh` 手动触发。

### 技能派发
- `CreatureSkill`（id / `start` / `tick(caster, spec, elapsed)` / `stop` / `defaultInterval`，**必须无状态**）+ `CreatureSkills` 注册表（`register` / `fromSpec`）。
- 铁斗士 skill1 迁为 `TiedoushiEntity.Skill1`（id `yizxianmod:tiedoushi_skill1`），在 `YizxianMod.commonSetup` 注册；实体侧改成 `activeSkill` + `skillElapsed` 驱动，不再写死 `skillPending`。`dealSkillDamage(ratio, spec)` 的半径/衰减/倍率/真伤比例均可由 `skill_params` 覆盖。
- 注意：技能靠 `combat.skill_id` 选中，**如果 JSON 没写 skill_id，技能就不会释放**（已把 tiedoushi.json 补上）。

## 验收时的注意点
- **`/reload` 只影响新生成的实体**：`apply()` 只在实体首个服务端 tick 跑一次，已存在的实例需重新生成（或后续补 `/yiz creature reload` 强制重应用）。
- `data/<ns>/yiz_creature/<path>.json` 的文件名相对路径就是原型 id（`yiz_creature/tiedoushi.json` → `yizxianmod:tiedoushi`）。

## 两个必须记住的坑
1. **`AttributeStandardizer` 会把数据包覆盖的属性当「外部篡改」还原**（每 20 tick 审计）：组件应用属性时必须同步 `registerStandard`，否则配置 20 tick 后失效。
2. **`EntityAttributeGate.isCallerTrusted()` 按调用栈鉴权，数据包加载线程不在信任帧内**：加载阶段只解析、不写实体；实际写入仍走实体首 tick 的受信任栈。

## 第三轮迭代（2026-09-22）：原型的 `effects` 真正生效 + 铁斗士免疫外部动量

### 之前是「配了不生效」（已修）
`CreatureProfileJson.toComponents()` 会把 `effects` 解析成布尔组件，但**没有判定读它**：
`InstanceEffectState.isEffectEnabled` 只看 ①实例补丁 ②类级 `BASE_EFFECTS`（字符串轨道，实际没人注册）
⇒ 原型/数据包里写的 `yizmodqzk:knockback_immunity` 一直是空转，且**静默无提示**。

现在判定顺序（与文档约定一致）：**实例补丁 → 原型（`CreatureProfileRegistry.profileEffect`，含父链合并）→ 类级 BASE_EFFECTS → false**。
- `profileEffect()` 是热路径安全实现（类→原型 id 查表 + 已合并组件集合查表 + 组件读，不合并/不分配）；
  **不能用 `componentsOf()`**——它会应用实例补丁并新建集合，而这个判定在 `setDeltaMovement`/`knockback` 上每 tick 被调。
- `toComponents()` 遇到未注册的效果 id 现在打 WARN 点名（原来静默 continue，「名字写错/漏命名空间」查不出来）。

### 效果 → 执行点对照（都在下游 `YizxianMob`，前置库只给开关）
| 效果 id | 执行点 |
| --- | --- |
| `knockback_immunity` | `motionGate()` 盖 `setDeltaMovement(double×3)` / `setDeltaMovement(Vec3)` / `addDeltaMovement` / `knockback` 四个速度入口（`ExternalCallGuard` 调用栈鉴权：只拦外部模组帧，原版/Forge/AI 帧放行 ⇒ 原版 hurt 击退照常；爆炸按既有设计算外部强制力、被拒） |
| `teleport_immunity` | `isAllowedPositionChange()` 盖 `setPos/moveTo/teleportTo/randomTeleport` 等 + 字段级位置恢复 |
| `physical_immunity` | 蜘蛛网等卡方块无效 / 流体减速改 1.0 / 其它物理推动 |
| `potion_immunity` | 负面状态免疫（另有静态总开关 `YizxianMob.setPotionImmunity`） |
| `ride_immunity` | `startRiding` 直接 false |
| `clear_immunity` / `pullback` | 存在性保护（`EntityASMUtil` / 守卫与自愈路径） |

### 铁斗士接入（两轨都写，务必保持同步）
- **代码轨**：`YizxianMod.commonSetup` → `registerTiedoushiProfile()`：
  `CreatureProfile.builder("yizxianmod","tiedoushi").component(KNOCKBACK_IMMUNITY, TRUE)` +
  `CreatureProfileRegistry.bindClass(TiedoushiEntity.class, id)`（类级绑定只有代码轨要显式做）。
- **数据包轨**：`data/yizxianmod/yiz_creature/tiedoushi.json` → `effects: ["yizmodqzk:knockback_immunity"]`。
- ⚠️ **同一 id 数据包轨会整体覆盖代码轨**（`profileById` 数据包优先，且 `toComponents()` 只加 JSON 里出现的组件）
  ⇒ 从 JSON 里删掉某条效果 = 该效果彻底消失（代码轨那份被覆盖，不会兜底）。改效果时**两处一起改**。
- 单实例开关：`/yiz effect ...`（实例补丁优先级最高）。

### 仍未做
P5 铁斗士 profile 全量迁移（`applyEntityAttributes` 里的 `setAttr` 搬进原型、删硬编码）；
辖界者 `combat.phases[]`（目前用 `DEFAULT_PHASES` 内置表）。

## 改动前置库的固定流程
`build` → `publishToMavenLocal` → `reobfJar`（`yizmodqzk` 的 `finalizedBy` 已移除，部署必须显式 reobf）；下游 `yizxianmod` 的 build 自带 reobf。改 mixin 时下游需 clean（refmap 重建）。
