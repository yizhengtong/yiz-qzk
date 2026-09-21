---
name: health-discovery-background-scan
description: "藏血发现（HealthMapRegistry/ExternalHealthStore/ExternalRefStore）性能改造 + 发现分层策略：三处各自全类路径枚举 → 合并成 HealthDiscovery 一次枚举 + 后台守护线程快照 + 只缓存字段句柄；再加 HealthTier「常规实体缓存 / 非常规生命值实体一律现场扫描」的行为判据（写回落地+未被拉回才升级常规、被拉回/回读未落地/命中门控即粘性非常规）。范围不缩、实例不缓存、不按命中收敛；含首击 1385ms→后台 与每 2.7s 重扫刷屏的日志证据、id 0 撞车崩溃（Byte→Pose）归因与防线补洞（defaultFor 漏 POSE）"
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

## 四、发现分层：常规实体缓存 / 非常规生命值实体一律现场扫描（`HealthTier`）

> 用户定的策略：**常规实体缓存，非常规生命值实体一律现场扫描**，关键在「匹配策略要能正常分辨」。

- **常规实体**（绝大多数）：血量规规矩矩躺在 vanilla 通道里 → **跳过三处外部藏血发现**（省掉几千个候选对象的
  字段走查），只走主槽判定 + 数值/串/对象图/NBT 镜像 + vanilla 通道；判定按类缓存，**30 秒复验一次**
  （到期那一刀按现场全量探测走），类结构指纹变化即作废。
- **非常规生命值实体**（藏血 Map / 差值血量 / 加密串 / 外部存档 / 权威门控）：**一律现场扫描，判定粘性永不降级**。
  缓存的只有「字段句柄/判据结论」，候选集与字段值每次攻击都是当场重新取 —— 绝不收敛候选、绝不缓存实例。
- **匹配策略（只认行为证据，不认类名包名）**：
  | 结论 | 证据 |
  | --- | --- |
  | 非常规（粘性） | 行为定位到主槽／任一藏血发现器读到值或写成功／差值血量 FSUB／**2 tick 写回被拉回**／立即回读未落地／命中权威门控 |
  | 常规（可证伪的乐观结论） | 全量探测都没命中 **且** [`GateHunt`](../../../1.20.1/yizmodqzk/src/main/java/net/minecraft/client/yiz/tool/health/GateHunt.java) 的 2 tick 写回验证报「值保持」或「实体已被本次写入击杀」 |
  | 作废（回到未知） | 立即回读未落地、结构指纹变化 → 下次攻击重新现场全量探测 |
- 默认是**未知 = 现场全量探测**：没拿到正向证据之前一律不缓存，宁可多扫。
- **与「禁止收敛候选」的区别（别搞混）**：被禁的是「只保留此前命中过的候选」（命中集收敛 → 后加载/换壳的
  结构永远发现不了）；这里是「整条发现通道被行为验证证明在这类实体上读不出东西」才跳过，且有四道反向闸门
  （每刀 2 tick 写回验证、立即回读失败即作废、30s 复验、结构指纹），任一异常立刻打回现场全量扫描。
- 日志证据：`[HealthTier] <类> → 非常规生命值实体（每次攻击现场全量扫描）: 依据` /
  `→ 常规实体（跳过外部藏血发现，30s 复验一次）: 依据` / `常规判定作废 → 回到现场全量扫描: 依据`；
  `[TotalOverride] <类> 表征扫描(现场全量/常规缓存): 主槽=… 藏血Map=…` 直接标出这一刀走的哪一档。

## 五、强度清单（改这条线时必须逐条自检，一条都不能丢）

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

## 七、08:29 会话复盘（第一轮 jar 上线后的实跑证据 + 崩溃归因更正）

**① 性能改造生效（日志实证）**：`[HealthDiscovery] 全范围枚举完成(后台)` 全程在 `yiz-health-discovery` 线程 ——
首扫 `类数=30146 … 用时=8918ms`，随后 1071 → 883 → 627 → 137 → **17ms**（类表稳定后）；
`[HealthMap] 藏血 Map 扫描完成` 全程 **1 次**（改造前 30 秒 12 次）；候选规模实测 **外部Map候选=279、静态对象=21980**
（每刀 2 次调用就是 4 万多次 `f.get(null)` —— 这正是第二轮 `HealthTier` 常规缓存要省掉的东西）。

**② id 撞车归因更正（重要）**：`Duplicate id value` 在 **08-21 起的 7 份崩溃报告**里反复出现（不是今天才有，
也**不是 village_mod 引入的** —— 08:29 会话根本没加载 village_mod）。新增的字段名诊断直接点名了撞车双方：
```
id=0 占用者 <entity data: 0>[Entity.f_19805_]   ← DATA_SHARED_FLAGS_ID
id=0 本次   <entity data: 0>[Entity.f_19836_]   ← DATA_NO_GRAVITY
```
而**同一会话启动自检**是健康的：`f_19805_=0 f_19832_=1 … f_19836_=5 … f_146800_=7；类池[Entity]=7`。
⇒ **同一个字段启动时 id=5，打到 08:31:47 变成 id=0** —— id 撞车是**会话中途事件**（字段被换掉或 id 被改），
不是 `Entity.<clinit>` 的启动顺序问题。**线索**：`[YizRestore] 自保护还原` 次数与冲突强相关 ——
07:53 会话 0 次还原/0 冲突，07:47 4 次/0 冲突，而 07:58(22/18)、08:01(20/1)、08:07(22/1)、08:29(22/2)
都是「~20 次还原 + 出现冲突」；08:29 的还原爆发在 08:31:40.69，冲突紧接在 08:31:47。
`YizRestoreTransformer` 走 `Instrumentation.retransformClasses`（规范上不重跑 `<clinit>`、不允许增删字段），
所以「静态状态被重新初始化」这条链路还没证实，**已补三样只读诊断等下一次实跑定案**：启动快照
（`声明类.字段名 → id@对象身份`）、冲突日志里的 `⚠漂移: 启动时=… → 现在=…` + 当时 `类池[Entity]` 值、
以及挂在后台 ticker 上的 `auditEntityChannelDrift()`（Entity 通道一变就 ERROR 打时间戳，好和 `[YizRestore]` 对齐时间线）。

**③ 08:29 会话「连接中断」不是本模组代码问题**：08:31:41.294 起 NVIDIA 驱动侧
`GL_OUT_OF_MEMORY`（10 条驱动通告 + **76,920 条** `Failed to allocate memory for buffer object` /
`Failed to map memory for buffer`），触发点是**焰魔死亡瞬间**（08:31:41.263 目标=0 → 31ms 后 OOM）。
同一签名在 `crash-2026-09-21_06.19.10-client.txt` 就出现过（**复发性显存问题**，与记忆里
「GL_OUT_OF_MEMORY 是显存/驱动侧、不是 Java 堆泄漏」一致）。之后 08:31:55 Forge `S2CModData` 握手解码抛
`ClassCastException: Collections$SetFromMap cannot be cast to [B`（netty `ByteBufUtil.threadLocalTempArray`）
→ 登录包处理失败 → `lost connection: 连接中断`；服务端同时报 tick 落后。**都不是渲染/网络之外的因果**，
本模组那一刀只在打血量（日志里 `[TotalOverride]`/`[GateHunt]` 一切正常）。

## 八、验证方法（生产）

1. 进存档后 grep `[HealthDiscovery] 全范围枚举完成(后台)` → 应出现**在后台线程**（启动时），用时是枚举真实成本；
   `(主线程兜底)` 只应在「预热还没跑完就开打」时出现一次。
2. `[TotalOverride] <类> 首击耗时(ms): … 藏血Map/外部=…` → 应从 1385ms 掉到个位数（该段现在只有字段读值）。
3. `[HealthMap] 藏血 Map 扫描完成，命中 N 个` → 只在**命中数变化**时出现（正常全程 1 次），不再每 2.7 秒刷屏。
4. **分层自检**：`[HealthTier] <类> → 常规实体`（普通怪，之后 `表征扫描(常规缓存)`）与
   `[HealthTier] <类> → 非常规生命值实体`（藏血实体，之后 `表征扫描(现场全量)`）都要能看到；
   若刷出 `常规判定作废 → 回到现场全量扫描`，说明这个类被误判过，把依据（reason）记下来。
5. 强度自检：`[HealthMap] 识别藏血 Map:` 仍能认出 omnimobs 的 `EntityUtil.REAL_MAX_HEALTH`；
   map 类实体仍然可改（`表征扫描(现场全量): … 藏血Map=1 …`，不是全 0）；`[EHL] 定位成功` 正常出现。
6. 崩溃自检：`[SynchedEntityData] Entity 通道 id 自检` 全绿（id=序号）；若报重复，看新日志里的字段名定位是哪两条通道。

## 九、构建/部署踩坑（本轮新增）

- **下游 jar 会静默丢掉 `yizxianmod.refmap.json`**：源码没变时 `compileJava` 是 UP-TO-DATE，
  mixin 注解处理器不再输出 refmap，而 `build/tmp/compileJava` 里也没有残留 → 打出来的 jar 少一个
  `yizxianmod.refmap.json`（少了 3~9KB，MD5 变了但其他类一模一样）。**部署前必须核对 jar 里的 refmap 条目**；
  丢了就 `gradlew clean build` 重来（clean 后 compileJava 实跑，refmap 8936 字节 / 17 条映射回来了）。
  这类 jar 上线会让下游的 `EntityRemoveProtectionMixin` 等静默不生效，症状像「免移除保护突然失效」。

相关：[[health-discovery-cache]]（已取代） [[synched-data-id-collision]] [[health-map-tamper]] [[tiedoushi-launch-and-juggle]]
