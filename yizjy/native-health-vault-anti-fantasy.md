---
name: native-health-vault-anti-fantasy
description: "fantasy 随梦骨头 Unsafe 三源清零对抗（进行中 2026-09-03）：攻击链、四层防御演进、NativeHealthVault 堆外方案现状与诊断结论。跨会话必读——新会话接续 native 对抗。"
metadata:
  type: project
---

# fantasy Unsafe 三源清零对抗 —— NativeHealthVault 方案（进行中 2026-09-03）

> **接续必读**：本会话开发环境装 Jade（`run/mods/Jade.jar`，生产同源）读辖界者血条验证"对外透真"。fantasy=mhzy-main（`D:\ZM\yizgzq\1.20.1\mhzy-main`，modid fantasy_ending）。诊断用生产 debug.log：`D:\桌面\.minecraft\versions\1.20.1-Forge_47.4.22\logs\debug.log`（中文 GBK 乱码但时间戳/栈可读）。

## ✅ 2026-09-03 晚 生产复现判定：根因 = **存档判死毒链**（非 fantasy 清 native），修复已部署待验证

**复现（用户生产加载原毒存档 20s，全程无 fantasy 参与）**：debug.log 19:05:09-10 记录：
- `表初始化` 一次（base/cap/seed 未变 → fantasy 没改元数据）；`get-槽不存在(空)`(size=1) 出现于辖界者读档 super 阶段 = **正式 UUID 尚未入槽**；
- 随后 `.388 大幅扣串 400.0→0.0 (uuid=e99a63aa…)` + `.389 setHealth(0.0)` = **我们读档链写 0**；之后每 tick `get-命中槽值为0`(size=2, 校验通过)= 两只辖界者 native 槽**存在**但值=0（size=2 排除注册断，校验通过排除 fantasy 裸写 native → **A/B 两候选都排除**）。

**毒链**：上一会话 Boss 被清 0 后 `addAdditionalSaveData:969` 把 `boss_health=getHealth()`=0 落盘 → 本次读档 `QuanshouzheEntity.readAdditionalSaveData:981` 静态 `SecureHealthClosure.setHealth`（**无条件、无 YizxianMob.setHealth 治疗向守卫**）把 0 反灌 native → 权威 0 → 每 tick 判死、存档续毒。旁证：`.310 setHealth(400) uuid=65564019…` = `<init>` 在**临时随机 UUID** 下写 native → 读档 setUUID 换正式 UUID 后成**幽灵槽**（400 白白留在不可达槽，每载入 +1 幽灵）。曾怀疑的「fantasy 活体清 0 / 读表指针清 native」未在本次复现出现——**此前所有"击杀成功"很可能都是这存档毒在鞭尸已判死 Boss**。

**修复（已实施，仅改下游 yizxianmod，build+reobf+部署生产 19:13，md5 67de9c67…）**：
1. `QuanshouzheEntity.readAdditionalSaveData`：恢复 `boss_health` 前消毒——`hp<=0` → 改信 `SecureHealthClosure.getHealth(this)`（此时 obf 已从 `yizxian_obf_health` 恢复，实证=400）；仍 ≤0 → `secureMaxHealth()` 满血。阻断读档反灌 0。
2. `YizxianMob.registerSecureHealth`：中毒判据 `v<0`→`v<=0`（0 也视为中毒，防任何受保护实体从毒 obf 以 0 起种 native，防线兜底）。

**待用户生产验证**：加载**原毒存档** → 两只辖界者应复活满血（现有存档一次加载即自愈：消毒→native 400→enforce 拉正 obf/表/容器→下次存档 boss_health=400）；复活后**再测 fantasy 随梦骨头打活体 Boss**——native 能否扛住真实活体攻击 = 剩余唯一问题（本次复现无 fantasy，尚未验证）。幽灵槽（<init> 临时 UUID 写 native）未修，仅 +24B/载入，容量 512 兜底，低优先。

## 🎯 2026-09-03 深夜 战略发现：fantasy coremod ASM 改写我们 LivingEntity 子类（实体类内打补丁证伪）+ 用户拍板

**实锤**：fantasy `FantasyEndingCore` 用 `Instrumentation.addTransformer(transformer,true)`（可 retransform）在**类加载期**改写**所有 super 链含 LivingEntity 的类**的 `getHealth()/isAlive()/isDeadOrDying()`（`coremod/FantasyEndingMixinPlugin.java:129-132` → `SoftGetHealthClassVisitor`）。我们 YizxianMob/QuanshouzheEntity 全在覆盖内 → **运行时字节≠源码** → 解释了 3 轮复现所有"不可能"：单发 capped hurt 却 400→0、killer 刀永远打不出诊断、NaN 守卫不触发（NaN 假设证伪，20:35 复现 amount 有限 6.96）。实体类内打补丁（拒 NaN/诊断/并发 guard）= **无效路线，已证伪**。

**防线不对称（决胜点）**：fantasy 只按 LivingEntity 子类改 3 方法；我们**纯类**（SecureHealthClosure/NativeHealthVault/AgentBridge/守护线程）不在其改写面 → native 400 一直准。**关键决策必须放纯类**。

**用户拍板**：①辖界者**合法磨死保留**（玩家正常刮痧磨死=真死保掉落）；②研究方向 = 研究能否"替换类指针等让外部完全无法用 agent/coremod 动我们代码" → 已产出研究报告 `workspace-files/.context/anti-coremod-armor.md`：JVM 层无 API 物理免疫，可行=改写了无效(权威真值入堆外/纯类)+可检出+可自愈(agent 已能 retransform)。

**推荐落地（待用户确认实施）**：A. `SecureHealthClosure.nativePut`（纯类未被改）加**秒杀判定**：受保护 boss 近窗口(2s)曾在 >50%max 而本次 single 写落 ≤0 → 拒/钳到 1（外部秒杀特征）；渐进刮痧在低位落 0 → 放行保掉落。B. immortalGuard（YizxianMob.java:557 独立线程）扩到 secure boss：native>0 而实体被判死 → 撤死亡态。改前置库 yizmodqzk → publishToMavenLocal+reobfJar+清 deobf 缓存+下游 clean build。

## 🏆 2026-09-03 21:00 决战胜利：jar 字节码自还原 v2 挡住 fantasy 骨头击杀（用户生产确认"血量没被改动了"）

**结果**：v2（保护集 = YizxianMob + QuanshouzheEntity，yizxianmod 20:57 md5 ca605d83）部署后，fantasy 随梦骨头对辖界者的击杀被**彻底挡住**，血量不再被动。

**定论（4 轮生产复现 + 3 种修复策略收敛）**：
- 击杀向量 = **fantasy 在类加载期对 QuanshouzheEntity（LivingEntity 子类）的 ASM 改写**（SoftGetHealthClassVisitor 全子类覆盖），叠加自家 LivingHealthTransformer 在 hurt/die 内 `this.getHealth()/isAlive()/isDeadOrDying()` 调用点的 specialGetHealth 注入——两者配合把判死喂给 hurt/die，表现为"单发 400→0 + 不打 QZK 诊断"（源码数学上不可能，全是运行时字节被改）。
- 还原 **YizxianMob** 不够（其 3 方法补丁非元凶）；还原 **QuanshouzheEntity** 后击杀消失 → 实锤。
- 附带效果：QuanshouzheEntity 上自家 LivingHealthTransformer 的调用点注入被 jar 还原一并去掉（hurt/die 恢复纯源码含 NaN 守卫/致死诊断）。

**遗留/待确认**：①合法磨死是否保留（需玩家正常刮痧确认仍能打死 + 掉落）；②免移除/拉回等存在性保护未受影响（它们在其它类/机制，理论不受影响）；③幽灵槽（<init> 临时 UUID 写 native）未修，低优先；④NaN 守卫/致死诊断保留无妨。

## ✅ 2026-09-03 深夜 实施：jar 字节码自还原防线（参考 buer）已部署生产待复测

用户方向=「外部改代码时直接从 jar 还原字节码」，给参考 jar buer-1.0.5fix（Grae）。已按 buer 机制落地（仅 v1=还原 YizxianMob）：
- **yizmodqzk** `core/asm/YizRestoreTransformer`（新）：受保护类每次 transform pass 返回 **jar 原始字节**（transform 传入 loader 的 getResourceAsStream 读 jar，缓存）；canRetransform=true、注册晚于外部 coremod → 链末执行 = 终态。限制：不能改结构（fantasy 仅改方法体，适配）。
- **AgentBridge** 加 `registerSelfRestore(internalNames)`（注册 transformer + 主动 retransform 已加载受保护类拉回）+ `selfRestoreWatchdog()`（周期重拉回，防 redefineClasses 绕过；2s 节流）+ `isSelfRestoreRegistered()`。
- **yizxianmod YizxianMob**：静态 `SELF_RESTORE_NAMES`={YizxianMob internal 名}（fantasy 改的 getHealth/isAlive/isDeadOrDying 全声明于此、且未被自家 agent 注入 = 还原零副作用）；`armSelfRestore()` 在 registerSecureHealth（首服务端 aiStep）调，agent 未就绪复位重试；aiStep 每 ~64 tick 调 watchdog。
- 部署：yizmodqzk 20:50 md5 a82aa9a3 / yizxianmod 20:51 md5 7b639f4b（产物含新类已验证）。

**设计要点**（镜像 buer，见 `workspace-files/.context/anti-coremod-armor.md` §3.5）：自家还原 transformer 注册晚于敌方→每 pass 返回 jar 字节盖外部注入；注册后主动 retransform 已加载类一次；周期 watchdog 兜底 redefine 绕过。

**待用户生产复测**：同配方（骨头砍→血条0不消失→补一刀），预期：armSelfRestore 后 YizxianMob 被还原读 native → 血条不再被压 0 / 补刀不再秒杀。日志应见 `[YizRestore] 自保护还原` + `[AgentBridge] 自保护还原 transformer 已注册`。若仍被杀 → 扩展还原目标到 QuanshouzheEntity（需处理自家 LivingHealthTransformer 注入共存）或加写侧兜底。

## 🔬 2026-09-03 20:18 第二轮生产复现：读档修复生效，活体击杀 = NaN 伤害补刀穿透

**用户复现**（骨头当近战武器砍 Boss，基础形态测）：fantasy 骨头左键=KillEntityTrigger 连发 3 命令（forceKill→actuallyHurt INF / forceSetHealth 0.0 / softGetHealthKill→delta -inf+isDead，见 mhzy-main `cb/Triggers.java`+`ForceKillCommand.java`，**不走 entity.hurt()**，直接 setHealth/DataItem VarHandle）。

**确认的防守成功**：
1. ✅ 读档判死毒链修复生效——3 只辖界者加载即 400 满血（此前 0）。
2. ✅ fantasy 骨头命令**即时打击被防住**：native 稳 397.7（server `specialGetHealth` 值=397.7），客户端被 delta=-inf 压到 ~0（血条显示 0），boss 不自行移除（tickDeath 表值>0 拦截）。

**剩余漏洞（fantasy 仍能杀死）**：血条 0 后**用户手动补一刀** → native 397.7→0（单发，走我们自己 hurt():843 → setHealth），boss 进 tickDeath 表值=0 放行死亡（removed=false 卡尸体无复活——基础形态 isPresenceProtected=false 不死守卫不跑）。**机制判定：单发 397.7→0 在 cap=100 下数学不可能 → NaN 穿透**（amount=NaN→min(NaN,cap)=NaN→max(0,current-NaN)=NaN→SecureHealthClosure.setHealth 把 NaN clamp 成 0）。且致死刀没打 QZK-HURT 诊断（jar 行号表 843→847→850 无断档）→ 推断 847 `EntityActuallyHurt.catchSetTrueHealth` 对毒化数据抛异常中断于诊断前。

**修复已部署（yizxianmod 20:32 md5 116f2492）**：①hurt() 顶部拒 NaN/Inf amount（`[QZK-KILL-GUARD]` + 栈）；②QZK-HURT 诊断对致死刀(表>0→≤0)/非有限 amount **无条件打**（含 amount bits）；③hurt() 845-847 吸血/catchSetTrueHealth 包 try/catch 保证必达诊断。fantasy creative bone 是普通 Item 无攻击力 override——NaN 来源待 [QZK-KILL-GUARD] 日志确认（疑骨头 onEntitySwing/客户端逻辑注入）。

**待用户复测**：同配方（骨头砍一次→血条0不消失→补一刀），看补刀是否还死 + 发日志（若 `[QZK-KILL-GUARD] 拒绝非有限伤害` 出现=NaN 实锤且已拦；若 lethal QZK-HURT 仍打出即捕获真实 amount/cap）。若 NaN 不是真向量，此修复只拦一半，需按新诊断继续。

## 攻击链：随梦骨头 `creative_bone` 击杀

`KillEntityTrigger` 连发三条服务端命令（均 `requires cs.hasPermission(2)`）：
1. `fantasy_ending entity forceKill <uuid>` → `ForceKillCommand` → fantasy `EntityActuallyHurt.actuallyHurt(…, POSITIVE_INFINITY, true)` → `entity.setHealth(NEGATIVE_INFINITY)` + `catchSetTrueHealth(NEGATIVE_INFINITY)`
2. `fantasy_ending entity forceSetHealth <uuid> 0.0` → `ForceSetHealthCommand` → fantasy `catchSetTrueHealth(living, 0.0)`（按实体血量 DataItem 索引 `set()` 直写 DataItem，绕过 setHealth override）
3. `fantasy_ending entity softGetHealthKill <uuid>` → `SoftGetHealthZeroCommand` → `EntityASMUtil.setHealthDelta(-inf)`（fantasy FE_GET_HEALTH_DATA delta 通道）+ `((LivingEntityEC)entity).uom$livingECData().isDead = true`（平行死亡标记）

**本质**：fantasy 逆向本模组类结构后，用 Unsafe/IMPL_LOOKUP **直改内存**（DataItem.value / CHM 内部 Node / 实例 float 字段 / 甚至 native 槽），绕开全部 Java override/mixin/调用栈鉴权/agent 覆盖。

## 四层防御演进（每层都被 fantasy 下一轮逆向攻破）

1. **混淆串 DataParameter + 表 + 容器三层**：fantasy 一次同步清零三层（表/容器/混淆串，日志 directDec=0.0 实证）。
2. **回表优先**（getHealth 服务端读表）：fantasy 改读 CHM 内部（ProtectedHealthMap 覆写 put 被 `IMPL_LOOKUP.unreflectSpecial 锁基类` 绕过）。
3. **NativeHealthVault 堆外 native**（本次会话新写）：`D:\ZM\yizgzq\1.20.1\yizmodqzk\src\main\java\net\minecraft\client\yiz\tool\health\NativeHealthVault.java`——Unsafe.allocateMemory 开放寻址哈希表（UUID→float 真值 + checksum），fantasy 全堆扫描到不了 native。SecureHealthClosure 服务端 getHealth native 优先 + enforceFromNative 每 tick 拉正镜像 + registerAuthority/setHealth 双落 + YizxianMob 服务器停止 freeAll。

## ⚠️ 诊断结论（关键，决定下一步）

增强诊断（`[NativeHealthVault]` 日志，含 get-槽不存在/值0/校验失败/元数据被改）在**最后两次生产复现**结果：
- 表初始化**仅一次**（base/cap/seed 从未变）→ fantasy **没改元数据**（tableBase/capacity/seed）。
- `get-命中槽值为0` 且**非"校验失败"**（size=1 校验通过但值是 0）→ 值是 0 且校验通过 = **我们自己的 nativePut 写的 0**（fantasy 不知 seed 无法伪造校验），说明 getHealth 某刻回退镜像读到 fantasy 清的 0 → hurt/setHealth 用 0 算 next=0 → nativePut 写 0。
- 但增强版「槽不存在(空)」日志加在**最后一次部署后**，用户尚未复现——**需新会话让用户复现一次拿"槽是否存在"判定**：若辖界者 registerAuthority 后槽不存在 → 注册链路断（nativePut 没写进 / 槽被删）；若槽在且 400 但 get 回退镜像 → 读路径问题。

**悬而未决根因候选**：
- A) 辖界者 registerAuthority→nativePut 是否真的把 400 写进 native（表 init 由 50.660 put 触发，但该 put 是否辖界者？size 何时变 1？需空槽诊断确认）。
- B) fantasy 是否仍能通过读 Java static `tableBase` 指针 → 算槽地址 → Unsafe 直写 native 槽（能读指针则 native 内容可被清，需指针本身藏 native/隐藏类，形成鸡生蛋）。

## 本地开发环境已就绪

- `run/mods/Jade.jar`（dev 生产同源）验证辖界者血条透真。
- 两 jar 部署：生产 mods `D:\桌面\.minecraft\versions\1.20.1-Forge_47.4.22\mods\` + `D:\ZM\yizgzq\1.20.1\jar\`。
- 构建：前置库改 → compileJava → publishToMavenLocal → reobfJar（显式）；下游改 → clean build（auto reobf）；部署先删旧再复制。

## 相关
- [[fantasy-anti-tamper-battle]]（IMPL_LOOKUP 改表盲区，2026-08 放弃对抗转数值——本次重新对抗）
- [[health-map-tamper]]（锁基类 put 手法，攻击视角）
- [[quanshouzhe-mhzy-defense-1-20-1]]（血量保护主文档）
- [[bytecode-health-takeover-defense]]（字节码接管对抗）
