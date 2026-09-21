---
name: health-discovery-background-scan
description: "藏血发现（HealthMapRegistry/ExternalHealthStore/ExternalRefStore）性能改造：三处各自 getAllLoadedClasses+逐字段反射 → 合并成 HealthDiscovery 一次全类路径枚举 + 后台守护线程快照 + 只缓存字段句柄；范围不缩、实例不缓存、不按命中收敛；含首击 1385ms→后台 与 每 2.7s 重扫刷屏的日志证据，以及本轮 id 0 撞车崩溃（Byte→Pose）的归因与防线补洞（defaultFor 漏 POSE）"
metadata:
  type: project
---

# 藏血发现：共享枚举 + 后台快照（取代 health-discovery-cache）

> ⚠️ **本文件取代 [health-discovery-cache](health-discovery-cache.md)**。那套「发现结果落盘 + 按实体类就近发现 +
> 信任落盘负缓存」是**用缩小发现范围换性能**，直接造成「map 生命实体改不动」的回归，已整批回退（commit `c474256`）。
> 正确方向是：**范围不动、实例不缓存，只把重复的枚举与反射解析挪到后台/做共享与缓存解析结果**。

## 一、改造前的实测数字（生产日志，2026-09-21）

| 证据 | 数值 |
| --- | --- |
| `[TotalOverride] <类> 首击耗时(ms): … 藏血Map/外部=1385.1 … 合计=1391.8` | 首击 ~1.39 秒，几乎全在「藏血Map/外部」这一段 |
| `[HealthMap] 藏血 Map 扫描完成，命中 1 个` | 30 秒内 **12 次**（08:11:31→08:12:01，间隔 ~2.7s），**全部在 Server thread** |

两条都是「三处发现器各自做全类路径枚举 + 逐字段反射」造成的：

- `HealthMapRegistry`：`ensureScanned()` 每次攻击都可能 `getAllLoadedClasses()`（2s 节流）+ 全类逐字段 `getGenericType()`；
- `ExternalHealthStore`：`rescanIfNeeded()` 同样 2s 节流 + 全量字段扫描，且**缓存 Map 实例**（不是句柄）；
- `ExternalRefStore`：**完全没有缓存** —— `readHealth` 与 `writeHealth` 每次调用各做一遍
  `getAllLoadedClasses()` + 全类静态字段 `f.get(null)`，并且 `holdsEntityRef`/`findHealthField`
  对每个候选存储对象都重算 `getStringUUID()/position()/getMaxHealth()`（每次攻击几万次多余分配）。

## 二、现在的架构（`tool/health/HealthDiscovery.java`）

- **一次枚举，三套判据**：一趟 `getAllLoadedClasses()` + 一趟 `getDeclaredFields()`，
  `getGenericType()` 每个静态 Map 字段只解析一次（旧版最多 3 次），同时分派：
  ①`Map` + K∈Entity 子类 + V∈Number → 藏血 Map；②`Map` + K∈{UUID,Integer,int,String,Entity,WeakReference} → 外部藏血 Map；
  ③静态对象字段（非原始/串/枚举/集合/java.* 除 UUID）→ 外部存档候选。
- **后台线程持有快照**：单线程 `ScheduledExecutorService`（守护线程、`MIN_PRIORITY`、名字 `yiz-health-discovery`）
  每 **5 秒**检查一次类表；类数量变了才全范围重建并**原子换 `Snapshot`**，没变只更新时间戳。
  主线程 `current()` 只读快照，**完全不参与枚举、不阻塞**；快照还没有时走一次同步兜底（强度优先）。
- **只缓存判据结论 + 字段句柄，绝不缓存实例**：`FieldHandle`（Unsafe staticFieldBase/Offset，不触发 `<clinit>`）
  与 `Field`；**字段值一律每次调用现读** —— 这条是硬规矩，缓存实例会在第三方换壳后拿到过期引用 → 读不到真血。
- **预热**：`tizMod.commonSetup`（Agent 加载后）与 `onServerStarting` 各调一次 `HealthDiscovery.warmupAsync()`，
  把冷启动那次 1~3 秒挪到后台；战斗外也持续保鲜（旧版只在攻击时才发现新类）。
- **实例字段清单按类缓存**：`ExternalHealthStore.INSTANCE_FIELDS`、`ExternalRefStore.FIELD_CACHE`/`NUMERIC_FIELD_CACHE`
  （含一次性 `setAccessible`），把每次攻击的 `getDeclaredFields()` + 可访问性检查降到零。

## 三、强度清单（改这条线时必须逐条自检，一条都不能丢）

1. 发现范围 = 全类路径 `Instrumentation.getAllLoadedClasses()`；有新类加载仍**全范围**重扫（只是节流放宽到 5s、挪到后台）。
2. 候选**每次调用现读字段值**；只缓存字段句柄。
3. **不得**按「此前命中过」收敛候选集合。
4. **不得**因为「读不到值」跳过写入（`TotalHealthOverride` 里 map 那步的 `if (hp != null)` 是基线语义，别动）。
5. **不得**砍掉 `EntityHealthLocator` 的「无槽负缓存 + 中间血量允许重试一次」。
6. **不得**写「调第三方模组方法」的通道（`ForeignHealthAuthority` 已删，不复活）。
7. `HealthSelfFilter.isOwnClass` 保留（排除本模组自己的记账 map/对象，纯正确性过滤）。

## 四、本轮顺带修的崩溃（通道 id 撞车 → Byte→Pose）

- **现象**：`ClassCastException: java.lang.Byte cannot be cast to net.minecraft.world.entity.Pose`
  at `Entity.m_20089_()`(getPose) ← `m_217003_(Pose)`(hasPose) —— 服务端崩在玩家 tick
  （`LivingEntity.tick → updateInvisibilityStatus → getEyeHeight → getPose`），客户端崩在渲染实体。
- **归因**：属**通道 id 撞车**（不是血量管线、也不是 cataclysm 的 `WorldGenRegionAccessor` VerifyError）。
  日志实证：`[SynchedEntityData] 通道 id 冲突（id=0，实体=…）：两个原版通道撞同一个 id`，
  08:02 起**每个**实体类（ItemEntity/ExperienceOrb/Arrow/LightningBolt/TiedoushiEntity/village 系/colossus/cataclysm 系…）
  各撞一次，且 `本次` 与 `占用者` 两个 accessor **都声明在 `Entity`** → `Entity` 自己的通道 id 重复。
  按 javap 过的 vanilla `m_135353_`(defineId) 字节码，`Entity.<clinit>` 的 8 次 defineId 只可能拿到 0..7；
  出现重复 ⇒ 类池 `ENTITY_ID_POOL` 在 `Entity.<clinit>` 前后被外部改动过。
  **不是本模组自己干的**：全 jar 扫描只有 vanilla/Forge 的 `SynchedEntityData` 引用 `f_135343_`（类池），
  且所有会话日志里都没有 `抢占了通道 id 0` 告警 ⇒ `defineId` 的 id 0 重映射**从未触发**。
- **崩因（本模组防线的洞）**：`SyncedDataSupport.defaultFor` 的默认值表**漏了 `EntityDataSerializers.POSE`**
  → 读守卫拿到 `null` = 「无需修复」→ 提前放行 → vanilla 把 `DATA_POSE` 槽里的 `Byte`
  （`DATA_SHARED_FLAGS_ID` 的值）当 `Pose` 返回 → CCE。已补齐 1.20.1 **全部 28 个**序列化器
  （POSE→`Pose.STANDING`、ROTATIONS/PARTICLE/VILLAGER_DATA/CAT_VARIANT/FROG_VARIANT/PAINTING_VARIANT/
  SNIFFER_STATE/VECTOR3/QUATERNION/OPTIONAL_UNSIGNED_INT…），每个构造都在 try/catch 里，失败退化为「不修」。
- **另修**：`SyncedDataSupport.isVanilla` 原按 `net.minecraft.` 前缀判原版，会把下游模组的
  **影子包名 `net.minecraft.client.yiz.xian.*`** 误判成原版 → 冲突时按「原版优先」驱逐真正的 vanilla 通道；
  现已先排除 `HealthSelfFilter.isOwnClass`。
- **新增诊断（下次跑就能点出真凶）**：
  ①冲突日志带上**字段名**：`本次 accessor=<entity data: 0>[Entity.DATA_POSE]…`；
  ②启动自检 `[SynchedEntityData] Entity 通道 id 自检（正常=序号）: DATA_SHARED_FLAGS_ID=0 …` +
  `[SynchedEntityData] 类池 ENTITY_ID_POOL[Entity]=…`，重复时直接 ERROR 点名。
- **线索（未定论）**：撞车只在 08:01 之后的会话出现；07:47/07:53（`[村庄革新] village_mod-1.4.5.jar` 尚未落盘）零撞车。
  village_mod 自带 `SynchedEntityDataMixin`（`set` 拦截做护甲减伤）等 entity mixin，但其字节码**不碰 defineId/类池**，
  全 jar 扫描也无任何模组引用类池 → 只能算可疑，需靠上面两条新日志定案。

## 五、验证方法（生产）

1. 进存档后 grep `[HealthDiscovery] 全范围枚举完成(后台)` → 应出现**在后台线程**（启动时），用时是枚举真实成本；
   `(主线程兜底)` 只应在「预热还没跑完就开打」时出现一次。
2. `[TotalOverride] <类> 首击耗时(ms): … 藏血Map/外部=…` → 应从 1385ms 掉到个位数（该段现在只有字段读值）。
3. `[HealthMap] 藏血 Map 扫描完成，命中 N 个` → 只在**命中数变化**时出现（正常全程 1 次），不再每 2.7 秒刷屏。
4. 强度自检：`[HealthMap] 识别藏血 Map:` 仍能认出 omnimobs 的 `EntityUtil.REAL_MAX_HEALTH`；
   map 类实体仍然可改（`表征扫描: … 藏血Map=1 …`，不是全 0）；`[EHL] 定位成功` 正常出现。
5. 崩溃自检：`[SynchedEntityData] Entity 通道 id 自检` 全绿（id=序号）；若报重复，看新日志里的字段名定位是哪两条通道。

相关：[[health-discovery-cache]]（已取代） [[synched-data-id-collision]] [[health-map-tamper]] [[tiedoushi-launch-and-juggle]]
