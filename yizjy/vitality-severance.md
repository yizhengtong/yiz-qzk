---
name: vitality-severance
description: "绝妄生机（原禁疗）完整体系：改名映射、三层机制（永久配置/临时+叠加/属性驱动统一入口）、字段级禁疗（EntityHealthLocator 定位真实字段+回弹抵消）、辖界者每次攻击+5%可叠到100%"
metadata:
  type: project
---

# 绝妄生机（原禁疗）体系（2026-08-07 重写落地）

「禁疗」更名「绝妄生机」。核心增强：**最初梦幻能改血的自研血量实体，绝妄生机也能彻底限制其回血**。

## 改名映射

| 旧 | 新 |
|---|---|
| 禁疗率 `anti_heal_rate` | 绝妄生机率 `vitality_severance_rate`（0~100，1=1%） |
| 禁疗时间 `anti_heal_time` | 绝妄生机时间 `vitality_severance_time`（秒） |
| `HealBanConfig` | `VitalitySeveranceConfig`（tool/health，Map<UUID,Config> 永久配置，百分比+固定值） |
| `HealBanHandler` | `VitalitySeveranceHandler`（临时/叠加/tick强制） |
| `HealBanAttributeRegistry` | `VitalitySeveranceAttributeRegistry`（api，注册表聚合禁疗源） |

三处显示名同步纪律见 [[attribute-display-name-sync]]。

## 三层机制

1. **永久配置** `VitalitySeveranceConfig.set(entity, percent, fixed)` — 先百分比后固定值削减，`Config.apply()`。
2. **临时/叠加** `VitalitySeveranceHandler`：
   - `addTempBan(entity, factor, duration)` — 临时削减比例（1.0=完全禁疗），put 覆盖刷新
   - `addStackingBan(entity, deltaPercent, duration)` — **叠加式**：在现有基础上 +delta%（上限 100%），重置时长；过期归零
   - `enforceTick` — **通道级**（DataParameter 扫描，原版实体）
   - `enforceFieldTick` — **字段级**（自研血量实体，见下）
3. **属性驱动统一入口** `VitalitySeverance.apply(attacker, target)` — 唯一施加入口：注册表聚合 + 绝妄生机率 + 绝妄生机时间；收敛了 `AttackInterceptorMixin.onAttackReturn` 与 `LivingEntityMixin.onHurtReturn` 两处分散施加。

治疗拦截：`LivingHealEvent`（onLivingHeal 统一应用 config+tempBan+stack 三种削减）+ ASM heal 注入（applyVitalitySeverance）。

## 字段级禁疗（核心增强）

自研血量实体（LM totalDamageTaken 型）的真实血量字段是**普通反射字段，不在 DataParameter**——旧禁疗的通道级扫描扫不到它，回血完全不受限；而最初梦幻能改它（`EntityHealthLocator.applyPersistentDamage` 反射直改）。同一批实体两套机制能力不对称。

**修复**：`enforceFieldTick` 用 `EntityHealthLocator.locate/readLocated/writeLocated` 定位真实字段，tick 检测**回血方向**变化（inverse 型字段减少 / 正向型字段增加 = 回血）→ 反射写回基线抵消。本模组主动扣血（applyPersistentDamage）后 `updateFieldBaseline` 防误伤。

## 辖界者效果

每次攻击 `addStackingBan(target, 5.0f, 7*20)` — **每次叠加 5% 绝妄生机，上限 100%（完全禁疗），持续 7 秒（连续攻击刷新，停手过期归零）**。

## 关键坑
- 自研血量实体真实字段 = 反射字段（不在 DataParameter）→ 字段级禁疗必须用 EntityHealthLocator 定位，不能只扫通道。
- 叠加用 `addStackingBan`（读现有+delta），不是 addTempBan（put 覆盖单值）。
- `onLivingHeal` 要**统一应用三种削减**（原版实现 config 存在就 return，会漏掉临时/叠加）。
- 改名用 sed 全局替换 `HealBan`→`VitalitySeverance` + `anti_heal_*`→`vitality_severance_*`，注意类文件也要 `mv` 改名（Java 文件名=public 类名）。

## 关联
- 受保护属性设施：[[entity-attribute-gate]]
- 辖界者状态：[[warden-animation-reuse]]
