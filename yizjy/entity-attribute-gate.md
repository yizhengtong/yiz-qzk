---
name: entity-attribute-gate
description: "前置库受保护实体属性维护设施（EntityAttributeGate + AttributeInstanceMixin 防移除）+ 辖界者接入方式。给实体加 yizmodqzk 自定义属性必读；无敌帧/闪避已泛化到实体（LivingEntityMixin hurt 统一路径）"
metadata:
  type: project
---

# 受保护实体属性维护设施（P0 落地，2026-08-05）

## 背景
前置库 12 个自定义属性（攻击强度/法术强度/全伤害/近战/远程/伤害减免/格挡/无敌帧/闪避/吸血/攻击强度防御/法术防御）原本只挂 `EntityType.PLAYER`。已适配到辖界者实体；设施通用，未来其他实体复用。

## 设施（前置库 yiz1.21.1，已 build 通过）

1. **`tool/attribute/EntityAttributeGate.java`** — 受保护属性维护门禁：
   - 受保护 modifier id 前缀 `prot_`，完整 id `yizmodqzk:prot_<idKey>`（与物品的 `item_/attr_`、实体级 `entity_`、装备汇总 `sync_` 前缀互不干扰）
   - `set(entity, attr, idKey, value)`：鉴权后 remove + addPermanentModifier；value=0 仅移除。`remove(entity, attr, idKey)`
   - `isCallerTrusted()`：**调用栈+包名鉴权**——从栈顶跳过框架帧（门禁类包 / mixin 注入包 / 目标类 `AttributeInstance`），对第一个决定性调用者判定：本家包 `net.minecraft.client.yiz.*`（前置库+所有下游共用此包根）→ 放行；引擎帧（`net.minecraft./net.neoforged./com.mojang.`）→ 放行；`@Mod` 注解 modid ∈ 白名单 `{yizmodqzk, yizxianmod}` → 放行；其余拒绝
   - **坑**：栈回溯必须显式跳过 `AttributeInstance` 类，否则外部模组调 `removeModifier` 时第一个决定性帧是它（net.minecraft. 引擎包），会被误判为引擎放行。过滤逻辑参照 `YizxianMob.motionGate()`

2. **`mixin/AttributeInstanceMixin.java`** — 防外部移除：
   - `@Inject removeModifier(Lnet/minecraft/resources/ResourceLocation;)Z`（**必须用完整描述符**，因 `removeModifier` 有 (AttributeModifier) 与 (ResourceLocation) 两个重载）
   - **该方法是所有移除路径唯一汇聚点**：`removeModifier(AttributeModifier)`、`removeModifiers()`、`addOrReplacePermanentModifier` 内部都委派到它
   - 对 `prot_` 前缀 id 做 `isCallerTrusted()`，非受信任调用者 `cir.setReturnValue(false)` 当场拒绝；非 `prot_` id 直接放行（零开销）
   - 已注册进 `yizmodqzk.mixins.json`

## 辖界者接入（下游 yizxian1.21.1）
- `QuanshouzheEntity.createAttributes()`：原版改为 400血/50攻/护甲0/步高2（移速0.30/击退抗性1/跟随60 保留）；12 自定义属性基值 0 全挂载
- `QuanshouzheEntity.applyEntityAttributes()`（override）：经 `EntityAttributeGate.set` 分配 8 个受保护值——攻击强度60/法术强度100/吸血10/格挡1/减伤25/无敌帧16/攻击强度防御15/法术防御15（2026-08-05 二次调整）；全伤害/近战/远程/闪避=0
- `YizxianMob`：加 `applyEntityAttributes()` 模板方法 + `yizxianAttrsApplied` 标志，`aiStep` 服务端分支**第一 tick 调用一次**（防重复覆盖外部临时 buff）。新实体继承 `YizxianMob` 后覆写该方法即可分配

## 生效范围（已实测/推演）
- 伤害消费全在前置库 `LivingEntityMixin`，从任意 LivingEntity 读属性（null 安全）——攻击强度/减伤/格挡/吸血/双防指数减伤**挂上即生效**
- 防御「指数减伤」（armor/spell_defense 在 hurt 层即时读值）实体生效；`mirrorArmor/mirrorSpellDefense`（1:1 镜像原版护甲/击退韧性）**已泛化到实体**（2026-08-05：YizxianMob 服务端分支值变化时调 tizMod.mirrorArmor/mirrorSpellDefense，辖界者也有原版护甲+韧性，不再只玩家生效）

## P1 已完成（2026-08-05）：无敌帧 / 闪避泛化到实体
- `AttackInvulnerabilityTracker` 签名由 `Player` 泛化为 `LivingEntity`：`onHurtHead` / `onHurtSuccess` / `onTick` / `clear`
- 桥接统一放 `LivingEntityMixin.hurt` HEAD/RETURN——玩家 override 的 `Player.hurt()` 最终调用 `super.hurt()`（LivingEntity.hurt），**玩家与实体共用一条路径**，不再双掷骰；`PlayerMixin` 只留 `FE_INVULNERABLE_DATA` 手动无敌字段
- 到期检查由 `LivingEntityMixin.onTick` 服务端分支处理，不再走 `PlayerTickEvent`
- 辖界者 `invincibility_mult=16` 已生效（受击后 16 tick 完全无敌）

## 遗留注意
- **狂暴条件**：辖界者血改 400 后 `血量≤600` 恒真，狂暴实际由"战斗5秒"驱动；要半血狂暴需改 `≤200`（见 [[warden-animation-reuse]]）
- **编辑工具**见 [[entity-attribute-edit-tool]]

## 新增绝妄生机 / 特殊伤害属性（2026-08-07 更名自「禁疗」，完整体系见 `vitality-severance.md`）
- 注册（全部 `setSyncable`）：`vitality_severance_rate`（绝妄生机率 0~100）、`vitality_severance_time`（绝妄生机时间 秒）、`first_dream`（最初梦幻 ≥0）；PLAYER 挂载 + `EquipmentAttributeSync` 追踪 + lang/ItemAttr/Editable 三处同步
- **消费点 `LivingEntityMixin.onHurtReturn` 攻方段**（任意 LivingEntity 攻击者生效）：统一走 `VitalitySeverance.apply(attacker, target)`（绝妄生机率 → 目标百分比禁疗、绝妄生机时间 → 完全禁疗 N 秒、注册表聚合），不再分散写
- 最初梦幻 → 通用攻击方消费 `EntityASMUtil.applyDreamDamage(attacker, target)`（见下方「自研血量实体通用处理」节）
- 辖界者已挂载（基值 0，数值可编辑工具调）；辖界者攻击另有叠加效果（每次 +5% 上限 100%，见 vitality-severance.md）

## 自研血量实体通用处理（2026-08-05 后期，针对 Legendary-Monsters 等 totalDamageTaken 型血量）
- **问题**：自研血量实体（override `getHealth()` 不走原版，如 LM 的 `getHealth = maxHealth - totalDamageTaken`）对 Delta/受击消费有三大坑：
  1. **伤害必须不依赖目标 hurt**：LM 免疫/无敌期间 `hurt()` 返回 false 不调 super.hurt → `onHurtPre`（super.hurt 注入）不触发 → 攻方属性消费不了。
  2. **Delta 会衰减**：原 onTick 每 100 tick 衰减 1 点 → 扣血回弹。**已参照 mhzy（梦幻终焉）改为全局不衰减**（删衰减块）。
  3. **Delta 被清**：`modifyHealth` 第 2/3 层（`HealthChannelScanner` / `DirectHealthFallback`）会遍历所有 Float DataParameter 把 `FE_GET_HEALTH_DATA`（delta 通道）用 `max(0,..)` 归 0 → 回弹。**已修：第 2/3 层都排除 `DELTA_ACCESSOR_ID`**。
- **`EntityHealthLocator`（tool/health/，全能扫描）**：实体类型首个实例时用「hurt 0.01% maxHealth 触发 + 字段快照偏移匹配」定位真实血量字段（如 `totalDamageTaken`），缓存 `config/yizmodqzk/entity_health_slots.json`；`applyPersistentDamage` 反射直改字段持久扣血。**坑：`resolveField` 必须沿父类链查找**（`totalDamageTaken` 定义在 `IAnimatedBoss`，`getDeclaredField` 只查当前类会漏，导致一直定位失败）。
- **⚠️ 扫描误判伤害累积字段为血量槽（2026-08-05 修复）**：scan 的「受击时变化量 ≈ 微小伤害」匹配会把**任何受击必变的 float 字段**当成 totalDamageTaken 型血量槽，包括：LivingEntity 基类 `lastHurt`（受击=amount）、Cataclysm `damageBucket`（转阶段伤害桶）、假人 `totalDamageTakenInCombat`（累计受击计数）——**都不是血量字段，写入不影响 getHealth**。后果：`applyDreamDamage → applyPersistentDamage` 命中假槽返回 true → 不再 fallback Delta → 最初梦幻打不动这些实体（**每个实体第一次打有效，误判缓存后永久失效**，缓存持久化到 json 重启仍在）。症状「LM 能改了、原版/假人/焰魔改不了」。**修复三层**：①`collectNumericFields` 跳过 LivingEntity 及以上基类字段（排除 lastHurt 等通用干扰）；②scan 候选加 `isRealHealthField` 语义验证（按槽语义写一次探测字段，检查 `getHealth()` 是否同步下降 ≈delta，探测后回滚，false 则排除不缓存）；③`applyPersistentDamage` 写入后验证 `getHealth()` 下降（不下降回滚+`CACHE.remove`+`save()`+返回 false 让调用方回退 Delta，历史误判槽自愈）。**教训：偏移匹配只是「受击变化的字段」，不等于「血量字段」；唯一可靠判据是写入后 getHealth 是否真的变。** **已验证（2026-08-05 runClient）**：打 Ignis（焰魔）日志 `[Apply] Ignis_Entity slot=null`（damageBucket 假槽被运行期剔除）→ fallback Delta → delta 累计 -190→-210、血量掉到 0 打死；json 里 Ignis 条目被自动清掉。辖界者/假人同理走 Delta。
- **⚠️⚠️ 写入验证的低血量 clamp 漏洞（2026-08-05 二修）**：③的验证 `healthBefore - healthAfter ≥ amount×0.5` 在**目标快死时误判假槽**——totalDamageTaken 型 getHealth 被钳到 ≥0，下降量被 0 下限截断（3 血写 10 → 只降 3 < 5）→ 回滚删缓存 + fallback Delta（对 LM 无效）→ **最初梦幻对 LM 最后一段血失效（卡血卡好久）**，且反复删缓存/重 scan。**已改 `≥ min(amount×0.5, healthBefore)`**：低血量时只要写入让血量下降/归零即算有效。**已验证**：TheObliterator/PossessedPaladin 重新定位 totalDamageTaken 并缓存成功（json 恢复 3 条 LM 槽）。
- **通用攻击方消费 `EntityASMUtil.applyDreamDamage(attacker, target)`**：读攻击者 `FIRST_DREAM` → 自研血量实体直接改字段 + `VitalitySeveranceConfig.set(100%)` 永久绝妄生机（堵死目标回血弹回，配合字段级禁疗 `enforceFieldTick`）；原版走 Delta。调用点：`Player.attack`（AttackInterceptorMixin）+ `Mob.doHurtTarget`（MobMixin）+ 辖界者 `hit`（下游）——**前置库通用，别的模组攻击入口可复用**。破时只在 `onHurtPre` 清目标无敌帧。
- **Agent（LivingHealthTransformer）限制**：`COMPUTE_FRAMES` 对 mod 类（LM）默认 loader 失败（TypeNotPresentException）；用 mod loader 重算帧会**启动时序崩溃**（swim_speed unbound）；`COMPUTE_MAXS` 下分支注入（破时 hurt）VerifyError。故 fallback **安全模式**（只注入值替换类：getHealth/heal/isAlive/setHealth），**破时绕过（hurt 注入）对 mod 类无效**，仅对默认 COMPUTE_FRAMES 成功的简单 mod（cataclysm）生效。

## 关联
- 属性显示名三处同步纪律：[[attribute-display-name-sync]]
- 物品 modifier id 冲突：[[item-modifier-id-collision-stacking]]
