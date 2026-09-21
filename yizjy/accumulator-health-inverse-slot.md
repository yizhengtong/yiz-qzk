---
name: accumulator-health-inverse-slot
description: "累加器型（派生血量）实体的正确改法：血量 = 上限 − 累加器字段，写回必须走那个字段（inverse 槽）、且回血对抗要按「逻辑血量升高」判定。含本模组真 bug：VitalitySeveranceHandler.enforceFieldTick 把 inverse 判据套在已经是逻辑血量的值上 → 判反 → 自己扣血被当回血回滚 = 「首次能改、之后永远锁死在首次改完的值」；以及 GateHunt 门控误判（容差 5% + 不还原 + 按类缓存）把无关冷却钉成 1e9 的止血"
metadata:
  type: project
---

# 累加器型血量（派生血量）实体：正确改法与两个真 bug

## 一、这类实体的数据模型（原型：`legendary_monsters` 2.1.22 的 `IAnimatedBoss`）

```java
// 服务端：血量是派生值，vanilla 通道只是显示面
public float getHealth() {                      // 每次调用现算
    if (level().isClientSide() || !attributesReady()) return super.getHealth();
    return Mth.clamp(safeMaxHealth() - this.totalDamageTaken, 0F, safeMaxHealth());
}
public void setHealth(float v) { ... super.setHealth(Mth.clamp(v, 0F, safeMaxHealth())); }  // 只写通道

// 累加器只有两个变更点：
//  ① ForgeEvents.addDamage(LivingHurtEvent) -> addDamage(amount, source)
//       clamped = min(amount, damageCap())  ← 基类 damageCap()=21，即单次最多计入 21（特定伤害类型免上限）
//       finalDamage = 子类减伤 -> 护甲 -> 若 <=0 直接 return
//       totalDamageTaken += finalDamage;  super.setHealth(getHealth());  hurtCD = 30;   // 30 tick 无敌
//  ② heal(amount)：totalDamageTaken = max(0, totalDamageTaken - amount);  super.setHealth(getHealth());
```
全 1325 个类里 `totalDamageTaken` 只出现在 `IAnimatedBoss` 一处 ⇒ 它就是权威。

**识别信号**：日志里血量在两个值之间来回（都是 `上限 − 累加器`）；
`entity_health_slots.json` 里该实体的槽是 `inverse=true` 的 field（我们的行为定位是对的）；
`getHealth()` 在客户端/`attributesReady()==false` 时才退回 vanilla 通道。

## 二、★真 bug：禁疗的「回血方向」判据对 inverse 槽判反了

`VitalitySeveranceHandler.enforceFieldTick` 原来写的是：
```java
boolean healed = slot.inverse() ? (curVal < prevVal) : (curVal > prevVal);   // ❌
```
但 `EntityHealthLocator.readLocated()` 返回的**已经是逻辑血量**（反向/编码槽都换算过了：
`B − 存储值` / 解码结果），**与槽是否 inverse 无关** ⇒ 对累加器型正好判反：
- 我们自己的扣血（逻辑血量变低）被判成「回血」→ `writeLocated(prevVal)` **把我们的扣血回滚**；
- boss 的真回血（逻辑血量变高）被判成「没变化」→ **放行**。

**为什么表现为「首次能改、之后锁死在首次值」**：第一刀时还没有禁疗配置
（`VitalitySeveranceConfig.get(entity) == null` → 直接 return），写进去的值生效；该刀结束后
改血线给目标挂上 100% 永久禁疗，从第二刀起这段就在 ≤10 tick 内把每次扣血回滚到改前的值。
生产实证（TheObliteratorEntity）：反复出现 `391.49334716796875`（= 450 − 58.506657）与
`377.3600158691406`（= 450 − 72.639984）两个值，正是「我们写进去 → 被回滚」。

**修法**：判据只认逻辑血量升高 —— `healed = curVal > prevVal`（与 inverse 无关），
降低则推进基线（棘轮）。另外新增 `enforceFieldFastTick` 挂在 `EntityTickMaintenance` 的
非周期分支每 tick 跑（只对「已禁疗 + 命中反向槽」的实体有实际动作）：这类实体回血是**每 tick**
改同一字段，`tickCount % 10` 的周期快照会把「扣血→回血」看成没变化。

> **通用教训**：`HealthSlot.inverse()` 只描述「存储值 ↔ 逻辑血量」的映射方向，
> **任何已经过 `readLocated()` 的值都不该再套一次 inverse 语义**。

## 三、写回对抗：反向槽直接「钉回」，不要去猎门控（A）

反向槽实体的血量权威就是那一个字段，第三方回血也改同一个字段 ⇒ `GateHunt.verifyAndHunt`
的「被拉回」分支现在先试 `EntityHealthLocator.reassertLocatedSlot(entity, target)`
（重写同一字段并回读确认，最多 6 次），失败才退回门控猎杀；并且**反向槽实体不受
`FOUND_GATE`/`NEGATIVE` 短路**（那两个缓存说的是「门控猎过了」，与「值要不要钉回去」无关）。

## 四、门控误判止血（C）

原判据只有「2 tick 后血量接近 target」，容差 `max(1, target*5%)` —— target=362 时容差 18，
于是**我们自己上一笔写入**就足以把无关候选判成权威门控并永久钉到 1e9：
实测 `TheObliteratorEntity#dimensional_shoot_cooldown → 钉 1e9`，而且命中后不还原、按类缓存
`FOUND_GATE` → 该 boss 这个技能冷却被永久写坏、该类之后连验证都不做。

现在：①容差收到 `max(0.5, target*1%)`；②必须与「探测前基线」不同（值确实因这次探测而变）；
③命中后 2 tick 双向复核，失守则还原候选 + 撤销 `FOUND_GATE` + 继续下一个候选；
④记原值（`PIN_ORIGINALS`），实体死亡/移除时 `restorePinned()` 还原。
`verifyAndHunt` 的「写回保持」容差同样从 5% 收到 1%（原来差 14 点也判「保持」）。

> 已钉在内存里的坏值（如某 cooldown=1e9）**重启游戏即恢复**（纯字段、不落盘）。

## 五、反向槽上限 B 的确定性（D）

反向槽写回是 `store = B − 逻辑血量`。B 的取值优先级：meta `b` → `getMaxHealth()` →
最后才是 `B = 存储值 + getHealth()` 自推导。**field 反向槽检测时以前不记 `b`**
（accessor 反向槽一直记 `b=maxHp`），只能靠自推导 —— 而自推导在「刚写完累加器」或
「`attributesReady()==false`、getHealth() 退回 vanilla 通道」时都会算错 B，写回落到错误的存储值上。
现已补齐：检测时记 `b=<maxHp>`，且老条目（无 b）也会先走 `getMaxHealth()`。
`config/yizmodqzk/entity_health_slots.json` 里既有的无 `b` 条目**不用删**。

## 六、下次验证（生产）

1. `[GateHunt] <类> 反向累加器槽被拉回 当前=… 目标=… → 重写钉回（第 n/6 次）` —— A 在干活；
2. 打这类 boss 时血量应**持续下降**而不是弹回同一个数；`391.49334716796875` 这种值不该再反复出现；
3. `[GateHunt] 门控复核` 只应在真门控上出现；不该再看到把 `xxx_cooldown` 钉 1e9；
4. 若还锁死，看 `enforceFieldFastTick` 是否生效（该实体是否真的带上了 `VitalitySeveranceConfig`）。

相关：[[health-discovery-background-scan]] [[native-health-vault-anti-fantasy]] [[dev-prod-reflection-and-player-gate]] [[health-map-tamper]]
