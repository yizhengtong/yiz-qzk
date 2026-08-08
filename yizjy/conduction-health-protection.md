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
- **受击 CD = 无敌帧属性 `INVINCIBILITY_MULT` 的时间**（用户定：传导 CD 就是无敌帧定义的时间）。`conductionHitCdTicks()` 每次 hurt 读 INVINCIBILITY_MULT → **编辑工具改无敌帧 → 传导 CD 实时跟随**。辖界者挂 16tick=0.8s。未挂载/为 0 → 保底 20。**无独立 CONDUCTION_INTERVAL 属性**（曾引入，用户指出应直接用无敌帧属性，已删）。
- **寰宇支配之剑连点**：每刀 hurt(真伤) + setHealth(0) 重定向 = 两次扣血，CD 让只吃第一下（真伤那次），连点 1 秒内后续全挡。
- 400 血实测：普通伤 80 → 衰减 59（<cap 按 59）；MAX 巨伤 → 限到 100（25%）；每 1 秒最多扣 100，4 秒打死。**绝非一刀秒**。

## 其他保护设施

- `tool/health/HealthWriteGuard.java`：反射钩子，泛化 enforceFieldTick，拦「回血方向」外部篡改真实血量字段。register/enforce/remove/updateBaseline。
- `EntityHealthLocator.detectViaBytecode`：getHealth 字节码探测（补充扫描盲区）。**build.gradle 加了 `implementation "org.ow2.asm:asm:9.8"`**。
- `tool/YizieManager.java`：`checkAndRemove(e)` 检测血量≤0 → 走原版移除链（remove KILLED）。YizxianMob.aiStep 已接入（血量≤0 → allowDeathRemove + checkAndRemove 主动移除）。
- **⚠️ 移除保护 isYizCaller 栈帧跳漏（2026-08-07 修）**：`EntityRemoveProtectionMixin.isYizCaller` 原从 i=3 遍历但没跳过 Mixin 注入帧 → 任何外部 remove 都被误判「本模组调用」放行（寰宇支配之剑能移除辖界者）。已修：跳过 `Entity` 帧 + `EntityRemoveProtectionMixin` 帧，看第一个真实外部调用者。

## 属性编辑器接线

- **实体属性编辑工具**（`EntityAttributeEditScreen`）：ENTRIES 加 `conduction_cap`（传导单发上限）；受击 CD 走**无敌帧条目**（传导 CD=INVINCIBILITY_MULT，无独立 CD 条目）。16 个属性 LIST_COLS=8。C2S 写入链路通用（attrId → EntityAttributeGate.set）。
- **物品属性编辑台**（AttributeEditorScreen / EditableAttribute）：**不加**传导属性——传导是目标侧属性，打武器/装备无消费端，加了误导。

## 受击红闪门控（2026-08-07 修，用户反馈「传导时红、CD 时不红」）

- **正确红闪链路**：原版红闪 = 服务端 `broadcastDamageEvent(source)` → 客户端 `handleDamageEvent` → `hurtTime=10` → 渲染变红。**不是 `broadcastEntityEvent(byte 2)`**（那是别的用途）——初版错用导致普通攻击不红。
- 辖界者 `hurt()` 传导扣血成功后：`hurtTime=10` + `level().broadcastDamageEvent(this, source)`（CD 内 return false 不广播 → 天然不红）。
- **寰宇支配疯狂变红**：`InfinitySwordItem.hurt` 绕过 hurt() 直接调 `victim.level().broadcastDamageEvent`（源码 161 行）→ 每次攻击都红、不经过传导 CD。**门控**：`ServerLevelDamageFlashProtectionMixin`（下游）拦 `ServerLevel.broadcastDamageEvent`，对 YizxianMob 仅当 `QuanshouzheEntity.isConductionHitFlash()`（ThreadLocal，hurt() 传导扣血时 set）才放行；外部直接调（标记未设）→ 拦截不红。已注册 yizxianmod.mixins.json。

## ⚠️ DataParameter 直写秒杀（more_avaritia 神明之剑，2026-08-07 防御）

- **more_avaritia 神明之剑**（`InfinityGodSwordItem` → `InfinityUtils.easyAttack → killEntity → forceSetHealth`）：不调 `hurt()/setHealth()`，直接 `victim.entityData.set(DATA_HEALTH_ID, 0)` **绕过 setHealth override** → 客户端 getHealth 读 DataParameter 得 0 → 触发死亡/移除。Re-Avaritia 寰宇支配之剑走 setHealth(0)（被 override 限伤挡），**more_avaritia 走 DataParameter 直写（override 挡不住）**——这是两者"一个挡得住一个挡不住"的根因。
- **flashfur 能挡**：`BossEntity.negateHealthDeltaSyncedData()` 每 tick 扫描 `SynchedEntityData` Float DataItem 写回 0。
- **辖界者防御（2026-08-07）**：
  1. `getHealth()` **客户端也读外部表**（S2C 同步真值，`isRegistered` 后才读表）→ 即使 DATA_HEALTH_ID 被写 0，客户端 getHealth 返回真值 → `isDeadOrDying` false → 不死。
  2. `customServerAiStep` 每 tick `EntityActuallyHurt.catchSetTrueHealth(this, 表血量)` 纠正 health 字段 + DATA_HEALTH_ID 回真值（flashfur 同思路）。
  3. `dropAllDeathLoot` override（表血量>0 拒）挡 its 反射掉落。

## ⚠️⚠️ removed/removalReason 字段直写 → 存档不保存（more_avaritia 终极移除，2026-08-08 防御）

- **现象**：游戏内辖界者没被移除，但**退出存档重进后消失**——存档 .mca 里根本没有辖界者实体。
- **根因（反编译 killEntity）**：more_avaritia 不只 setHealth/hurt，还用 Unsafe/反射**直接改 private 字段**：
  ```java
  victim.dead = true                    // 直写 dead
  victim.removalReason = DISCARDED      // 直写 removalReason（绕过 remove/setRemoved 方法！）
  victim.removed = true                 // 直写 removed
  ```
  `removalReason=DISCARDED` 的 `shouldSave()=false` → 原版 `saveAsPassenger`/`writeUnlessPassenger` 返回 false → **实体不写入 .mca → 重进消失**。remove/setRemoved override 拦不住字段直写。
- **flashfur 能扛**：override `isRemoved`/`remove`/`setRemoved` 空实现 + 每 tick 纠正——实体永不真正移除。
- **辖界者三重防御（2026-08-08）**：
  1. `remove(RemovalReason)` override：拦外部 remove 方法调用（FORCE_REMOVE ThreadLocal 区分主动移除）。
  2. `customServerAiStep` 每 tick `clearForcedRemoved()`：反射清 `removed`/`removalReason` 字段（逻辑血量>0 时；字段句柄缓存防每 tick 反射开销）。
  3. **`saveAsPassenger(CompoundTag)` override**：逻辑血量>0 强制跳过 removalReason 检查，直接 `putString("id") + saveWithoutId` → 实体必写 .mca（终极兜底，即使字段没来得及清）。
- **存档持久化**：`addAdditionalSaveData` 存外部表血量到自定义 tag `yizxianmod_boss_health`；`readAdditionalSaveData` 恢复外部表 + 清 `dead/deathTime/hurtTime`（防死亡残留）。
- **验证**：存档 `r.0.0.mca` chunk 0 含辖界者（`[QSZ] shouldBeSaved=true removed=false` 诊断确认）。
- **教训**：外部模组可用 Unsafe/反射直接改实体 private 字段绕过所有 override 方法；实体"没保存"排查要直接查 .mca 原始字节（`b'quanshouzhe' in 解压chunk`），而非只查血量。

## ⚠️ MAX_HEALTH 属性保护（tianshaxing 等改最大生命值，2026-08-08 防御）

- **tianshaxing（天沙星道）** `MaxHealthDrainConsequence.reduceMaxHealth(Player,double)`：`getAttribute(Attributes.MAX_HEALTH).addPermanentModifier(...)` 给 MAX_HEALTH 加 **permanent modifier** 降低最大生命值（虽签名收 Player，但任何模组都能对任意实体加 MAX_HEALTH modifier）。
- **缺口**：`EntityAttributeGate` 只保护 `prot_` 前缀的 **yizmodqzk 自定义属性**，**vanilla `Attributes.MAX_HEALTH` 不受保护** → 外部模组能改辖界者最大生命值。
- **⚠️ `LivingEntity.getMaxHealth()` 是 final 不能 override**（编译报错确认）——不能像 getHealth 一样 override 返回外部表值。
- **辖界者防御（2026-08-08）**：
  1. `SecureHealthClosure` 加 `MAX_HEALTH_MAP`（受保护最大生命值存储）+ `getMaxHealth/setMaxHealth`；**fallback 读 `getAttributeValue(MAX_HEALTH)` 而非 `entity.getMaxHealth()`**（否则 getMaxHealth 若被 override 会无限递归）。
  2. `applyEntityAttributes` 里 `setMaxHealth(this, getAttributeValue(MAX_HEALTH))`（难度缩放后记录）。
  3. `customServerAiStep` 每 tick：`getAttribute(MAX_HEALTH)` 值 ≠ 记录值 → `setBaseValue(记录值)` + `removeModifiers()` 清外部 modifier（MAX_HEALTH 上无本模组 prot_ modifier，安全）。
  4. `YizxianMob.applyVanillaDifficultyScale` 改用 `getAttributeValue(MAX_HEALTH)` 算比例（原来用 getMaxHealth()，若子类 override 会恒等 → 比例缩放失效）。

## 验证（2026-08-07 runClient 通过 ×N）

前置库 build + 下游 runClient：玩家进世界→寰宇支配之剑打辖界者→服务器正常退出（exit 0）。无 InjectionError/VerifyError（第三方 mod 的 agent retransform VerifyError 是已知旧行为）。`[QSZ]` 诊断日志验证：衰减→限伤→CD 节奏正确，血量按 25%/秒 下降非秒杀。

## 关联
- 辖界者 Boss 整体：[[warden-animation-reuse]]
- 属性鉴权设施：[[entity-attribute-gate]]
- 实体移除保护：[[entity-remove-protection]]
- 参考实现：`D:\ZM\yizgzq\jar\flashfur`（HealthManager + BossEntity override）、旧项目 `D:\ZM\yizgzq\jar\yiz-main`
