---
name: dev-prod-reflection-and-player-gate
description: "1.20.1 两个「开发好、生产坏」类坑的定性与修法：①反射字符串常量不会被 reobf 重映射（开发 DATA_HEALTH_ID / 生产 f_20961_）→ 只写 official 名会在生产静默返回 null，表现为「对外血量不刷新」；统一三级解析（official 名 → SRG 名 → 类型兜底）且全模组一处口径 + 失败必吼。②改血线公共闸门：目标是玩家一律只走普通伤害轨（原先只豁免创造/旁观 → 生存玩家被直改血+100% 禁疗，即「误伤玩家」）"
metadata:
  type: project
---

# 开发好 / 生产坏：反射名 + 玩家闸门（2026-09-21 生产反馈）

## 一、反射字符串不会被 reobf 重映射（本项目高频坑）

**事实**：`getDeclaredField("DATA_HEALTH_ID")` 里的字符串是**常量**，reobf 只改字节码里的字段/方法引用，
不会改字符串。开发环境（Mojang 映射）字段叫 `DATA_HEALTH_ID`，生产环境（SRG）叫 `f_20961_`。
**只写 official 名 = 生产必然 `NoSuchFieldException` → 静默返回 null → 调用方悄悄退化。**

### 这次的现场（生产「铁斗士血量对外不刷新」）

- `HealthChannelScanner.initVanillaHealthAccessor()` 只写 `DATA_HEALTH_ID` → 生产 = `null`。
- `YizxianMob.enforceSecureHealthState()` 每 tick：
  ```java
  var vanillaHealth = HealthChannelScanner.getVanillaHealthAccessor();   // 生产 null
  DirectHealthFallback.forEachFloatItem(this, (acc, cur, item) -> {
      if (vanillaHealth != null && acc.getId() == vanillaHealth.getId()) return;  // 守卫失效
      if (cur != 0.0F) { item.setValue(0.0F); item.setDirty(true); }             // → 把真血清零
  });
  ```
  ⇒ 每 tick 先把真血写进 vanilla 通道（`catchSetTrueHealth`），紧接着**又被清零** →
  其它模组读到的永远是 0/不变。**真实血量确实变了，对外展示不变；开发环境守卫生效所以正常。**

### 规则（再遇到「开发好生产坏」先查这个）

1. **反射取原版字段/方法一律写双名或做类型兜底**，推荐三级：
   `official 名 → SRG 名（f_/m_） → 按类型/签名兜底`。
2. **同一个东西只允许一处解析口径**：vanilla 血量通道统一用
   `DirectHealthFallback.VANILLA_HEALTH_ACCESSOR`（`HealthChannelScanner` / `EntityActuallyHurt` 均委托它），
   否则会出现「A 处拿到、B 处拿到 null」的半失效状态，比全失效更难查。
3. **解析失败必须吼**（`[HealthChannel] ⚠ 原版血量通道解析失败…`）并**成功也打一行带 id 的日志**，
   这类问题只有日志能证伪。
4. SRG 名查法：项目自带 `yizmodqzk/build/createMcpToSrg/output.tsrg`（dev→SRG 表），
   `Select-String -Path output.tsrg -Pattern "DATA_HEALTH_ID"` 即得 `f_20961_`；
   也可 `javap -p` 生产 SRG jar 看类型核对。
5. 已核对**本来就对**的：`EntityASMUtil`（`lastHurtByPlayer=f_20888_` / `lastHurtByPlayerTime=f_20889_` /
   `dead=f_20890_` / `dropAllDeathLoot=m_6668_`）、`DirectHealthFallback`（`itemsById=f_135345_` /
   `isDirty=f_135348_` / `onSyncedDataUpdated=m_7350_` + 类型兜底）。
6. **未动但记录在案**：`ComboAttackHelper` 的 `Player.class.getDeclaredField("attackStrengthTicker")`
   是死代码 —— 该字段（SRG `f_20922_`）声明在 **LivingEntity** 且未写 SRG 名，所以开发/生产都拿不到；
   修它会让连击的攻速刻度重置真正生效（改变行为），故只记录不改。

## 二、改血线对玩家：只走普通伤害轨

**规则**：目标是玩家时，整条「涨跌多空/改血」线不生效，只保留普通伤害（`hurt`）。
铁斗士本来就是这么设计的（调用点各写了 `if (t instanceof Player) continue;`）。

**这次的漏洞**：公共入口 `EntityASMUtil.applyProportionalDreamDamage` 只豁免
**创造/旁观**玩家（`isBackdoorExempt`），生存玩家仍会被整条线命中：
直改真实血量（绕过护甲与无敌帧）、`VitalitySeveranceConfig.set(target, 100f, 0)` 挂 **100% 禁疗**
（之后治不回来）、门控击穿、判死标记、跨阈值死亡链。表现就是「误伤玩家」。

**修法**：在 `applyProportionalDreamDamage` 开头加闸门 `if (target instanceof Player) return;`
—— 这是整条线唯一的公共入口（铁斗士 / 辖界者 / C2S 攻击包 `C2SDreamAttackPayload` 都汇到这里），
一处生效；`dreamDeathblow` 也补同样的门（防直接调用绕过）。调用点各自的门保留（省范围扫描）。
**注意**：C2S 攻击包是客户端指定目标 id 的，也就是说玩家之间的涨跌多空攻击同样收敛为「只伤害」；
若要恢复 PvP 改血，改的是这一处闸门。

## 三、自检清单

1. 生产日志应有 `[HealthChannel] 原版血量通道解析成功: f_20961_ (id=6)`；出现
   `⚠ 原版血量通道解析失败` 就说明这处口径又断了。
2. 生产用其它模组看铁斗士血量：受伤后应立刻变化（不再恒定 0/不变）。
3. 玩家被铁斗士/辖界者打：只掉普通伤害那份；**不应**出现「护甲没生效」或「之后回不了血」。

相关：[[120-blood-dataparameter-port]] [[quanshouzhe-mhzy-defense-1-20-1]] [[health-discovery-background-scan]] [[tiedoushi-launch-and-juggle]]
