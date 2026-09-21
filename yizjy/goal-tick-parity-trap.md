---
name: goal-tick-parity-trap
description: "1.20.1 自定义 Mob 的 Goal 隔 tick 陷阱：Mob.serverAiStep 按 (serverTick+entityId)%2 隔 tick 跑 goalSelector.tick()，Goal 里用 mob.tickCount % N 节流会让约一半实体永不命中 → 不寻路/发呆、攻击间隔翻倍；附动画索引同步与星级实体属性打架两个连带坑"
metadata:
  type: project
---

# Goal 隔 tick 陷阱与铁斗士三连坑（2026-09-10 落地）

## 现象
一批同类自定义实体（铁斗士）中只有一部分会锁定目标并走过去，其余原地发呆；同时攻击动画每段播完有明显停顿（应 1234 无缝循环），偶发某段完全不播。

## 根因一：Goal.tick() 每 2 tick 才跑一次
- `Mob.serverAiStep()` 取 `i = serverTickCount + entityId`，`i % 2 != 0 && tickCount > 1` 时只调 `targetSelector/goalSelector.tickRunningGoals(false)`，否则才调 `tick()`。
- `GoalSelector.tickRunningGoals(false)` 只 tick `requiresUpdateEveryTick() == true` 的 Goal；`Goal.requiresUpdateEveryTick()` 默认 **false**。
- 于是自定义 Goal 的 `tick()` 实际每 2 tick 才执行一次。
- 若 Goal 内用 `mob.tickCount % N == 0` 做节流（如寻路每 10 tick 一次），`(birthTick + entityId)` 奇偶为奇数的实体**永远**命中不到该条件 → 从不 `moveTo` → 发呆。这解释了"只有一部分能动"的随机分布。
- 同一原因让 `attackCooldown` 每 2 tick 才递减 1 → 攻击间隔翻倍（28 tick 变 56 tick），动画播完后长时间空档 = 卡顿。

## 修复要点
1. Goal 覆盖 `requiresUpdateEveryTick()` 返回 true，保证每 tick 收到 tick()。
2. 节流改用 Goal 自己的计数器（`start()` 里初始化，tick 里递减），不要用 `mob.tickCount % N`。
3. 攻击间隔按"即将播放的那段动画长度"返回（动画 1.375/1.0/1.25/0.5s → 间隔 28/20/25/10 tick），而不是全场一个固定值，否则短动画段会空等。

## 根因二：动画索引与动画触发不同包
- 索引走 `SynchedEntityData`（tick 末才打包发送），动画触发走 `broadcastEntityEvent`（立即发送）→ 客户端先收到事件、后收到索引，`setupAnim` 用旧索引挑动画定义，播错段或按错时长提前 stop。
- 修复：把索引编码进事件 id（如 `EVENT_ATTACK_BASE + idx`，选 70..73 避开 vanilla 已用 id），客户端在 `handleEntityEvent` 里同步设索引，渲染只读该字段。

## 根因三：星级实体属性被自己的 aiStep 打回 1 星
- 基类 `aiStep` 顺序是 `applyEntityAttributes()`（内含 `applyVanillaDifficultyScale`，注册标准 80）→ `registerSecureHealth()` → `applyChessStarIfNeeded()`（`applyStar` 把 max_health 改成 180 并把 AttributeStandardizer 标准更新为 180）。
- 子类又在 `super.aiStep()` 之后重跑 `applyVanillaDifficultyScale()` → max_health 打回 80 且标准被覆写回 80；而 `enforceSecureHealthState` 每 tick 又按 ChessUnitTable 拉回 180 → **max_health 每秒 80↔180 震荡**，日志被 `[AttributeStandardizer] 检测到外部篡改` + `[SecureHealthClosure] 表值跳变` 刷屏。
- 同段代码里 `setHealth(ManaTracker.getMax(this))` 是笔误：`ManaTracker.getMax` 返回 **法力上限**（140），把 1 星铁斗士血量设成 140（上限 80）。
- 修复：删掉子类这段重复初始化。出生属性/星级/满血基类已经跑完，`MOVEMENT_SPEED` 由 `createAttributes` 保证，不需要子类再补。

## 排查手法
- 判据：同一 spawn 批次里"能动的和发呆的交替出现" → 优先查 Goal 隔 tick / `tickCount % N`，而不是寻路或碰撞箱。
- vanilla 源码在 `forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar` 里可直接 unzip 出 `net/minecraft/world/entity/Mob.java`、`ai/goal/GoalSelector.java` 核对，不必猜。
- 同类风险：`QuanshouzheMeleeGoal` 未覆盖 `requiresUpdateEveryTick()`，其 `attackCooldown` 同样每 2 tick 才递减 → 辖界者实际攻击间隔是 `getAttackInterval()` 的两倍（7/15 → 14/30 tick）。本次未改，待确认是否要一并修。

## 补充：动画换版与技能分批（2026-09-10 同日迭代）
- 换 Blockbench 新版动画（中文骨骼名：全模型/身体/头/肌胸/腰部/左臂/左小臂/左拳头/右臂/右小臂/右拳头/右腿/左腿/左小腿/右小腿）必须映射回模型英文骨骼名（all/body/head/chest/waist/left_arm/left_forearm/left_fist/right_arm/right_forearm/right_fist/right_leg/left_leg/left_calf/right_calf），否则动画静默不生效。**批量替换要按长度降序**：单字「头」会先把「左拳头/右拳头」吃成「左拳head」，导致这两个骨骼名替换失效。
- 新动画长度（tick）：ATTACK_1=1.0s / ATTACK_2=0.6667s / ATTACK_3=0.5769s / ATTACK_4=0.5s / SKILL_1=2.0s；攻击间隔跟着动画长度走。
- 一段动画可有多个伤害触发点（ATTACK_3 = 0.35s + 最后一帧），实现用「动画段 → 触发 tick 数组 + 游标」，而不是单个 pendingTick。
- **持续伤害必须破无敌帧**：`LivingEntity.hurt` 在 `invulnerableTime > 10` 时对等量伤害直接 `return false`。技能从一次性改成「每 tick 一跳、共 1 秒」时，若继续用 `t.hurt`，20 跳只有第 1 跳生效，总伤害只剩 1/20。用 `YizModQZKAPI.pierceInvulnerabilityDamage(target, amount, source)`（暂存 invulnerableTime=0 → hurt → 恢复）才能逐 tick 生效；分批比例取 `1/持续tick数`，总输出与一次性持平。

## 补充二：三段合并为单动画（2026-09-10 定稿）
- 最终形态：用户直接在 Blockbench 把攻击1/2/3 合并成单个 `攻击` 动画（2.5s / 50t / 45 通道，含 ROTATION+POSITION+SCALE），不再需要代码里连续播三个 state。一次攻击 = 播一整套动画，伤害在 0.75s / 1.4s / 2.1s（第 15/28/42 tick）三个点触发。
- 伤害与范围：第 1 段=自身半径 4 格 ×1.4；第 2 段=朝目标方向长 4 宽 3 高 3 ×1.25；第 3 段=自身半径 4 格 ×1.25。攻击间隔 = 动画时长 +1 tick。
- 转 Blockbench 导出时：动画可能带 SCALE 通道，转换脚本整块复制即可，不要只处理 ROTATION/POSITION。
- **面板查不到攻击力**：`EntityProbeScreen.a7Rows` 的「攻击力」优先显示 `ATTACK_STRENGTH`，而它其实是百分比伤害加成（`LivingEntityMixin`：`amount *= 1 + atkStr/100`），为 0 时伤害走 vanilla `ATTACK_DAMAGE`。铁斗士把 ATTACK_STRENGTH 设为 0 → 面板显示 0/看似缺失。修复=攻击强度为 0 时回退显示 vanilla ATTACK_DAMAGE。
