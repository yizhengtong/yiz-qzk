---
name: no-magic-number-hacks
description: "实现\"无限制/全场/所有实体\"等功能用正确 API，别用魔法数字 hack 假装"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: d1a63761-c9e1-4afa-97f3-05af1129959f
---

用户批评：卢登激荡"无限制传播范围"需求，我一开始用 `AABB.ofSize(center, 20000.0, 20000.0, 20000.0)` 塞个魔法数字假装全场搜索，还留了 dead code（`SPILL_RADIUS` 常量变 unused）。正确做法是 `level.getAllEntities()` 遍历全部加载实体、按距离取最近 N 个。

**Why:** 魔法数字 hack 既不是真正无限制（超距实体照漏）、语义混乱，又制造死代码与过时注释。用户代婳要的是干净、语义正确的实现，能直接看懂。

**How to apply:** 遇到"全场/无限制/所有实体/无距离上限"这类需求，先 grep codebase 找现成的全实体遍历用法（本项目 `YizSetHealthCommand` 用 `level.getAllEntities()`），用正确 API；不要塞超大 AABB 或凭空编个数字。改完顺手清理因此产生的 dead code（unused 常量/import）和与实现脱节的注释。写代码要经得起"谁教你这么写代码的"的审视。
