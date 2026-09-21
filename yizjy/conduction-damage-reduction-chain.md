---
name: conduction-damage-reduction-chain
description: "本模组实体自管 hurt 不走 vanilla 链导致 5 种减伤属性全部失效；统一实现 DamageReductionChain（原版护甲 + 通用/法术防御指数 + 全减免 + 格挡），YizxianMob.hurt 与 QuanshouzheEntity.hurt 共用"
metadata:
  type: project
---

# 传导伤害的 5 层减伤链（2026-09-10 落地）

## 问题
`YizxianMob.hurt()`（本模组实体基类）对受保护实体自管扣血：读混淆串血量、减 `conductionCap()`、直接 `SecureHealthClosure.setHealth`，**不调用 `super.hurt()`**，也就不经过 vanilla `LivingEntity.hurt → actuallyHurt → setHealth` 链。

后果：所有挂在 vanilla 链上的减伤对本模组实体全部无效——
- vanilla 护甲公式在 `actuallyHurt.getDamageAfterArmorAbsorb`；
- `YizAttributes.ARMOR` / `SPELL_DEFENSE` 指数减免和传导限伤在 `LivingEntityMixin` 的 `@ModifyVariable(method="hurt", HEAD)`；
- `DAMAGE_REDUCTION` / `DAMAGE_BLOCK` 在 `setHealth` 的 `@ModifyVariable`。

子类 `QuanshouzheEntity.hurt` 此前内联补了其中 4 层（指数/百分比/格挡），仍缺原版护甲减免。

## 实现
新建 `yizmodqzk/tool/health/DamageReductionChain.java`，`apply(target, source, amount)` 按序做 5 层：

1. 原版护甲：`CombatRules.getDamageAfterAbsorb(amount, getArmorValue(), ARMOR_TOUGHNESS)`，`BYPASSES_ARMOR` 伤害类型跳过。
2. 通用防御 `YizAttributes.ARMOR` 指数减免（物理伤害）。
3. 法术防御 `YizAttributes.SPELL_DEFENSE` 指数减免（非物理伤害）。
4. 全伤害减免 `DAMAGE_REDUCTION`（百分比）。
5. 伤害格挡 `DAMAGE_BLOCK`（固定点数）。

指数公式与 `LivingEntityMixin` 同一套：`reduction = 1 - (1 + x/40)^(-log2/log1.5)`，锚定 x=20→50%、x=50→75%。物理/法术路由判据：投射物 / 爆炸 / 摔落 / 近战（`getDirectEntity() == getEntity()` 且是 LivingEntity）→ ARMOR，其余 → SPELL_DEFENSE。

调用点两处，均在 `conductionCap()` 限伤之前：
- `YizxianMob.hurt`（基类，铁斗士等所有棋子）
- `QuanshouzheEntity.hurt`（替换原先内联的 4 层）

## 坑与流程
- 改前置库 `yizmodqzk` 后必须 `build publishToMavenLocal`（发布到 `~/.m2/repository`，下游 `yizxianmod` 的 `repositories { mavenLocal() }` + `fg.deobf("net.minecraft.client.yiz:yizmodqzk:1.0")` 消费），再编译下游；只 build 不 publish，下游拿到的还是旧 jar。
- 5 层里第 1 层与第 2 层用的是同一个数值：`tizMod.mirrorArmor` 把 `YizAttributes.ARMOR` 镜像成 vanilla `ARMOR` + `ARMOR_TOUGHNESS` 的 ADDITION modifier，所以两套公式都会被同一份防御值驱动（这是预期，用户要求 5 种属性都参与）。
- `SPELL_DEFENSE` 的镜像只写到 `KNOCKBACK_RESISTANCE`（击退），不参与伤害；它的伤害减免只靠第 3 层指数公式。
- 顺序固定为 护甲 → 指数 → 百分比 → 固定格挡 → 限伤，改顺序会改变最终数值。
