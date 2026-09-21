---
name: entity-death-removal-drops
description: "1.20.1 辖界者死亡掉落与免移除兼容：已弃用自定义 spawnCustomDeathDrops，改为 vanilla 完整死亡链(die→super.die→dropAllDeathLoot LootTable钻石+dropExperience经验球→tickDeath动画20tick移除)；免移除保护对表值>0 绝对生效，死亡经 allowDeathRemove 放行；allowDeathRemove 标记残留漏洞(复活后)已用 mixin 双重验证+revoke 修复。"
metadata:
  type: project
---

# 实体死亡掉落与免移除兼容（2026-08-12 更新，1.20.1）

> 辖界者"死亡掉落不稳定"根因 + 改为 vanilla 正常死亡流程 + 免移除保护无漏洞。方案终态。

## 根因（2026-08-12 实测：钻石/经验双重缺失）
1. `aiStep()` 表值≤0 → `YizieManager.checkAndRemove` **立即 remove(KILLED)**，同一 tick 内 `tick()` 分支 `isRegistered` 已被置 false → `spawnCustomDeathDrops()` 永不执行。
2. `QuanshouzheEntity.die()` **不调 super.die()** → vanilla `dropExperience()`（经验球）从未执行。
3. 辖界者无 LootTable（`getDefaultLootTable` 默认空表）→ `dropAllDeathLoot` 掉空。
4. 结论：既无自定义掉落也无 vanilla 掉落。

## 终态方案（vanilla 完整死亡链，已弃用自定义掉落）
- **掉落**：`die()` 表值≤0 → 清理 bossEvent/SkillManager → `super.die(source)`（内部 dead=true + `dropAllDeathLoot`→`dropFromLootTable`+`dropExperience` + 死亡广播 + setPose(DYING)）。
  - **LootTable 绑定**（1.20.1）：override `getDefaultLootTable()` 返回 key（`Mob.lootTable` 字段构造时初始化），json 放 `data/yizxianmod/loot_tables/entities/quanshouzhe.json`（掉 1~3 钻石）。
  - **经验**：override `getExperienceReward()=50`；**必须 override `isAlwaysExperienceDropper()=true`**——辖界者自定义 `hurt()` 不设 `lastHurtByPlayerTime`，否则 vanilla `dropExperience` 的 `lastHurtByPlayerTime>0` 条件不满足 → 不掉经验。
  - ⚠️1.20.1 `dropExperience()` 是**无参**（在 `dropAllDeathLoot` 内部调用，非 die 直接调）；`dropAllDeathLoot` 签名 `(DamageSource)` 单参。
- **移除**：去掉 `aiStep` 的 `checkAndRemove` 立即移除（跳过倒地动画+抢掉落）→ 走 vanilla `tickDeath` 死亡动画 20 tick 后自然 `remove(KILLED)`；`tick()` hp≤0 分支改为 **deathTime>=20 兜底强制移除**（防 tickDeath 意外未移除残留）。

## 免移除与死亡移除兼容（关键）
- **表值>0 绝对不可移除**：`remove` override（非 FORCE_REMOVE 且表值>0 拦截）+ `die/kill` override + `setPose(DYING)` 拦截 + `EntityRemoveProtectionMixin`（拦 `setRemoved`，白名单=服务器停止/死亡放行/本模组调用栈）。
- **死亡（表值≤0）放行**：`aiStep`/`tickDeath` 调 `allowDeathRemove(uuid)` → mixin `consumeDeathAllow` 放行移除。

## ⚠️ allowDeathRemove 标记残留漏洞（2026-08-12 修复，防复活被外力移除）
- **成因**：改成 vanilla 死亡动画后，`aiStep` 的 `allowDeathRemove` 标记从"1 tick 即消费"变为**存续整个死亡动画窗口（20 tick）**；若表值≤0 后、动画完成前被拉回 >0（复活），`tick()` 会重置 dead/deathTime 但**标记残留** → 之后外部任意 `setRemoved` 被 consumeDeathAllow 放行 → **活实体被外力移除**。
- **三层修复**：
  1. `EntityRemoveProtectionMixin.shouldAllowRemove`：consumeDeathAllow 后**双重验证表值≤0** 才放行（复活实体不放行，主防线不变）。
  2. `YizxianMob.tick()` hp>0 分支：`revokeDeathAllow(uuid)` 主动清残留标记。
  3. `EntityRemoveProtection` 新增 `revokeDeathAllow(UUID)`。

## 相关
- [[quanshouzhe-mhzy-defense-1-20-1]] 主文档
- [[fantasy-anti-tamper-battle]] 生产对抗（不死守卫背景）
