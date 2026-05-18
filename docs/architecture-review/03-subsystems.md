# 子系统评估

## ① 伤害系统 — 95/100

### 特殊伤害类型 (4种)

| 类型 | API方法 | 特点 |
|------|---------|------|
| 固定值 | `damage(target, amount, source)` | Delta系统三層应用 |
| 百分比 | `percentDamage(target, percent, source)` | 基于最大生命值 |
| 真实伤害 | `trueDamage(target, amount, source)` | setHealth直接扣减 |
| 破甲 | `armorPiercingDamage(target, amount, source)` | 用magic()跳过护甲 |
| 破无敌帧 | `pierceInvulnerabilityDamage(target, amount, source)` | 临时清零invulnerableTime |
| 破甲+破无敌帧 | `armorPiercingAndPierceInvulnerabilityDamage(...)` | 组合 |

### 属性绑定伤害

- `DamageAttributeRegistry` — 注册属性自动在近战攻击后附加伤害
- `SpecialDamageAttributeRegistry` — 注册属性自动附加真伤/破甲/破无敌帧

### 伤害计算引擎

`DefaultDamageCalculator` 使用 `ModifierStack` 多乘区公式：
```
FinalDamage = (BaseDamage + ΣAdditive) × Π(1 + Multiplicative) × ΠIndependent
```

> 注：当前仅有 multiplicative 区被实际使用（物品 %伤害增幅/减免）。

---

## ② 健康修改系统 — 95/100

### 三层防御 + 一保底

```
Layer 1: Delta System (EntityASMUtil.addDelta)
  ├── SynchedEntityData Float 通道
  └── 压缩血量上限: getHealth() ≤ maxHealth + delta

Layer 2: ChannelScanner (HealthChannelScanner)
  ├── 反射扫描类层次全部 EntityDataAccessor<Float>
  └── 修改所有 Float 通道（覆盖模组自定义血量）

Layer 3: DirectHealthFallback
  ├── 反射直接修改 SynchedEntityData.DataItem[] 数组
  └── 按字段名/类型启发式找到 itemsById 和 isDirty

Backup: EntityActuallyHurt
  └── VarHandle 直接写入 LivingEntity.health 字段

每个 Layer 失败时下一层接管，确保伤害一定生效。
```

### 直接健康值修改

`modifyHealth(entity, delta)` — 正数治疗/负数伤害，绕过 hurt()，修改所有 Float 通道。

---

## ③ 禁疗系统 — 90/100

### 三层拦截

```
Layer 1: ASM注入 (EntityASMUtil.applyHealBan)
  ├── 注入到所有 LivingEntity.heal() HEAD
  └── 在任何事件/原版逻辑之前削减治疗量

Layer 2: Mixin (LivingEntityMixin @ModifyVariable)
  └── setHealth() HEAD 处拦截治疗增量

Layer 3: NeoForge Event (HealBanHandler.onLivingHeal)
  └── LivingHealEvent 订阅

Tick强制: 每10 tick 快照 Float 通道，未经授权的增量回溯削减
```

### 策略

- 先百分比削减，再减固定值
- 百分比 + 固定值独立配置
- 属性绑定禁疗 (`HealBanAttributeRegistry`)

---

## ④ 玩家保护态 — 90/100

### 5 层防御

| 层 | 位置 | 机制 |
|---|------|------|
| Agent | `LivingEntity.setHealth()` 入口 | `health = max(1, NaN?1:health)` |
| Agent | `LivingEntity.die()` 入口 | `if protected → return` |
| Agent | `Entity.remove()` 入口 | `if protected → return` |
| Unsafe | `ProtectedServerPlayer` | 覆盖 hurt/die/kill/remove/tick/setHealth |
| Mixin | `LivingEntityMixin.die()` HEAD | `ci.cancel()` 最终兜底 |

实现方式: `sun.misc.Unsafe` 替换单个 Player 实例的 class 指针 → ProtectedServerPlayer。

---

## ⑤ 物品属性系统 — 95/100

### 7 属性 × 3 操作

| 属性 | 存储位置 | 操作 |
|------|---------|------|
| 攻击力 | ATTRIBUTE_MODIFIERS | get/set/add |
| 攻击速度 | ATTRIBUTE_MODIFIERS | get/set/add |
| 交互距离 | ATTRIBUTE_MODIFIERS | get/set/add |
| 横扫范围 | ATTRIBUTE_MODIFIERS | get/set/add |
| 横扫衰减 | CUSTOM_DATA (NBT) | is/set |
| 耐久值 | MAX_DAMAGE Component | get/set/add |
| %伤害增幅 | CUSTOM_DATA (NBT) | get/set/add |
| %伤害减免 | CUSTOM_DATA (NBT) | get/set/add |

- set 使用固定 UUID（基于 `yizmodqzk:<属性名>` hash），多次 set 不产生重复 modifier
- 属性 6-7 在 DefaultDamageCalculator 中自动参与伤害乘区计算

---

## ⑥ UI 系统 — 85/100

### PlayerTalentUI
- 窗口化面板（可拖拽、可调大小）
- 默认定位在生存背包左侧
- 从 ModRegistries 自动读取全部效果，过滤 EntityPerception
- 交叉 UnlockManager 状态决定展示

### ItemInfoUI
- 替代原版物品悬浮提示
- 展示物品的 6 维效果数据
- 自动显示 7 种物品属性

### 问题
- getPlayerTalents() 每渲染帧 O(n) 遍历全部效果
- 窗口状态用静态字段（无持久化）

---

## ⑦ 效果框架 — 80/100

### 已完成
- 6 维度清晰建模
- 构造函数自动注册到 ModRegistries
- JSON 数据驱动加载 (EffectDataLoader)
- 解锁管理与 NBT 持久化
- EffectEventBus 调度分发

### 缺失
- JSON 加载的效果 execute() 为空实现
- 无代码级具体效果实现（设计意图：由下游模组提供）

---

## ⑧ ASM Agent — 85/100

### 加载策略（双保险）

```
策略1: 直接 attach
  ├── Unsafe 绕过 ALLOW_ATTACH_SELF 限制
  └── 反射 VirtualMachine.attach(pid)

策略2: 子进程 attach (JDK 21 回退)
  └── VmAttachment.loadAgentViaSubprocess(jarPath)

嵌入: /META-INF/jarjar/yizmodqzk-agent.jar
```

### ASM 改写内容
- `getHealth()` — delta 截断防止 NaN
- `isAlive()` / `isDeadOrDying()` — delta 感知
- `heal()` — 禁疗注入
- `setHealth()` — 保护态生命值钳制
- `die()` / `remove()` — 保护态拦截
