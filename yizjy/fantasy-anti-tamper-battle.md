---
name: fantasy-anti-tamper-battle
description: "2026-08-11 生产环境对抗 fantasy_ending 改血完整战果：藏名/权威表/鉴权容器/客户端钳制/不死守卫/自绘头顶血条。结论：fantasy 用 IMPL_LOOKUP 反射最终仍能改服务端表（改 CHM 内部绕过方法鉴权），未完全防御，用户放弃该方向转数值调整。"
metadata:
  type: project
---

# 生产对抗 fantasy_ending 改血（2026-08-11）

> 在 PCL 生产环境（`1.20.1-Forge_47.4.22`，mods 含 fantasy_ending 2.7.20）实测辖界者 vs fantasy 武器/改血。

## 现象（fantasy 的攻击路径，逐步定位）
1. **直写混淆串**：fantasy 的 `isHealthField` 谓词只要求"字段名含 HEALTH"（不要求 Float），定位到 String 的 `SECURE_HEALTH` 通道，VarHandle 直写 DataItem（绕过 setHealth/hurt/cap）→ 一刀 -40。
2. **压 getHealth() 虚拟调用**：fantasy agent 对辖界者 getHealth 的 FRETURN 包装 `min(值, maxHealth+delta)`，delta 深压 → 血条/死亡判定被压。
3. **触发死亡**：客户端 isDeadOrDying 被压 → vanilla 客户端 `setPose(DYING)`（我们 override 漏了 `!clientSide`）→ 倒地。
4. **反射改权威表**：ProtectedHealthMap 只拦 put/remove 时，fantasy 用 `putIfAbsent/compute/merge` 绕过 → 服务端表被改 → 砍死。

## 落地的防御（全部通用、不点名）
1. **藏名**：`SECURE_HEALTH→SECURE_OBF`、`SECURE_HEALTH_KEY→SECURE_OBF_KEY`（不含 HEALTH/HP/LIFE）——fantasy `isHealthField` 匹配不到。
2. **服务端权威表**：`AUTHORITY_TABLE`（服务端唯一逻辑血量）+ 混淆串镜像；`getHealth` 服务端读表、客户端读串镜像；enforce 每 tick 表→串覆盖。
3. **鉴权容器**：`ProtectedHealthMap`（继承 CHM，override **全部写方法** put/putIfAbsent/putAll/compute/computeIfAbsent/computeIfPresent/merge/replace/replaceAll/remove/clear，调用栈+OBF_WRITE_GATE 鉴权，学 Flashfur）。
4. **客户端钳制**：`getHealth` 客户端分支——fantasy 直写串 0/负 → 返回上次/满血（防假死）；⚠️去掉"跳变>5%忽略"（会把真实表值变化也忽略 → 头顶血条卡住）。
5. **不死守卫**：`YizxianMob.tick()` 客户端+服务端每 tick 强制 `dead=false/deathTime=0/pose非DYING`（用静态 SecureHealthClosure，fantasy 压不到）；`tickDeath()` 表值>0 空过；`setPose(DYING)` **去掉 !clientSide**（客户端也拦，否则 fantasy 压判死 → 客户端倒地）。
6. **自绘头顶血条**（绕开原版 ServerBossEvent / Jade 等读虚拟 getHealth 被压的问题）：`QuanshouzheRenderer` 绘制，2.png 空血条边框 + 1.png 填充按比例**从右至左**裁剪叠加，数值用 `SecureHealthClosure.getHealth`（权威表镜像）。图片在 `assets/yizxianmod/textures/gui/healthbar_fill.png` / `healthbar_empty.png`。

## ⚠️ 结论（用户决定放弃该方向）
- fantasy 用 **IMPL_LOOKUP 改 CHM 内部 table 数组/static 字段引用**（绕过一切方法鉴权）最终仍能改服务端权威表——**方法级鉴权防不住 IMPL_LOOKUP 直接改内部结构**（这是 super-steve/Flashfur 也防不住的层面）。
- 2026-08-11 用户判断"没法打败这个模组"，**放弃针对 fantasy 的改血对抗**，转向**数值调整**（辖界者属性/强度）。
- 本文件记录完整战果供未来参考（若再遇同类模组对抗，可复用：藏名 + 权威表 + 鉴权容器 + 客户端钳制 + 不死守卫 + 自绘血条这套组合，但要知道 IMPL_LOOKUP 改内部结构是终极盲区）。

## 相关
- [[quanshouzhe-mhzy-defense-1-20-1]] 主文档
- [[flashfur-health-hiding]] 藏血方案参考
- [[deploy-mod-delete-then-copy]] 部署工作流
