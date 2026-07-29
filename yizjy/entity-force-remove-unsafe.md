---
name: entity-force-remove-unsafe
description: "当 Entity.remove/discard/ChunkSource.removeEntity 全部被 override 时，用 Unsafe + EntityLookup 内部 Map 反射强制移除"
metadata:
  type: feedback
---

## 问题

某些模组/反作弊会 override `Entity.remove()`、`discard()`、`ChunkSource.removeEntity()` 等多个层级，导致实体无法通过任何 API 调用移除。

## 绕过方案（优先级递减）

1. **Mixin 注入 ServerLevel** — 直接操作 EntityLookup 内部 Map（反射遍历 getEntities() 返回的 LevelEntityGetter 内部所有字段，找到 Map 后按 UUID remove），配合 Unsafe 写 `Entity.removed=true`、反射调 `EntityTickList.remove()`。完全不经过任何可被 override 的方法。
2. **MethodHandle unreflectSpecial** — 用 `MethodHandles.Lookup.IMPL_LOOKUP` 锁定 Entity 基类 remove()，跳过虚方法分发表。
3. **Unsafe + ChunkSource** — 设 removed=true + sl.getChunkSource().removeEntity(entity)。

**Why:** 连 ChunkSource.removeEntity 都被重写的情况下，只能深入 EntityLookup 内部数据结构（Map<UUID, Entity>）直接按 key 删除。这是能绕过一切 override 的最底层路径。

**How to apply:** 优先走路径 1（Mixin 反射 EntityLookup 内部 Map），失败再尝试路径 2（MethodHandle），最后路径 3（Unsafe + ChunkSource）。代码位置：`mixin/EntityForceRemoveMixin`、`tool/EntityRemovalUtil`、`tool/YizRemoveCommand`。
