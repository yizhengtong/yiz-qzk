---
name: dynamic-health-accessor
description: "通用「差值血量」DataParameter 检测+篡改（DynamicHealthAccessor）：getHealth=normal-away 差值动态计算 + 独立 Boolean 死亡标记，双向行为验证识别 normal/away，增加 away 正确扣血。⚠️待修复 Bug：梦幻终焉 coremod 截断 getHealth 会导致检测失效（已放弃兼容）。"
metadata:
  type: project
---

# 通用差值血量 DataParameter 检测 + 篡改（2026-08-14 落地）

> 针对血量藏在**自定义 DataParameter**、`getHealth = normal - away` 差值动态计算、`isAlive/isDeadOrDying` 读**独立 Boolean 死亡标记**的实体（泽林变体 VariantZsieinEntity 类）。纯行为验证，不依赖字段名/类名。

## 模式特征（类型可混淆，行为不变）

- 两个 Float DataAccessor：`normal`（上限/被减数）+ `away`（损失/减数），`getHealth = normal - away`；
- 一个 Boolean DataAccessor：死亡标记，`isAlive = !death`（独立于 getHealth）；
- `away` 通常被 clamp 到 `[0, normal]`。

## 检测（DynamicHealthAccessor.detect）

1. 枚举实体类层级所有静态 Float / Boolean DataAccessor；
2. 两两组合 Float，`|a-b| ≈ getHealth` 过滤（差值 ≈ 血量）；
3. **双向行为验证**区分 normal/away：直写通道 +1，getHealth 增 → normal，减 → away；
4. 找值 `== isDeadOrDying` 的 Boolean 作死亡标记。

## 篡改（tamper）

- 直写 `away`（减数）**增加** amount（正确扣血方向），clamp 到 normal，绕开目标 `setExaltedAway` 的 clamp；
- `away ≥ normal` 时置死亡标记 `true`。

## ⚠️ 待修复 Bug：梦幻终焉截断 getHealth 致检测失效（已放弃兼容）

- **现象**：加载梦幻终焉（fantasy_ending，`com.mega.uom`）时，泽林这类差值实体无法正确修改。
- **根因链**：梦幻终焉 coremod（`FeCoremod`/`SoftGetHealthClassVisitor`）对所有实体 `getHealth` 注入 `special_getHealth`，做 `min(health, getMaxHealth()+delta)` 截断；泽林变体 `getHealth = normal - away`（真实血量 ~500）但**没 override getMaxHealth**（vanilla 默认 20）→ getHealth 被截断成 `min(500-away, 20) ≈ 20` → 差值过滤 `|a-b|≈getHealth` 与方向验证（直写+1 看 getHealth 增减）全部失真。
- **取舍**：已放弃兼容梦幻终焉，回退到纯双向验证。测试差值实体时需**临时禁用梦幻终焉**（和其他冲突模组一样 `.disabled`）。
- **若将来要兼容**：方向验证不能依赖 getHealth（被截断），需改用字节码静态分析（识别 getHealth 方法体 FSUB + 两个 DataAccessor）或 DataAccessor 值域特征，但会牺牲对普通实体的精确区分（见下面误判坑）。

## 误判坑（已修）

- **ImmortalGolem 误判**：`CUSTOM_HEALTH`（当前血量）+ `REVIVE_TIMES`（复活次数，初始 0），`|当前-0| ≈ getHealth` 命中差值 → 需**双向验证**（away 直写 +1 必须让 getHealth 减）排除；`REVIVE_TIMES` +1 不影响 getHealth → 非 away。
- **值域 fallback 误伤普通生物**：曾为兼容梦幻终焉加值域 fallback（normal=大值、away=小值），但普通生物（DATA_HEALTH_ID + 护盾值）方向验证「部分失败」也触发 fallback，把护盾值当 away 改 → 已回退。

## 相关

- [[health-map-tamper]] 藏血 Map 篡改（同属涨跌多空通用藏血检测，另一分支）
- [[forge-classload-enumeration]] Forge 1.20.1 类加载 + agent instrumentation
