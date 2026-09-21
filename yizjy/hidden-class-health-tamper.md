---
name: hidden-class-health-tamper
description: "隐藏类藏血实体（KlassHacker 换头 + AES 加密 + 差值血量）的通用改血：隐藏类不可 instrument（JEP 371），agent 注入 specialGetHealth 失效 → 走「定位失败 → DREAM_ACCUM 累积 → dreamDeathblow 死亡链」，学 Trial 的 soul damage。"
metadata:
  type: project
---

# 隐藏类藏血实体通用改血（2026-08-24 落地）

> 大贤者（village_mod 的 GreatSage）用 KlassHacker 换头成隐藏类 `HiddenGreatSage`，血量 = `maxHealth - AES解密(LOST_STRING)`（差值血量 + AES 加密），此前改不动，经排查打通。

## 根因：隐藏类不可 instrument

Java JEP 371 规定隐藏类（`Lookup.defineHiddenClass`）**不可修改**：`JVM TI IsModifiableClass` 返回 false，`ClassFileTransformer.transform` 对 defineHiddenClass **不生效**、也不支持 retransform。所以 agent 注入 `specialGetHealth`（delta 软压）**根本打不进隐藏类的 getHealth**——KlassHacker 正是利用这点换头藏血。来源见 JEP 371 实现评审。

## 通用改血路径（学 Trial 的 soul damage）

定位失败（AES/隐藏类）时，走「累积软压 + 死亡链」，不依赖 getHealth 字节码：

```
applyProportionalDreamDamage → TotalOverride 定位失败 → DREAM_ACCUM 累积(accum += dream/maxHp)
→ accum 跨 1 → dreamDeathblow → finishDeathblow(setHealth(-∞)+反射die+kill+forceRemoveDeep)
```

Trial 的 soul damage 同理：Mixin 给 Entity 基类加 DataParameter + ISoulDamage 接口，累积持续 + 跨阈值 die/kill 无条件。字段在基类、`instanceof` 对隐藏类成立、死亡靠父类 die/kill（不读 getHealth），所以隐藏类也能改。

## 四个坑（都踩过，别重犯）

1. **clearDreamAccum 不能每次攻击清**：`TotalHealthOverride.apply` 开头清累积，导致大贤者「定位失败 → 回退累积」时 accum 每次从 0 重来，永远到不了 1。修=只在死亡后清（dreamDeepRemove）。
2. **kill 兜底判死不能用 isDeadOrDying（getHealth）**：隐藏类 getHealth 恒返回原值（AES 串没改），isDeadOrDying 恒 false → kill 永不触发。改用 `isEntityDead`（读 dead 字段，反射 die 已置 true）。
3. **TotalOverride 误判主槽 modify 返回 true → 跳过累积**：定位到误判字段（castCooldown 等）时 modify 返回 true，apply 提前 return，累积软压被跳过。修=写后回读「未落地」时返回 false，让调用方回退累积。
4. **死亡链别补 hurt**：finishDeathblow 里「补 hurt(1.0F)」会触发外部模组（village_mod）的 hurt 逻辑，把其召唤物（神圣悦灵）的 `DATA_SHARED_FLAGS_ID`(Byte) 写成 Boolean → 渲染崩溃。Trial 的 onSoulDeath 不补 hurt，所以不崩。

## 相关

- [[quanshouzhe-mhzy-defense-1-20-1]] 辖界者血量防御主文档
- [[read-additional-save-data-tamper]] readAdditionalSaveData 借道篡改
- [[encrypted-string-health-cipher]] 加密串藏血反解
