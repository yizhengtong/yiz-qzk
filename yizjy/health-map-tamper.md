---
name: health-map-tamper
description: "通用「藏血 Map」检测+篡改方案（HealthMapRegistry）：泛型判据定位 Map<实体,Number> 藏血表、unreflectSpecial 锁基类 put 绕过重写鉴权、同步改写多个 Map 绕过每 tick 拉回。已落地涨跌多空攻击线，攻破 omnimobs Flashfur。"
metadata:
  type: project
---

# 通用藏血 Map 检测 + 篡改（2026-08-14 落地）

> 目标：写一个**通用**的「篡改所有实体生命值」系统，按**类型特征**攻取把血量藏在**静态 Map** 里的模组（不碰对方类名/字段名，改名/更新后仍命中）。已验证攻破 omnimobs Flashfur。

## 三类藏血 Map 的通用锚点（类型改不了）

- 字段是 `static`、类型 `java.util.Map`（或子类）；
- **泛型签名**：`Map<K, V>` 且 `V ∈ Number`（血量数值）、`K` 是 `Entity` 子类（实体）。
- 用 `Field.getGenericType()` 读 `ParameterizedType` 的 K/V，不读字段名（可混淆）。

## 三层绕过（对应 omni 的三重防线）

1. **鉴权（ProtectedWeakHashMap 重写 put/remove/replace 全调 checkAccess）**
   → `IMPL_LOOKUP.unreflectSpecial` 锁 `java.util.WeakHashMap.put` **基类实现**，跳过虚分发表直写。
   - `IMPL_LOOKUP` 非 public，用 Unsafe 直读 `MethodHandles.Lookup.IMPL_LOOKUP` 静态字段拿。
   - 沿实例类父链向上找第一个 `java.util.*` 下的具体 Map 实现（WeakHashMap/HashMap/ConcurrentHashMap）作锁定目标。
2. **每 tick 拉回（checkAndUpdateHealth → lambda$checkAndUpdateHealth$1）**
   - 比较 `healthValues`（当前血量）vs `lastGoodHealthValues`（最后正确血量），不等就 `setHealth` 拉回。
   - **绕过 = 同步改写所有命中的 Map**，让两者恒相等 → 篡改不被察觉。
3. **篡改检测（HealthManager.setHealth 里 `_a(entity) != healthValues.getOrDefault(...)` 直接 return）**
   - 我们直接操作 Map 不调 setHealth，天然绕过。

## 关键坑

- **数据结构**：同 key 类可能有多个藏血 Map（healthValues + lastGoodHealthValues），`Map<Class, FieldHandle>` 用 keyClass 作 key 会**后者覆盖前者**（只改一个 → 拉回）。必须 `Map<Class<?>, List<FieldHandle>>`。
- **「最大血量」表混入**：`EntityUtil.REAL_MAX_HEALTH` 的 key 是 `Entity` 基类（存最大血量），不是当前血量。用**继承深度**启发式：只取 key 类相对 Entity 深度最大的那组 Map（具体实体类的当前血量+拉回依据），忽略 Entity 基类的表。
- **readHealth 取最小值**：命中多个 Map 时取该实体条目的最小值（当前血量 ≤ 上限）。

## ✅ 已修复（2026-08-16）：藏血 Map 检测失效 = 全局一次性扫描缓存

- **根因**：`HealthMapRegistry` 的 `scanned=true` 是进程级一次性扫描标志——第一次攻击时扫描的类表快照，若目标模组的藏血 Map 类还没加载（懒加载），后续就永远漏判（判成「非 map 实体」→ 误走常规改法改不动）。
- **修复**：改成「有新类加载就重扫 + 按类缓存」：`ensureScanned` 里 `currentClassCount() != lastClassCount`（节流 2s 检查）时重扫，`scan()` 构建新 map + 原子交换（`HEALTH_MAPS = fresh`）。
- ⚠️ **坑**：双检锁里带副作用的节流检查别调两次（第一次刷新时间戳、第二次被节流吞掉 → 重扫永远跳过），时间戳只在真正重扫后更新（检查函数改纯函数）。
- **同类 bug 一并修**：`DynamicHealthAccessor.NON_DYNAMIC` / `DeathMarkerAccessor.NEGATIVE` 会把「已死实例」也永久缓存成「非该类型」（doDetect 里 `isDeadOrDying` 短路返回 null 被当成「不是」）→ 只有健康实例检不到才缓存负结果，已死/无血实例下次重检。

## 相关

- [[flashfur-health-hiding]] omni 藏血的防御视角（本文件是攻取视角，互补）
- [[dynamic-health-accessor]] 差值血量 DataParameter 篡改（涨跌多空通用藏血检测的另一分支）
- [[bytecode-health-takeover-defense]] 字节码级血量接管（同类对抗主题）
- [[modding-tech-landscape]] 技术谱系（TransformationService/Instrumentation/VTable 分层）
