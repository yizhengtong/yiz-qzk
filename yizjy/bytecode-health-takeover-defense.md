---
name: bytecode-health-takeover-defense
description: "字节码级血量接管防御：外部模组用 Coremod/Instrumentation 改写 LivingEntity 子类 getHealth/isAlive/isDeadOrDying 字节码覆盖 Java override 的根因与通用对抗方案"
metadata:
  type: project
---

# 字节码级血量接管防御（2026-08-09）

## 根因：外部系统用字节码改写 LivingEntity 子类，Java override 无法抵抗

**原理**：某些外部系统用 `Instrumentation` 注册 `ClassFileTransformer`（Coremod），在类加载早期改写**所有 LivingEntity 子类**（含自研实体）的 `getHealth`/`isAlive`/`isDeadOrDying` 字节码——在每个 `FRETURN`/`IRETURN` 前注入静态方法调用，把实体血量/存活判定导向外部系统。**字节码优先于 Java override**，所以实体自己 override 这些方法会被覆盖。

**注入方法通常读外部状态**：
- 一个 `isDead` 强制死亡标记字段（外部 mixin 注入到实体，设 true → 判定死亡）
- 一个 Float 血量 delta 通道（SynchedEntityData DataParameter，设负无穷 → 血量负无穷）
- **关键 fallback**：当 `isDead=false` 且 `|delta|≤1e-6` 时，注入方法 `return src`（原始返回值 = 实体 override 结果）。

**"打烂"的本质**：外部系统一次攻击同时设置 `isDead=true` + `delta=NEGATIVE_INFINITY`，注入方法据此判定死亡——绕过实体的一切 Java override（hurt/setHealth/传导限伤）。**调数值无效**（对方不走 hurt()）。

## 通用对抗方案（代码级，不针对任何外部系统）

**核心**：让外部注入 fallback 到实体 override（读 SecureHealthClosure 表）——清除外部死亡标记，使注入走 `return src`。

1. **override `baseTick`**：`super.baseTick()` 前强制安全血量状态（本 tick 判定基于干净状态，防 tickDeath 触发）
2. **每 tick（aiStep）再强制一次**（双保险）
3. **强制安全血量状态**（纯通用，"实体永不真正受伤"外部表方案）：
   - 用反射 `EntityActuallyHurt.catchSetTrueHealth` 把 vanilla `health` 字段 + `DATA_HEALTH_ID` 校正回 SecureHealthClosure 表值（绕过一切 setHealth override）
   - 用 `HealthChannelScanner.getAllFloatChannels` 遍历所有 Float 血量 DataParameter（含外部注册的自定义通道），凡偏离表值 → 校正回表值
4. **override `kill()`**：逻辑血量>0 时拒绝（外部 kill 无效）
5. **override `heal(float)`**：负值重定向 hurt 走传导限伤
6. **override `actuallyHurt`**：外部绕过 hurt 直调 actuallyHurt → 重定向 hurt（外部直写底层扣血也走传导链）

**零编译依赖**：全部用原版 API + 前置库通用工具（SecureHealthClosure/HealthChannelScanner/EntityActuallyHurt），不 import 任何外部系统类，未装时天然跳过。

## 关键教训
- **外部"打烂"不一定是数值问题**——先查是否用了字节码改写 LivingEntity。
- Java override 无法抵抗字节码改写；对抗是"清除对方状态使注入 fallback"或"自己也在字节码层注入且靠后"。
- 调 CONDUCTION_CAP/INVINCIBILITY_MULT 数值无效（对方绕过 hurt）。

## 关联
- [[conduction-health-protection]]（SecureHealthClosure 外部表 + 传导限伤）
- [[warden-animation-reuse]]（辖界者）
- [[entity-remove-protection]]（移除保护 isYizCaller 栈帧鉴权）
