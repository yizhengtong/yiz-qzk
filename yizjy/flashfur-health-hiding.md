---
name: flashfur-health-hiding
description: "omnimobs 模组 Flashfur 实体藏生命值方案（反编译 jar/flashfur/）：血量存外部 ProtectedWeakHashMap + AccessChecker（token 门禁 + StackChecker 防反射/invoke 帧）。已落地到辖界者 ProtectedHealthMap。"
metadata:
  type: project
---

# omnimobs-Flashfur 藏生命值方案（2026-08-11 分析）

> 用户提供 `D:\ZM\yizgzq\改血\omnimobs-0.3.5.3`（源码空，反编译在 `D:\ZM\yizgzq\jar\flashfur\`），研究 Flashfur 实体怎么藏血/免改。

## Flashfur 藏血核心（BossEntity + anticheat 包）
1. **血量存外部表**：`HealthManager.healthValues = ProtectedWeakHashMap<BossEntity, Float>`——不在实体 DataParameter/字段上，外部扫描实体找不到血量。
2. **getHealth 走外部表**：`BossEntity.getHealth() → HealthManager._a(this)`（读表）。
3. **setHealth 特权动作**：`AccessChecker.performPrivilegedAction(() -> HealthManager.setHealth(...))`——token 门禁。
4. **AccessChecker 双重鉴权**：
   - `StackChecker.notCalledFromAllowedPackage`：StackWalker 检查，**拒绝 `java.lang.reflect.`/`java.lang.invoke.` 帧**（防反射/IMPL_LOOKUP 直调）+ 允许包白名单（`flashfur.omnimobs.`/`net.minecraft.` 等）。
   - token 门禁：`performPrivilegedAction` 设随机 128 字节 ThreadLocal token，`checkAccess` 验证。
5. **ProtectedWeakHashMap**：继承 WeakHashMap，所有 mutator（put/remove/replace）都调 `AccessChecker.checkAccess()`——外部（无 token + 非允许包 + 反射帧）写不进去。
6. **FlashfurInvulnerabilityDetection**（反作弊反杀）：记录攻击者旧血量，检测到攻击者血量异常变化（被改血）→ 计数 10 次 → 反杀（玩家 forceHurt 100 / 非玩家 forceRemove）。

## 对我们的启示（已落地）
- **防反射直写表**：调用栈检查拒绝 `java.lang.reflect`/`java.lang.invoke` 帧是防 IMPL_LOOKUP 直调方法的关键——我们落地为 `ProtectedHealthMap`（全写方法 `isTrustedTableWrite` 鉴权）。
- **血量存外部表 + getHealth 走表**：外部扫描实体（DataItem/字段/字节码）找不到血量。
- ⚠️ **盲区**：方法级鉴权防不住 IMPL_LOOKUP 直接改 Map 内部结构（CHM table 数组）/static 字段引用（见 [[fantasy-anti-tamper-battle]]）。

## 相关
- [[health-map-tamper]] 通用藏血 Map 篡改（攻取视角，已攻破本方案）
- [[fantasy-anti-tamper-battle]] 生产对抗 fantasy 战果
- [[quanshouzhe-mhzy-defense-1-20-1]] 主文档
