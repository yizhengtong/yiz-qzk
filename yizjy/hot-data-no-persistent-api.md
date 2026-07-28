---
name: hot-data-no-persistent-api
description: "每 tick 写的热数据别走 PlayerDataAPI——它会每 tick 全量解析 NBT + 全量 S2C 同步整个 root，改纯内存 + 事件驱动下发"
metadata:
  node_type: memory
  type: feedback
---

**判据：** 一条玩家数据若同时满足「退出即弃、无需存盘、客户端只需最终结果」——它就该是**纯内存 Map + 事件驱动 S2C 下发**，绝不能塞进 `PlayerDataAPI`。

**Why:** `PlayerDataAPI` 底层是单一 `AttachmentType<String>`，`get` 每次全量 `TagParser.parseTag(整个 root)`，`set` 每次全量序列化 + **无条件把整个 root 字符串 S2C 发给客户端**（无节流、无 dirty、无差量，见 `NetworkHandler:88`）。把「每 tick 写一次」的热数据（如连招计数）放进去 = 每 tick 烧 CPU 做全量 parse + 每 tick 一个全量同步包，玩家数线性放大。即便只是「攻击时写」，每次攻击也拖整个 root 序列化+网络。

**How to apply:**
- 先问三个问题：① 这数据玩家退出后还要不要？（不要→纯内存）② 要不要存盘/跨会话？（不要→纯内存）③ 客户端是要中间态还是只要最终结果？（只要结果→服务端算好按事件下发）。
- 三条都满足 → 服务端用 `ConcurrentHashMap<UUID, State>` 存，超时/窗口判断改**惰性**（访问时算 `now - lastAtkTick`，不要每 tick 写计数器）。
- 客户端要的结果（如动画索引 animIdx），服务端在事件回调里算出后用专用轻量 S2C payload（如 `S2CComboAnimPayload(int animIdx)`）发给攻击者本人，客户端缓存供每帧渲染读。
- 登录/退出/切维度时 `clear(uuid)` 防内存泄漏。
- 反例案例：`ComboStateMachine` 原把 `combo_step/combo_tick` 存 PlayerDataAPI 每 tick set，重构为纯内存 + `S2CComboAnimPayload`（2026-07-28）。存储机制全貌见工作区 note「NBT 数据存储审查」。
