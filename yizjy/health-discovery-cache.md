---
name: health-discovery-cache
description: "【已被取代 · 反面教材】外部藏血发现的全类路径扫描性能坑：getAllLoadedClasses 冷启动 1~3 秒、旧实现按类数量变化每 2 秒重扫 → 每次进存档首击卡顿；本文那套修法（发现结果落盘 + 按实体类就近发现 + 负缓存落盘）用「缩小发现范围」换性能 → 直接造成「map 生命实体改不动」回归，已回退"
metadata:
  type: project
---

> 🔴 **本文已被 [health-discovery-background-scan](health-discovery-background-scan.md) 取代，只作历史/反面教材。**
>
> 本文的修法（`HealthDiscoveryCache` 落盘 + 「发现一次就不再全扫」+ 按实体类就近发现 +
> 把 `EntityHealthLocator` 的「无槽」负缓存落盘后**信任不再重试**）确实让首击变快，
> 但同时**缩小了发现范围 / 缓存了解析出的实例 / 按命中收敛候选**，生产结果是
> **omnimobs 那类「map 藏血」的实体一处都写不进去**（判定退回被 delta 抬高的显示值 → 改不动）。
> 该机器已在 `c474256` 整批回退，四个文件退回 `b82f222` 语义。
>
> **正确方向**：范围不动、实例不缓存、不按命中收敛 —— 只把「全类路径枚举 + 逐字段反射解析」
> 合并成一次并挪到后台线程，按类缓存**判据结论与字段句柄**，字段值每次现读。
> 具体见 [health-discovery-background-scan](health-discovery-background-scan.md)（含强度清单与验证方法）。

# 外部藏血「发现」扫描的性能坑与缓存体系（已取代）

## 症状

- 用户实测：「同一款模组的同一个实体，**每次进存档后的第 1 次攻击卡 1~2 秒**」，
  但按设计血量定位结果**应该被保存**（`entity_health_slots.json` 已经有正缓存）。
- 生产日志实证：`[EHL] 定位失败(无槽) …Ignis_Entity`（06:30:44.306）→
  `[HealthMap] 藏血 Map 扫描完成，命中 1 个`（06:30:46.798）→ `[TotalOverride] … 表征扫描`（06:30:46.928）
  = **首击花了 2.5 秒**；随后 `[HealthMap] 藏血 Map 扫描完成` 在战斗期间**每 2.0~2.9 秒刷一次**（14 次）。

## 根因（两条，都在「发现」而不是「读写」）

1. **负缓存没落盘**：`EntityHealthLocator.NON_SLOT_CLASSES`（"该类无槽，走 delta/数值通道兜底"）只在内存里。
   每次进存档，第一个被攻击的「无槽」实体（如 cataclysm 的 `Ignis_Entity`）都要把 5 阶段全量探测
   （itemsById 行为验证 / accessor 扩展扫描 / 字节码探测 / per-field 行为验证 / codec 反解）重跑一遍。
2. **发现扫描按「类数量变化」自动重扫**：`HealthMapRegistry`、`ExternalHealthStore`、`ExternalRefStore`
   三处都用 `Instrumentation.getAllLoadedClasses()` 枚举**全部已加载类 + 逐类逐字段反射**
   （`ExternalRefStore` 还对每个静态非原始字段 `f.get(null)`），并用「已加载类总数变了 + 2 秒节流」触发重扫。
   战斗中新类不断加载（刷怪、粒子、AI）→ **每 2 秒把整个类路径反射一遍**，冷启动那次尤其贵。

## 项目规则（重要，别再犯）

> **绝不要用「定时器 / 类数量变化」自动重扫整个类路径。**
> 发现一次 → 结果落盘（声明类名 + 字段名）→ 之后只做**按目标类就近发现**（只扫该实体类及其父类）。

## 现在的实现

- 新类 `HealthDiscoveryCache`（mixin 包外的普通类）→ `config/yizmodqzk/health_discovery.json`：
  - `scanned: {section: true}` + `entries: {section: [{owner, field}]}`；
  - section：`health_maps`（HealthMapRegistry）/ `external_maps`（ExternalHealthStore）/ `external_refs`（ExternalRefStore）。
  - API：`isScanned/markScanned/put/get/resolve`（`resolve` 用 `Class.forName(name,false,loader)`，不触发 `<clinit>`）。
- 三个扫描器：首次使用 → `isScanned` 为真则**只反解落盘句柄**（零类枚举），否则做**唯一一次**全类路径扫描并
  `put` 每条发现 + `markScanned`；另加 `ensureScannedFor(entityClass)`（扫实体类继承链，每类一次）兜住「模组实体类后加载」。
- `HealthMapRegistry.ensureScannedFor` 由 `resolveHealthMaps` 调用；`ExternalHealthStore`/`ExternalRefStore`
  在候选列表入口按当前实体类做同样的就近发现。
- `EntityHealthLocator` 的 `NON_SLOT_CLASSES` **落盘**：`entity_health_slots.json` 新增
  `no_slot: {类名: 结构指纹}`（`_version: 3`）。加载时只有「类存在 + 指纹一致」才 `NON_SLOT_TRUSTED`；
  被信任的类**不再做中间血量重试**（那个重试正是首击卡顿的来源），模组更新导致字段结构变化时指纹不匹配 → 自动作废重探。
- 诊断：`TotalHealthOverride` 每类第一次写血量时打
  `[TotalOverride] <类名> 首击耗时(ms): 定位=… 主槽写=… 数值通道=… 串通道=… 藏血Map/外部=… 对象图=… NBT=… vanilla=… 合计=…`
  → 下次生产测试直接看这一行验证（合计应在几十毫秒内）。

## 验证方法（生产）

1. 首击后 grep `首击耗时` → 合计应 < 100ms；`[HealthMap] 藏血 Map 扫描完成` 全程**只应出现 1 次**（首次启动那次），
   第二次进存档应为 0 次。
2. `config/yizmodqzk/health_discovery.json` 存在且含 `scanned` 三个 true；`entity_health_slots.json` 里
   `no_slot` 有对应实体类。
3. 想强制重扫：删掉 `health_discovery.json` 的对应 section（或整个文件）再进游戏。

相关：[[synched-data-id-collision]] [[tiedoushi-launch-and-juggle]] [[entity-attribute-gate]]
