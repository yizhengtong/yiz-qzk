---
name: warden-animation-reuse
description: "辖界者（同人坚守者）当前完整状态 + 复刻原版 Warden 动画/骨骼的通用方案。新窗口接续必读：模型照抄 WardenModel、纯近战、中立立即反击、狂暴计时（血≤600或5秒，持续6秒+刷新）"
metadata:
  type: project
---

# 辖界者（同人坚守者）— 当前状态与实现方案（2026-08-04 深夜）

> **给新窗口的接续上下文**：下方「当前状态」是辖界者 Boss 的最新实现快照；下方「Warden 动画复用」是可复用的通用方案。用户接下来还要加新机制（见文末待办）。

## 当前状态（代码即现状，编译通过）

实体 `yizxian1.21.1/src/main/java/net/minecraft/client/yiz/xian/entity/QuanshouzheEntity.java`，刷怪蛋 `quanshouzhe_spawn_egg`，召唤 `/summon yizxianmod:quanshouzhe`。

**模型** `client/model/QuanshouzheModel.java`：**完全照抄原版 WardenModel 骨骼层级**（`root→bone(24)→body→head→tendrils/arms/ribcages`，腿挂 bone 下），纹理 `textures/entity/quanshouzhe/warden.png`（用户绘制 128×128）。头顶两个装饰（原 head2）**挂在 head 下随头转动**。Renderer `translate(0)` + `scale(0.8)`（脚踩地）。

**动画**：照抄原版 WardenModel.setupAnim（idle/walk/tendrils + `this.animate()` 各 AnimationState → WardenAnimation 关键帧）。触发：咆哮用 Pose.ROARING、攻击/音爆用 `broadcastEntityEvent` byte 4/62。

**攻击**：**纯近战**（咆哮 AoE 和音爆射线**均已移除**，`QuanshouzheCastingGoal.java` 已删文件）。近战 `MeleeGoal`：攻击范围 3.5 格，**会追击**（`navigation.moveTo(target, 1.0)`），基础间隔 **12 tick**（0.6 秒），狂暴 **6 tick**（0.3 秒），`ATTACK_DAMAGE=50`（2026-08-05 调低），攻击时 `broadcastEntityEvent(this,(byte)4)` 播 Warden 攻击动画。伤害结算时还会乘自定义「攻击强度 60」→ ×1.6。

**AI（纯中立）**：不主动攻击任何实体。被攻击**瞬间立即反击**（`hurt()` 里直接 `setTarget(attacker)`）+ `RetaliateGoal`（玩家优先，**跳过创造模式玩家/无敌实体** `isValidRetaliateTarget`）。仇恨范围 `FOLLOW_RANGE=60`。

**狂暴机制（计时+刷新，2026-08-05 改百分比）**：`customServerAiStep` 判断 `rageCondition = 血量 ≤ 最大生命×50%（半血，随难度生命联动）|| 战斗开始后100tick`。满足且未狂暴 → `enterRage()`（播 `WARDEN_AGITATED` 音效 + 移动速度 +0.2），设 `rageEndTick=tickCount+120`（**持续 6 秒**）；已狂暴 → **刷新** `rageEndTick`；条件不满足且计时到 → `exitRage()`（移除速度修饰器）。`isRaging()` 返回 `tickCount < rageEndTick`（服务端读）。半血触发：困难 200 / 普通 150 / 简单 100。

**属性（2026-08-05 难度机制落地）**：**困难模板** = 400 血 / 50 攻 / 护甲 0 / 步高 2 / 击退抗性 1 / 跟随 60。**世界难度缩放**（YizxianMob 基类 `difficultyMultiplier`）：困难 1.0 / 普通 0.75 / 简单+和平 0.5；原版**只缩生命+攻击**（移速/击退/跟随/步高不变），**已损生命按比例缩放**（保持血条比例），**难度切换由 `DifficultyChangeEvent` 事件驱动**统一重算（无每 tick 检测，YizxianMod 遍历所有维度）。另挂 15 个 yizmodqzk 自定义属性（基值 0，8 个经 `EntityAttributeGate` 分配受保护值且**均为困难模板**：攻击强度60/法术强度100/吸血10/格挡1/减伤25/无敌帧16/攻击强度防御15/法术防御15；缩放后**正值最低 1 点、格挡三档保持 1**，0 值属性不钳成 1；绝妄生机率/绝妄生机时间=0；全伤害/近战/远程/闪避=0）。**防御镜像已泛化到实体**（YizxianMob 值变化时调 tizMod.mirrorArmor/mirrorSpellDefense → 实体也有原版护甲+韧性，困难 15/15）。**最初梦幻 = 攻击力×20% 已实现**（aiStep 派生同步，攻击力缩放后自动跟随：困难10/普通7.5/简单5）。**伤害 = 攻击力倍率**（反击 ×1.7 / 普通 ×(0.5~0.8) / 重击 ×(0.9~1.3)，随难度缩放）。**创造旁观后门**（YizxianMob.isObserver=创造玩家，辖界者单点/范围 AoE/反击锁定全跳过该玩家）。**爆炸击退免疫**（EXTERNAL_FORCE_PREFIXES 拦 Explosion 的 setDeltaMovement，爆炸伤害仍生效）。**实体移除保护**（拦 Entity.setRemoved+ServerLevel.addFreshEntity/addDuringTeleport，白名单=服务器保存/本模组死亡放行/本模组包，见 `entity-remove-protection.md`）。**传导限伤 + 血量外部哈希表（2026-08-07，flashfur 式）**：真实血量存 `SecureHealthClosure` 外部哈希表，辖界者 override `getHealth/setHealth/hurt/isAlive/isDeadOrDying/setPose/dropAllDeathLoot/handleEntityEvent/die` 完全接管——setHealth 扣血重定向 hurt、先衰减（DAMAGE_REDUCTION/BLOCK）再 `min(衰减后, maxHealth×CONDUCTION_CAP%)`、受击 CD=CONDUCTION_INTERVAL（20tick）。寰宇支配之剑（InfinitySwordItem 自实现 die 直接 dead=true+掉落+倒地）无法秒杀。详见 `conduction-health-protection.md`。详见 `entity-attribute-gate.md`。Boss 血条「辖界者」+ 半血前狂暴。

**网络阻断**：maven.neoforged.net 被 TLS 阻断，构建依赖本地离线配置（`local-maven-repo` + build.gradle 本地仓库 + gradle/neoform 缓存），方案见 `neoforge-maven-offline-build.md`。**勿删 `local-maven-repo` 和 `dl-deps`**。

## Warden 动画复用方案（通用，可复制到其他实体）

原版实体动画 = **AnimationState + KeyframeAnimations**，三层同步：
1. 实体持 public `AnimationState` 字段（`net.minecraft.world.entity.AnimationState`，不是 client 包）：attack/sonicBoom/roar/digging/emerge/sniff。
2. 服务端→客户端触发：
   - 姿态动画：`setPose(Pose.ROARING)` → SynchedEntityData 同步 → 客户端 `onSyncedDataUpdated` 检测 `DATA_POSE` → `state.start(tickCount)`。
   - 事件动画：`level().broadcastEntityEvent(entity,(byte)4/62)` → 客户端 `handleEntityEvent` → `state.start(tickCount)`。
3. 模型播放：`this.animate(state, WardenAnimation.WARDEN_ATTACK/ROAR/SONIC_BOOM, ageInTicks)`（WardenAnimation 在 `net.minecraft.client.animation.definitions`）。

**前提：模型骨骼名必须匹配关键帧引用**。WardenAnimation 用 `body`(不是 torso)、`left_ear`/`right_ear`、`head`、`left/right_arm`、`left/right_leg`、`left/right_ribcage`。Blockbench 导出的辖界者用 torso/head2，需重命名。Warden 触须动画用 `entity.getTendrilAnimation(partialTick)`（byte 61 置 tendrilAnimation=10，每 tick 递减）。

**音爆（Sonic Boom）**：原版 1.21.1 **无弹射物实体**，纯射线：`ParticleTypes.SONIC_BOOM` 沿射线 + `WARDEN_SONIC_BOOM` 音效 + `target.hurt(damageSources().sonicBoom(owner), 10)` + 击退；起点 `EntityAttachment.WARDEN_CHEST`。辖界者已移除音爆，此知识保留备用。

## 新窗口待办（用户接下来要加机制）

- **🚦 当前状态（2026-08-07 03:40 最终，下一会话从这里接）**：
  - **辖界者 = 原版血量**（400 HP）+ 原版 ServerBossEvent 血条 + 原版死亡（死亡移除保护 `allowDeathRemove` 已恢复）。BigDecimal 自定义生命值**已整体回退**（用户要重新写）。
  - **反击递归门禁已恢复**：YizxianMob 静态 ThreadLocal `COUNTER_RECURSION_GUARD`（`isCounterInProgress/beginCounterWindow/endCounterWindow`）+ QuanshouzheEntity.hurt 贴脸反击检查——**负生命值/不死实体互击防 StackOverflow**（用户明确：之后还会存在负生命值场景，此门禁必须保留）。
  - **⚠️ AttributeGate `isCallerTrusted` 已修复（本会话最后改动，已验证 0 拒绝）**：从「全栈区段检查（任一帧非信任即拒）」改为「**只看第一个决定性调用者**」。原全栈检查被外部 mod 注入引擎 tick 链的 mixin 帧误伤 → Boss `applyEntityAttributes` 9 属性全被拒、**实体编辑器 `entity_attribute_editor`（右键辖界者）写入被静默拒绝**。修复后编辑器可正常编辑、Boss 受保护属性正常应用。⚠️教训：调用栈鉴权别用"全栈任一帧非信任即拒"，外部 mixin 注入引擎帧会误伤本家正常路径。
  - **下一步待办：真正的生命值保护（用户重写自定义生命值）**。设计参考（已回退但可复用）：`workspace-files/.context/custom-health-bigdecimal-design.md`——BigDecimal 真值 + float 投影、真值只走 hurt 精确捕获（damage container）、外部 setHealth/heal/die 忽略（独立系统）、+500 余量验证、踩坑清单（投影差值精度丢失 / Gradle run/mods 隐式依赖 / @Redirect 描述符）。

- **以下为已回退的 BigDecimal 自定义生命值历史实现，仅供设计参考，代码中已不存在**：
  - **完整实现（2026-08-07，验证后回退）**：真值 `yizxianHealth/yizxianMaxHealth`(BigDecimal) 只走 hurt 伤害路径（前置库 setHealth mixin 从 damage container 捕获精确 f1 → BigDecimal 减伤 → pendingFinalDamage → YizxianMob.setHealth 真值扣减）；外部 setHealth/heal/die 全忽略；customMax=Float.MAX_VALUE+500，测试剑命中后真值精确剩 500；自绘 Boss 血条 + 服务端同步文本 + Jade 兼容 + NBT 持久化 + 伤害粒子抑制。踩坑：Gradle `fileTree("run/mods")` 编译依赖命中 copy 任务输出报隐式依赖错（用 libs/ 复制）；`@Redirect` 描述符必须完整（sendParticles 返回 `I` 非 `V`）。
  - **第 1/2 步**：isDeadOrDying 恒 false（已回退为原版死亡）；isAlive 契约（已回退）；反击递归修复（此门禁保留，见上）。
- **当前会话 YizxianMob 状态快照（2026-08-07 收尾）**：
  - 动量门禁升级：`tool/ExternalCallGuard` 全栈区段白名单检查 + MixinMerged 识别（本家包/引擎帧/爆炸排除），`YizxianMob.motionGate` 复用 → 拦截外部速度/动量/位置注入
  - 击退免疫：`onKnockback` v>0 全拦（knockback 方法）；「拦截 setDeltaMovement/addDeltaMovement」的 `KnockbackImmunityMixin` **已回退删除**
  - 无视碰撞：`NoCollisionMixin` v>0 完全无碰撞（push/canCollideWith/isPushable 全拦）
  - 药水免疫：`YizxianMob.potionImmunity` 静态开关（默认 true 全免疫，可临时关测试）
  - `EntityAttributeGate.isCallerTrusted`：~~全栈区段 + MixinMerged 鉴权~~ → **已改回「只看第一个决定性调用者」**（2026-08-07，全栈检查误拒编辑器/Boss 属性应用，见上方当前状态）
- 常用命令：改代码后 `cd D:\ZM\yizgzq\yizxian1.21.1 && ./gradlew.bat compileJava --no-configuration-cache` → 后台 `runClient`（CLAUDE.md 规则：不问直接启，90 秒查日志）。
