---
name: synched-data-id-collision
description: "SynchedEntityData 通道 id 撞车（Duplicate id value for 0!）：症状=玩家登录被踢「无效的玩家数据」/建假 ItemEntity 时崩客户端；根因=第三方 accessor 硬编码 id 0 与 Entity.DATA_SHARED_FLAGS_ID 同槽；修法=define 层冲突消解（原版通道优先）+ 读守卫兜底；含 vanilla 字节码事实与误诊记录"
metadata:
  type: project
---

# SynchedEntityData 通道 id 撞车（1.20.1）

## 症状 → 真身

- 玩家「莫名其妙数据丢失」= **登录被踢**，不是存档损坏：日志里
  `Couldn't place player in world` → `IllegalArgumentException: Duplicate id value for 0!`
  （`SynchedEntityData.define` ← `ServerPlayer.<init>` ← `Player.<init>` ← `Entity.<init>`）
  → 玩家实体根本构造不出来 → `lost connection: 无效的玩家数据`。
- 同一根因还会在客户端崩：`ItemEntity.<init>` ← `ItemPickupParticle`（拾取物品粒子建"假 ItemEntity"渲染飞行物品）
  → **目标死亡掉落后拾取**时概率性触发，玩家以为是"死亡导致数据丢失"，其实是**实体构造失败**。
- 同一个异常还会出现在 `Mob.<init>`、`Player.<init>`——凡是被那个外来通道污染的实体类，构造时都可能炸。

## vanilla 字节码事实（1.20.1，别再猜）

从生产 SRG jar `javap` 实测：

| 事实 | 结论 |
| --- | --- |
| `m_135372_` = 实例 `define(EntityDataAccessor, T)` | 抛点在它的第 80-81 行：`itemsById.containsKey(id)` → `"Duplicate id value for " + id` |
| `m_135353_` = 静态 `defineId(Class, EntityDataSerializer)` | 池里没这个类就一路 `getSuperclass()` 走到 `Entity` 仍找不到 → **返回 0**；返回前 `LOGGER.debug("defineId called for: {} from {}")`（caller≠实体类才打） |
| `m_135379_` = `getItem` | 用读写锁查 `itemsById`；缺 id 返回 **null** |
| `m_135370_` = `get(accessor)` | `getItem(accessor).getValue()` → 缺 id 时 **NPE** |
| `SynchedEntityData.<init>` | 只 new 一个空 `Int2ObjectOpenHashMap` + 锁 + entity，**不预置任何通道** |
| `Entity.<init>` 顺序 | `new SynchedEntityData` → 8 个 define（flags=id 0 在最前：`iconst_0; Byte.valueOf; define`）→ **最后才 `this.defineSynchedData()`**（偏移 338） |

**推论（关键）：** `Entity.<init>` 里第一次 define（flags，id 0）就撞车 ⇒ 这张**全新的空 map** 里
已经有 id 0 了。而 `defineSynchedData()` 在 8 个 define **之后**才调用 ⇒ 不可能是子类先占槽。
**唯一解释：第三方 mixin 往 `Entity.<init>` 里注入了一段 `define`**（注入代码的行号会显示成
`Entity.java:256`——Forge 打过补丁的 Entity 行号与原版 SDG 行号不同，**不要拿 256 去反查源码**）。

## 修法（双保险，已落地）

1. `SynchedEntityDataMixin` 注入 `define` HEAD（cancellable）：id 已被占用时不抛异常，交给
   `SyncedDataSupport.keepExisting` 决策——**原版通道优先**：
   - 本次是原版（`net.minecraft.` 类声明）通道 → `map.remove(id)` 驱逐外来占用者，让原版定义成功；
   - 否则 → `ci.cancel()` 保留先到的条目，丢弃这次定义（被丢弃的通道读到类型安全默认值）；
   - 同一 accessor 重复 define → 直接 cancel。
2. `SynchedEntityDataMixin` 的读守卫（`get` HEAD，cancellable）：值类型与序列化器不匹配 → 修成默认值并返回；
   **该 id 根本没有 DataItem → 补一个类型安全的 DataItem 再返回**（否则 vanilla NPE）。
3. `SyncedDataSupport`（**mixin 包之外**的 holder 类）：缓存反射句柄（itemsById/锁/entity/DataItem.accessor）、
   类型安全默认值表、按实体类缓存的「继承链声明通道 → 声明类」表、冲突告警（ERROR + 12 帧调用栈）。

## 定位真凶

冲突时打 ERROR：`[SynchedEntityData] 通道 id 冲突（id=…，实体=…）` + `[SynchedEntityData] 冲突调用栈:`
——12 帧栈里就有那个模组的 mixin/工厂类。判读要点：
- **本次 accessor 声明类**（`net.minecraft.` 开头=原版）与**占用者**（「外来」=不在该实体继承链上声明）。

### ⚠️ 全 pack 审计结果（2026-09-21，逐 jar javap）

**16 个第三方 jar 里没有任何硬编码 id 的通道**：`EntityDataAccessor."<init>"(I…Serializer;)V` 出现 **0 次**、
`createAccessor`/`m_135021_` **0 次**、也没有反射/Unsafe 造 accessor。全 pack 唯一手工构造通道的是本模组
（`HealthChannels` 252/253/254、`SynchedEntityDataDefineIdMixin` 的 200+hash）。本模组 agent/ASM
（`LivingHealthTransformer`/`EntityASMUtil`）也**完全不碰** `EntityDataAccessor`/`SynchedEntityData.define`（已 grep 确认）。

**⇒ id 0 只能来自 `defineId` 返回 0**，即 `ENTITY_ID_POOL` 里「本类族（含 `Entity`）一个都没登记」时的那条
`j = 0` 分支。而 `Entity` 未登记 ⇒ 调用发生在 **`Entity.<clinit>` 之前**（mod 类初始化期 / mixin 应用期 / agent）。

**因此最后一条通路是「第三方替 `Entity.class` 调 `defineId`」**（我的守卫原先只跳过 `entityClass == Entity.class`，
把这条漏了）：第三方在 `Entity.<clinit>` 之前替 `Entity.class` 调 `defineId` → 拿到 id 0 且 `pool[Entity]=0`
→ 随后原版 8 个通道**整体 +1 偏移**（flags 变 1、air 变 2…）→ 它的 id 0 一旦也被 define 进实体，就与后面的
原版通道同槽 → `Duplicate id value for 0!`。**已修**：`defineId` RETURN 处改为按**调用方**判定——
调用方不是 `net.minecraft.*`（栈上第一个非原版/非本模组/非 Mixin 框架帧）就重映射到 200+ 段并 WARN
点名「谁替谁抢了 id 0」；调用方未知或原版则一律保留（保守，避免再把 vanilla 自己的 flags 挪走）。

可疑调用方（`defineId` 跨类调用，本次日志只抓到 3 条 `defineId called for:`）：
`citadel` 的 `mixin.LivingEntityMixin`（静态块 `defineId(LivingEntity.class, COMPOUND_TAG)` + `defineSynchedData` TAIL define）、
`omnimobs` 的 `mixin.LivingEntityMixin`（`@Inject(TAIL,"<clinit>")` + `addModSyncedData`）、
`salmonsgenesisreincarnation` 的 `CosmicSteve → defineId(ImmortalGolem.class, …)`。
**判读要点：日志里没有 `抢占了通道 id 0` 告警 ⇒ 当时的分配没走非 Entity 类的 defineId**（这点决定了往哪查）。

## 误诊记录（别再走一遍）

- ❌「某模组硬编码了 id 0 的通道」→ 审计证明全 pack 没有这种代码；真凶只在 `defineId` 的 id 分配上。
- ❌「用 `defineId` RETURN 重映射 id 0 就够了」→ 第一版守卫**跳过 `Entity.class`**，恰好漏掉「第三方替
  `Entity.class` 调」这条通路，所以告警始终不出现、崩溃照旧。要按**调用方**判，不是按 `entityClass` 判。
- ❌「玩家数据丢失=存档坏了」→ 实际是玩家实体构造失败导致登录被踢，存档没动。
- ❌「30G 内存不够=内存泄漏」→ 崩溃报告是 `GL_OUT_OF_MEMORY … Failed to allocate memory for buffer object`
  （显存/驱动侧），Java 堆只用了 1.2G/2.3G（上限 30G）。加堆只会更糟，建议 `-Xmx` 收 8–10G。
- ⚠️ 修 bug 时**不要用 PowerShell 改 Java 源文件**（`Get-Content`/`Set-Content` 会按 GBK 读 UTF-8，
  把中文注释变乱码、还可能吃掉换行）——用编辑器工具改，或在 PowerShell 里显式指定 `-Encoding utf8`。

## 排查清单（下次遇到构造期崩溃）

1. 看崩溃栈：`Entity.<init>` + `Duplicate id value for N` → 本坑；`Unknown data value N` → 通道缺失（读守卫已兜底）。
2. 生产日志 grep `Duplicate id value`、`通道 id 冲突`、`通道被分配到 id`（`defineId` 低 id 告警）。
3. 若日志里 `[HealthMap] 藏血 Map 扫描完成` 每 2 秒刷一次 → 是发现扫描的性能坑，见 [[health-discovery-cache]]。

相关：[[mixin-unique-static-clinit-crash]] [[tiedoushi-launch-and-juggle]] [[health-discovery-cache]]
