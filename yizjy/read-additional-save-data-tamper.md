---
name: read-additional-save-data-tamper
description: "辖界者血量防御漏洞：外部 mod 借道 public readAdditionalSaveData 塞恶意 NBT 篡改权威表血量（绕过 setHealth 扣血丢弃 + 传导限伤）。修复 = isVanillaEntityLoadCaller 调用栈鉴权，且必须放在 super.readAdditionalSaveData 之前。"
metadata:
  type: project
---

# readAdditionalSaveData 借道篡改血量漏洞（2026-08-23 发现+修复）

## 漏洞

外部 Boss（jerotesvillage 的 `VariantZsieinEntity.doHurtAll`）直接调辖界者的 `readAdditionalSaveData`（被 override 成 public），塞恶意 NBT，绕过 setHealth 扣血丢弃黑洞和传导限伤，直接改权威表血量——实测 `400 → 102` 一次扣 191 点，远超单次 15% cap=60。

两层绕过：
1. 辖界者 override 恢复 `yizxianmod_boss_health` 字段 → `SecureHealthClosure.setHealth` 直接写权威表。
2. vanilla `readAdditionalSaveData` 读 NBT 的 `Health` 字段 → 调 `setHealth`（恢复血量）。

根因：vanilla 的 `readAdditionalSaveData` 是 `protected abstract`，辖界者 override 时扩大成 `public`，任何代码都能调。

## 修复

1. 加 `isVanillaEntityLoadCaller()`：StackWalker 检查调用栈是否有 vanilla `Entity.load`（official `load` / SRG `m_20258_`），只在真读存档时放行。
2. **鉴权必须放在 `super.readAdditionalSaveData()` 之前**——放 super 之后 = 白修（vanilla 读 Health 调 setHealth 的扣血在鉴权前就发生）。

## 关键坑

- **鉴权放 super 之后是无效的**：vanilla 的 readAdditionalSaveData 会在 super 链里读 `Health` 调 setHealth 扣血，鉴权后置拦不住。这是本次踩的关键坑。
- 症状识别：日志 `大幅扣血 293 -> 102`（一次扣 100+ 点，远超传导 cap），调用栈含 `VariantZsieinEntity.doHurtAll → readAdditionalSaveData`。
- 辖界者的 `setHealth` override 会丢弃扣血方向，但 vanilla readAdditionalSaveData 读 Health 走的路径绕过了这个丢弃。

## 相关

- [[quanshouzhe-mhzy-defense-1-20-1]] 辖界者血量防御主文档
- [[entity-attribute-gate]] 属性维护门禁
- [[conduction-health-protection]] 传导限伤 + 外部表终态
