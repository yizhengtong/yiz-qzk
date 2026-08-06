---
name: conduction-health-protection
description: "辖界者真实血量外部哈希表存储 + 实体 override 血量方法 + 传导限伤（CONDUCTION_CAP/INTERVAL 属性）+ 寰宇支配之剑防秒杀。给新 Boss 做受保护血量必读"
metadata:
  type: project
---

# 实体生命值保护补强（2026-08-07 落地 + 多轮重构，最终 = flashfur 式）

用户参考旧项目 `D:\ZM\yizgzq\jar\yiz-main` + flashfur 模组（`D:\ZM\yizgzq\jar\flashfur`，`ProtectedWeakHashMap` + 实体 override）思路，做「所有自研血量实体通用」的生命值保护。**终态：真实血量存外部哈希表，实体 override 血量方法完全接管**——外部模组（如寰宇支配之剑）无法靠 setHealth(0)/巨伤/die() 秒杀。

## 血量外部存储（SecureHealthClosure，flashfur HealthManager 式）

- `tool/health/SecureHealthClosure.java`：真实血量存 `ConcurrentHashMap<UUID, Float>`（外部哈希表，逻辑血量唯一来源），**不写 vanilla 血量字段**。
- 方法：`isSecure(e)`（SECURE_PULSE>0 门控）/ `getHealth(e)`（无记录回退 maxHealth）/ `setHealth(e,v)`（服务端写表 + 广播 S2C 同步客户端显示）/ `register(e,hp)` / `isRegistered` / `tick` / `removeAll`。
- `network/S2CSecureHealthPayload.java`：服务端写表后 `sendToPlayersTrackingEntity` 发真实血量给客户端 → 客户端 `setHealth` 更新本地表，血条/HUD 正常。

## 辖界者 override 血量方法（关键，QuanshouzheEntity）

外部模组（寰宇支配之剑 `InfinitySwordItem`）攻击不走 vanilla hurt 链——`onLeftClickEntity` 里先 `target.hurt(MAX)` 再 `target.setHealth(0)` 再**自实现 die()（不调 target.die，直接 victim.dead=true + dropAllDeathLoot + setPose(DYING)）**。所以必须实体 override 全套：

| override | 作用 |
|---|---|
| `getHealth()` | 服务端读外部表（`SecureHealthClosure.getHealth`）；客户端走 vanilla（显示用） |
| `setHealth(v)` | 治疗方向写表；**扣血方向重定向到 `hurt(generic, current-v)` 走传导限伤** → setHealth(0) 无法秒杀 |
| `hurt(source, amount)` | **完全接管，不调 super.hurt**：受击 CD → 传导限伤 → 写表 → 受伤反馈 → 无敌帧 → 死亡判定 → 反击锁定 |
| `isAlive()` | `!isRemoved() && 表血量>0` |
| `isDeadOrDying()` | `表血量<=0`（外部置 dead=true 不影响——原版 tickDeath 由 isDeadOrDying 触发，返回 false 就不移除） |
| `setPose(DYING)` | 血量>0 时拒绝外部强制倒地 |
| `dropAllDeathLoot()` | 血量>0 时拒绝外部伪造掉落 |
| `handleEntityEvent(3)` | 血量>0 时忽略死亡动画广播 |
| `die()` | 血量>0 时 return（拒绝外部 die）；血量归零才手动接管（保留掉落/经验/动画，不派发 LivingDeathEvent） |

## 传导限伤（属性可调，不硬编码）

**计算顺序：先衰减计算，再与限伤上限比较取 min**：
```
reduced = 衰减(amount)        —— DAMAGE_REDUCTION（百分比）+ DAMAGE_BLOCK（固定）
limited = min(reduced, cap)   —— cap = maxHealth × CONDUCTION_CAP%
```
语义：原始伤害先被减伤/格挡衰减；衰减后仍超上限（MAX 巨伤）才被 cap 兜底；衰减后已低于上限按衰减后值扣。

- 属性 `CONDUCTION_CAP`（上限%，辖界者挂 25%）：`cap = max(3, maxHealth × cap%/100)`。未挂载/为 0 → **保底 25%**（写死，防属性鉴权失败穿透）。
- 属性 `CONDUCTION_INTERVAL`（受击 CD tick，辖界者挂 20=1s）：每次实际扣血后 N tick 内不再接受任何伤害（flashfur iFrames）。未挂载/为 0 → **保底 20**。
- **寰宇支配之剑连点**：每刀 hurt(真伤) + setHealth(0) 重定向 = 两次扣血，CD 让只吃第一下（真伤那次），连点 1 秒内后续全挡。
- 400 血实测：普通伤 80 → 衰减 59（<cap 按 59）；MAX 巨伤 → 限到 100（25%）；每 1 秒最多扣 100，4 秒打死。**绝非一刀秒**。

## 其他保护设施

- `tool/health/HealthWriteGuard.java`：反射钩子，泛化 enforceFieldTick，拦「回血方向」外部篡改真实血量字段。register/enforce/remove/updateBaseline。
- `EntityHealthLocator.detectViaBytecode`：getHealth 字节码探测（补充扫描盲区）。**build.gradle 加了 `implementation "org.ow2.asm:asm:9.8"`**。
- `tool/YizieManager.java`：`checkAndRemove(e)` 检测血量≤0 → 走原版移除链（remove KILLED）。YizxianMob.aiStep 已接入（血量≤0 → allowDeathRemove + checkAndRemove 主动移除）。
- **⚠️ 移除保护 isYizCaller 栈帧跳漏（2026-08-07 修）**：`EntityRemoveProtectionMixin.isYizCaller` 原从 i=3 遍历但没跳过 Mixin 注入帧 → 任何外部 remove 都被误判「本模组调用」放行（寰宇支配之剑能移除辖界者）。已修：跳过 `Entity` 帧 + `EntityRemoveProtectionMixin` 帧，看第一个真实外部调用者。

## 属性编辑器接线

- **实体属性编辑工具**（`EntityAttributeEditScreen`）：ENTRIES 已加 `conduction_cap`（传导单发上限）+ `conduction_interval`（传导受击CD），17 个属性 LIST_COLS=9。C2S 写入链路通用（attrId → EntityAttributeGate.set）。
- **物品属性编辑台**（AttributeEditorScreen / EditableAttribute）：**不加**传导属性——传导是目标侧属性，打武器/装备无消费端，加了误导。

## 验证（2026-08-07 runClient 通过 ×N）

前置库 build + 下游 runClient：玩家进世界→寰宇支配之剑打辖界者→服务器正常退出（exit 0）。无 InjectionError/VerifyError（第三方 mod 的 agent retransform VerifyError 是已知旧行为）。`[QSZ]` 诊断日志验证：衰减→限伤→CD 节奏正确，血量按 25%/秒 下降非秒杀。

## 关联
- 辖界者 Boss 整体：[[warden-animation-reuse]]
- 属性鉴权设施：[[entity-attribute-gate]]
- 实体移除保护：[[entity-remove-protection]]
- 参考实现：`D:\ZM\yizgzq\jar\flashfur`（HealthManager + BossEntity override）、旧项目 `D:\ZM\yizgzq\jar\yiz-main`
