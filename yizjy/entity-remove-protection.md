---
name: entity-remove-protection
description: "本模组实体（YizxianMob）移除/新增总闸门：拦 Entity.setRemoved + ServerLevel.addFreshEntity/addDuringTeleport，白名单=保存/死亡/本模组包；关键坑：1.21.1 无 ServerLevel.removeEntity、维度传送直接 setRemoved 不走 remove"
metadata:
  type: project
---

# 实体移除保护（YizxianMob 基类，2026-08-05 落地）

本模组实体（辖界者）的移除总闸门——其他任何模组都无法移除，只白名单放行。

## 拦截点（3 个，下游 yizxian1.21.1）

| Mixin | 拦截方法 | 说明 |
|---|---|---|
| `EntityRemoveProtectionMixin`（@Mixin Entity） | `Entity.setRemoved(RemovalReason)` | **所有移除的最终汇聚点**：`remove()`（discard→DISCARDED、kill/die→KILLED）内部调它，维度传送也直接调它 |
| `ServerLevelAddProtectionMixin`（@Mixin ServerLevel） | `addFreshEntity(Entity)` | 一般新增（召唤/存档加载），返回 **boolean**（拦截时 `CallbackInfoReturnable.setReturnValue(false)`） |
| 同上 | `addDuringTeleport(Entity)` | **跨维度传送新增专用方法**，对 YizxianMob 一律拦 |

## 白名单放行

- **移除（setRemoved）**：①服务器停止/世界保存（`!server.isRunning()`）②本模组死亡监听（生命≤0 → `EntityRemoveProtection.allowDeathRemove(uuid)`）③本模组包调用者（`isYizCaller` 调用栈含 `net.minecraft.client.yiz`，覆盖 /yiz remove、未来本模组维度传送方法）。其余一律拦。
- **新增（addFreshEntity）**：本模组包生成 / 引擎帧存档加载恢复；`addDuringTeleport` 一律拦（维度传送在移除端已断，新增端兜底）。
- 只对 `instanceof YizxianMob` 生效；玩家/普通实体不受影响（玩家照样维度转移）。

## 关键坑（每个都踩过）

1. **1.21.1 没有 `ServerLevel.removeEntity`**（javap 验证）——实体移除汇聚在 `Entity.setRemoved`，不是 remove。初版拦 `ServerLevel.removeEntity` 直接 `InjectionError` 崩溃（方法不存在）。
2. **维度传送不走 `Entity.remove`**：`changeDimension(DimensionTransition)` / `teleportTo(ServerLevel,...)` 字节码直接 `setRemoved(CHANGED_DIMENSION)` + `addDuringTeleport`。所以拦 `remove` 拦不住维度传送，必须拦 `setRemoved`（`setRemoved` 也是 remove 内部调用，一处覆盖全部）。
3. **addFreshEntity 返回 boolean**（不是 void）：描述符 `(Lnet/minecraft/world/entity/Entity;)Z`，回调用 `CallbackInfoReturnable<Boolean>`。
4. **mixins.json 是资源，改了必须重新构建进产物**：dev 运行读 `build/resources`，只 `compileJava` 不更新资源 → 保护不生效（维度传送漏网就是这个：mixins.json 没进 build）。
5. **测试期临时关闭死亡放行**：注释 `YizxianMob.aiStep` 的 `allowDeathRemove` 调用；**注意**——死亡放行关闭时打死辖界者会以 0 血残留存档，**污染存档导致后续加载卡「加载地形中」**（需换新世界测试）。
6. **⚠️ `isYizCaller()` 栈帧跳漏（2026-08-07 严重 bug，寰宇支配之剑能移除辖界者）**：原实现 `for (i=3...)` 遍历调用栈但**只跳过 `Entity` 帧，没跳过 Mixin 注入帧**。而 `Thread.currentThread().getStackTrace()` 的 `i=3` 恰好是 `yizxianmod$protectRemove`（Mixin 注入到 setRemoved 的方法，属于 `net.minecraft.client.yiz.xian.mixin` 包）→ `startsWith("net.minecraft.client.yiz")` 恒命中 → **任何外部 remove 都被误判「本模组调用」放行，移除保护形同虚设**。修复：跳过 `net.minecraft.world.entity.Entity` 帧 + `EntityRemoveProtectionMixin` 帧，从第一个真实外部调用者判定。**教训：调用栈鉴权必须跳过 Mixin 注入帧自身，否则框架帧恒命中白名单**。

## 关联
- 辖界者整体状态：[[warden-animation-reuse]]
- 死亡监听（生命≤0 检测在 YizxianMob aiStep）：[[entity-attribute-gate]]
