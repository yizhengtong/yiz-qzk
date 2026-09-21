---
name: bleed-system
description: "1.20.1 流血系统（bleed_ratio/time/stack 三属性 + 持续结算 + 流血伤害类型无视减免 + 展示Buff + 蓄力满增强）"
metadata:
  type: reference
---

# 1.20.1 流血系统

## 结论（2026-08-29 落地）

**服务端机制**：攻击者施加流血 → 目标被外部伤害实际扣血 → 按比例累加待结算流血伤害 → **分 4 次、每 4 tick 结算（总 16 tick）**，用**流血伤害类型 `actuallyHurt` 直接结算（无视无敌帧/抗性/护甲/魔咒）**。

## 三属性（部分设置用默认互补）

- `bleed_ratio`（流血比例 %，默认 **0**=不流血，属性驱动）——只设比例即生效。
- `bleed_time`（每层时间 **秒**，默认 **6**）——⚠️ 属性注册默认值必须与代码 `DEFAULT_TIME_SEC` 一致（否则玩家实例默认值覆盖代码默认，显示错误）。
- `bleed_stack`（最大叠加 **次**，默认 **2**）。

**挂载坑**：新属性必须加进 `tizMod` 的 `EntityAttributeModificationEvent` 玩家挂载，否则 `getAttribute` 为 null → 聚合跳过 → 始终 0 不生效。

## 叠加放大

每次攻击 +1 层（上限 stack）：
- `流血比例 = 基础比例 × 层数`（默认 20% 叠 2 层 = **40%**）。
- `持续时间 = 每层时间 × 层数`（6s × 2 = **12s**）。

## 伤害反应与结算

- `BleedHandler.onLivingAttack`（LivingAttackEvent）：攻击时 `applyBleed`（读攻击者属性）。
- `LivingEntityBleedMixin`：`hurt` HEAD/RETURN 记录血量差 → `onExternalDamage`（仅外部伤害，流血自身不连锁）。
- `onExternalDamage`：累加 `pendingBleed` + 重置结算计划（4 次 × 16 tick）。
- `tick`（baseTick TAIL）：每 4 tick 结算 `pending/(剩余次数)`，`actuallyHurt(bleedSource, amount)`。

## 流血伤害类型（无视所有减免）

- datapack：`data/yizmodqzk/damage_type/bleed.json`（scaling: never）。
- `YizDamageTypes.BLEED`（ResourceKey）。
- **`actuallyHurt` 是 protected** → 需 `LivingEntityActuallyHurtAccessor`（@Invoker）暴露。
- `actuallyHurt` 不走 `hurt()` → **无受击反馈**，需手动 `hurtTime = hurtDuration = 10`（public 字段）。
- ⚠️ SPELL 的 `YizDamageTypes.SPELL` 注释说 datapack 但 json 未落地（spell.json 不存在）——参考时注意。

## 展示 Buff（纯前端）

- `BleedEffect`（MobEffect，`isDurationEffectTick=false` 不驱动逻辑）+ `BleedEffects`（DeferredRegister）+ 图标 `textures/mob_effect/bleed.png`（1.20.1 无 `getSprite()`，图标按 id 自动加载）。
- `syncBuff`：`MobEffectInstance.duration` 是 **private**，且 `addEffect` 的 `update()` **只取更大的值**（递减的剩余时间无法刷新）→ 用 `MobEffectInstanceDurationAccessor`（@Accessor）**直接强制设 duration = 剩余时间**。
- Buff 只在真正流血时添加；直接挂 Buff 不触发流血。

## 蓄力满增强（会心/渴攻）

- `LockOnHandler.isChargeReadyFor(player, target)`：**必须目标 == 锁定目标**（避免锁定 A 蓄力打 B），视线转移即失效（充能重置）。
- 攻击时（`BleedHandler`）：蓄力满 → `markChargeCrit`（暴击 mixin 消费，强制暴击）+ `applyChargeBleed`（比例按玩家 `bleed_ratio`，未配默认 30%；时间/叠加固定默认 6 秒/2 次）。
- 距离：满充能挂 `ForgeMod.ENTITY_REACH` 修饰（已有）。

## 锁定目标改视线射线

`LockOnProvider`（客户端）+ `LockOnHandler`（服务端）都从「60° 锥扫描选最近视线中心」改为 **`ProjectileUtil.getEntityHitResult` 视线射线命中第一个实体（穿墙）**，与穿墙描边一致。

## 文件位置（yizmodqzk）

`tool/health/BleedSystem.java`、`handler/BleedHandler.java`、`effect/BleedEffect.java`+`BleedEffects.java`、`mixin/LivingEntityBleedMixin.java`+`LivingEntityActuallyHurtAccessor.java`+`MobEffectInstanceDurationAccessor.java`、`api/YizDamageTypes.java`、`data/yizmodqzk/damage_type/bleed.json`；属性挂载在 `tizMod` EntityAttributeModificationEvent。
