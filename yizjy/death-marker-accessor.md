---
name: death-marker-accessor
description: "通用「强制判死标记」检测+篡改（DeathMarkerAccessor）：行为验证枚举 Boolean DataParameter 设 true 看 isAlive 变 false，识别 coremod 软 getHealth 型模组的判死标记，直设 true 强制判死绕过软压/护甲/混淆串。涨跌多空通用改血的第 3 分支。"
metadata:
  type: project
---

# 通用强制判死标记检测 + 篡改（2026-08-15 落地）

> 针对 coremod「软 getHealth」型模组（对 getHealth/isAlive/isDeadOrDying 做字节码注入）里的「强制判死标记」——一个 Boolean DataParameter，被 coremod 注入的拦截器在方法开头读取，true 时直接返回 0/false/true，优先级高于软压、护甲无敌、混淆串藏血。

## 机制（coremod 软 getHealth 型模组的裁决优先级）

```
getHealth/isAlive/isDeadOrDying 方法开头: if (判死标记) return 0/false/true   ← 最高优先级
方法体: 原始逻辑（读混淆串藏血）
FRETURN 前: 软压 min(health, maxHealth+delta) + 护甲无敌                      ← 次之
```

## 检测（DeathMarkerAccessor.detect）

纯行为验证（不依赖字段名/类名）：
1. 枚举实体类层级所有静态 Boolean DataParameter；
2. 逐个「设 true → 看 `isAlive` 是否变 false → 还原」；
3. 命中即该 Boolean 是强制判死标记。

## 篡改（tamperToDead）

直设标记为 true → 绕过软压/护甲/混淆串全部保护，强制判死。**不依赖我们的 agent**（依赖目标模组自己的 coremod 注入），故 agent 降级时仍可用。

## 涨跌多空通用改血四分支（applyProportionalDreamDamage 优先级）

1. 藏血 Map（HealthMapRegistry）→ 2. 差值血量 DataAccessor（DynamicHealthAccessor）→ 3. 强制判死标记（DeathMarkerAccessor）→ 4. 字段级直改 / 数据层 / delta 软压。

## 相关

- [[health-map-tamper]] 藏血 Map 篡改（第 1 分支）
- [[dynamic-health-accessor]] 差值血量 DataAccessor 篡改（第 2 分支）
- [[forge-classload-enumeration]] Forge 类加载 + agent（agent 降级时本模块仍可用）
