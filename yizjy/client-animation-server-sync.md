---
name: client-animation-server-sync
description: "客户端动画/姿态判断不能用原版 Mob 的服务端状态：getTarget() 不进同步通道（Mob 只有 DATA_MOB_FLAGS_ID，setTarget 只写普通字段，isAggressive() 也读普通字段）⇒ 客户端恒 null。铁斗士「追击移动不播动画」即此坑；修法=服务端判定位移档位走自己的 DataHolder 通道同步。含 walkAnimation 是按真实位移算的这一点与排查清单"
metadata:
  type: project
---

# 客户端动画：原版 Mob 的状态拿不到，必须服务端同步

## 坑（2026-09-22 生产反馈：铁斗士"攻击前追击移动不播动画"）

`TiedoushiModel.setupAnim` 原来这样选动画：
```java
boolean hasTarget = entity.getTarget() != null && entity.getTarget().isAlive();
boolean moving = entity.walkAnimation.isMoving() && hasTarget;
boolean chasing = moving && entity.getTarget() != null;
```
**客户端 `getTarget()` 恒为 null** ⇒ `hasTarget` 恒假 ⇒ `moving` 恒假 ⇒ 永远播 idle →
"一边追一边滑行"；而攻击/技能动画正常（它们走 `handleEntityAttack`/`broadcastEntityEvent` 同步）。

**字节码事实（1.20.1，别再猜）**：
- `Mob` 只定义一个 DataItem：`DATA_MOB_FLAGS_ID`（Byte）。**没有 target 通道**。
- `Mob.setTarget(LivingEntity)` = `putfield target`（普通字段，不同步）。
- `Mob.isAggressive()` 读的也是**普通 boolean 字段**（不是那个同步 flag）⇒ 对 `Mob`（非 `Monster`）
  这条同样拿不到，别拿它当客户端判据。
- 顺带：`walkAnimation` 是**按逐 tick 真实位移**算的（`LivingEntity.updateWalkAnimation`：
  `getX()-xo` 那一套 → `walkAnimation.update(min(位移*4,1), 0.4)`），**不是**按 `getDeltaMovement()`。
  ⇒ 所以「拦住 lerpMotion / 速度包」不会让走路动画失效；反过来，被推着滑动也会被算成"在走"。

## 规矩

1. **凡是客户端要用来做动画/渲染分支的服务端状态（目标、档位、阶段、姿态…），一律自己开同步通道送过去**
   （本项目既有做法：DataHolder + `SynchedEntityData.defineId`，或姿态包如 `S2CLaunchFxPayload`）。
2. 判"有没有在动"用 `walkAnimation.isMoving()`（两侧都算，且按真实位移）——
   比 `getDeltaMovement()` / 位置差手算更稳，且天然排除"被挡住"。
3. 一次同步给一个**档位枚举**（0 静止 / 1 行走 / 2 追击），客户端只做渲染分支，
   避免再出现"客户端自己推状态"推不出来。

## 铁斗士的落地（参照实现）

- `TiedoushiEntity.DataHolder` 新增 `LOCOMOTION`(BYTE)：`LOCOMOTION_IDLE=0 / WALK=1 / CHASE=2`，
  `getLocomotionState()` 供客户端读；`defineSynchedData` 里注册（通道 id 从池里取，不硬编码）。
- 服务端 `aiStep` → `updateLocomotionState()`：目标存活 **且** `walkAnimation.isMoving()` 时，
  目标在 `getAttackRange()` 之外 → CHASE，之内 → WALK；否则 IDLE；**只在变化时 set**（省同步带宽）。
- `TiedoushiModel`：`chasing = (档位==CHASE)`，`moving = 追击档 || 行走档 || walkAnimation.isMoving()`（兜底）。
  这样 WALK(1.0s) 与 CHASE(0.4762s) 两条循环动画各自有了语义——
  **在此之前 `walkState` 是死分支**（`moving && !chasing` 在"有目标"时恒假）。

## 排查清单（"移动但没动画"）

1. 动画分支条件里有没有 `getTarget()` / `isAggressive()` / 其它服务端字段？→ 有就是本坑。
2. 该状态有没有同步通道？`javap -p` 目标类的 `EntityDataAccessor` 字段数（`Mob` 只有 1 个）。
3. `walkAnimation.isMoving()` 在两侧是否都为真（服务端也跑 `updateWalkAnimation`）。
4. 动画分支是不是被别的条件短路成死代码（本次 `walkState` 就是）。

相关：[[tiedoushi-launch-and-juggle]] [[goal-tick-parity-trap]] [[warden-animation-reuse]] [[creature-component-system]]
