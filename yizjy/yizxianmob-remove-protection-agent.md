---
name: yizxianmob-remove-protection-agent
description: 1.20.1 辖界者免移除对抗外部模组（终极骷髅灭神模式/omnimobs清除工具/超级史蒂夫）的 agent 字节码拦截方案与踩坑
metadata:
  type: project
---

# 辖界者免移除的 agent 字节码拦截（2026-08-19 进行中）

## 背景与目标

1.20.1 辖界者（YizxianMob 基类）要对抗「外部模组把辖界者强制移除」，尤其「终极骷髅」（`net.mcreator.ultimateskeletons`）的**灭神模式**：`Deathlist.killEntity` 每 tick 持续对辖界者做「字段直写 + 列表清」，绕过 setRemoved/override/mixin，把辖界者从世界移除。

用户要的是「灭神模式期间辖界者也**根本不被移除**」（不是移除后拉回来的闪烁）。

## 灭神模式的移除路径（反编译确认）

`Deathlist.killEntity → completeRemove → serverEntityManagerRemove`：
1. `entity.removalReason = KILLED`（putfield 直写字段，AccessTransformer 公开）
2. `entity.isAddedToWorld = false`（putfield 直写）
3. `EntityTickList.active.remove(id)` + `ChunkMap.entityMap.remove(id)`（**直接操作 Int2ObjectMap，不调 EntityTickList.remove/ChunkMap.removeEntity 方法**）

这三步都绕过方法调用，只能用 **JavaAgent ASM 字节码改写**拦截。

## 已落地的 agent 拦截（LivingHealthTransformer，前置库 agent/src）

三层拦截：
1. **FieldWriteAdapter**：拦所有类里 `putfield Entity.removalReason`（引用 value）/`isAddedToWorld`（boolean value）——活辖界者（表值>0）的「移除语义」直写跳过。
2. **Int2ObjectMapRemoveAdapter**：拦 `Int2ObjectOpenHashMap.remove(int)`——活辖界者 id 返回 null（不删除）。
   ⚠️ **实际覆盖面远小于当初设想，见下方纠正**。
3. 配合 `EntityASMUtil.registerProtectedId/unregisterProtectedId`（辖界者 id 集合，registerImmortal 加入 / unregisterImmortal 移除）。

## ⚠️ 两个已踩的坑（关键，别重犯）

1. **VerifyError（Bad type on operand stack）**：FieldWriteAdapter 最初用同一个 `shouldProtectRemoval(Object, Object)` 处理两个字段，但 `isAddedToWorld` 是 boolean，putfield 的 value 在栈上是 int，int 不能赋 Object 参数。**必须拆两个重载**：`shouldProtectRemoval(Object, Object)`（removalReason 引用）+ `shouldProtectRemoval(Object, boolean)`（isAddedToWorld），FieldWriteAdapter 按字段名选 descriptor `(Ljava/lang/Object;Ljava/lang/Object;)Z` vs `(Ljava/lang/Object;Z)Z`。
2. **NoClassDefFoundError（EntityASMUtil）**：Int2ObjectMapRemoveAdapter 最初拦 `it/unimi/dsi/fastutil/ints/` 整个包，把 `Int2ObjectLinkedOpenHashMap` 也拦了，而其 classloader 访问不到主模组 EntityASMUtil → 崩溃。因此收窄为只拦 `Int2ObjectOpenHashMap`。

## ⚠️ 纠正：这层拦截的覆盖面远小于原设想（2026-08-21 实证）

原记录称「服务端 EntityTickList/ChunkMap 用的就是 `Int2ObjectOpenHashMap`」——**错误**。
查 1.20.1 官方映射源码实测：

| 结构 | 实际实现 | 这层拦得住? |
|---|---|---|
| `EntityTickList.active/passive` | `Int2ObjectLinkedOpenHashMap` | ❌ |
| `EntityLookup.byId` | `Int2ObjectLinkedOpenHashMap` | ❌ |
| `PersistentEntitySectionManager.knownUuids` | `HashSet` | ❌ |
| `ChunkMap.entityMap` | `Int2ObjectOpenHashMap` | ✅ 仅此一处 |

即：**更新列表与实体索引的直删，这层从来没拦住过**，只有区块跟踪表拦得住。
改拦 Linked 版会重蹈上面第 2 坑。正确做法是拦**方法**（`EntityTickList.remove` / `EntityLookup.remove`
/ `EntitySection.remove`）而不是拦通用容器类：可鉴权、不碰 fastutil、不触发 classloader 问题。
已按此思路在 mixin 层落地，agent 这层未改动，见 [[entity-presence-hardening-1-20-1]]。

## 关键文件

- 前置库 agent：`D:\ZM\yizgzq\1.20.1\yizmodqzk\agent\src\net\minecraft\client\yiz\agent\LivingHealthTransformer.java`（FieldWriteAdapter + Int2ObjectMapRemoveAdapter + 现有血量包装）
- 前置库：`...\tool\health\EntityASMUtil.java`（shouldProtectRemoval 两重载 + shouldProtectRemove + registerProtectedId/unregisterProtectedId）
- 资源模组：`...\entity\base\YizxianMob.java`（SafeLevelCallback + reAddIfRemovedFromWorld + guardPosition 字段级位置保护 + clearKnownUuid + isTickListMissing + registerImmortal 维护 id 集合）
- 发布 jar：`D:\ZM\yizgzq\1.20.1\jar\{yizmodqzk,yizxianmod}-1.0.jar`（reobf SRG，构建命令 `./gradlew build reobfJar` 前置库 + `./gradlew build` 资源模组）

## 对抗参考（外部模组移除手段，见 [[anti-tamper-test-not-dev]]）

- omnimobs `EntityRemovalUtil.deleteEntity`：列表清（删 EntitySection/EntityLookup/ChunkMap）
- 终极骷髅 `Deathlist.killEntity`：字段直写 + 列表清（删 EntityTickList/ChunkMap，不删 byId/knownUuids）
- 超级史蒂夫 `SSUtil.safeEntity`：copy-on-write 反向操作列表清 + 字段级恢复（免移除参考）
- Metapotent Flashfur `forceSetPos`：直接写 position 字段传送到 1E9 格

## 下一步

1. 部署最新 jar 到生产环境测灭神模式，确认辖界者稳定存活、无 VerifyError/NoClassDefFoundError。
2. 若还有残留移除路径，看 `logs/latest.log` 的 `[QZK-READD]`（已移除诊断日志，需要时再加）排查。
3. 参考 [[cross-version-port-plan]] 的构建/部署守则。

## 相关

- [[anti-tamper-test-not-dev]] 对抗测试不进 dev 环境
- [[entity-remove-protection]] 辖界者移除保护总闸门
- [[cross-version-port-plan]] 1.20.1 移植计划主文档
