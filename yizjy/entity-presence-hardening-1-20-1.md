---
name: entity-presence-hardening-1-20-1
description: 1.20.1 实体免移除通用加固：命门是「删掉后回不回得来」而非「拦不拦得住删除」；三层防线 + 自愈不得依赖官方加入入口 + 身份被改致按 id 的保护静默失效
metadata:
  type: project
---

# 实体存在性通用加固（2026-08-21 落地，yizxianmod 1.20.1）

## 核心认知（推翻此前的思路）

**免移除的真正命门不是「能不能拦住删除」，而是「删掉之后能不能回来」。**

此前的体系是白名单式方法级门禁（拦 setRemoved / removeEntity），假设「所有移除都会调某个方法」。
但成熟的清除实现根本不调移除方法：直接改字段、直删世界结构。此时唯一依靠是自愈回填 ——
而自愈若走官方加入入口（`ServerLevel.addFreshEntity` → `PersistentEntitySectionManager.addEntity`），
外部只要在那个入口前端返回 false 或取消加入事件，**自愈 100% 失效，实体永远回不来**。

生产日志实锤：每 50ms 重试、连续 123 次、每次报告的缺失项完全相同、`isAddedToWorld` 始终 false
—— 守卫线程活着，死在最后一步回填上。

## 三层防线（缺一不可，互相独立）

1. **入口抢先**：在加入入口最前端完成加入并给出成功返回值，终止回调链，排在后面的否决没有执行机会。
2. **事件反否决**：`LOWEST` + `receiveCanceled = true` 把取消状态改回放行。**服务端和客户端都要**
   —— 客户端把实体放进世界同样走这个可取消事件，被取消时服务端再完好玩家也看不见、打不着。
3. **结构自愈**：按缺失项直接回填世界结构，**完全不经过任何加入入口**。这是唯一不受入口封锁影响的一层，
   也是前两层被绕过时的最后依靠。

## 三个非显而易见的坑

1. **身份被改会让按 id 的整层保护静默失效**：外部把 id 改成随机值后，受保护 id 集合里仍是旧值，
   底层所有按 id 的判定全部失配 —— 不报错、不崩溃、只是保护整体不生效。
   恢复 id 时**必须同步注销旧 id、注册新 id**。
2. **自然清除是一条完全合法的移除路径**：持久化标记是可被改写的普通状态，置 false 后原版自己
   就会在玩家走远时清掉实体。必须从判定源头（`removeWhenFarAway` / `isPersistenceRequired`）
   返回固定值，让标记被改写也不生效。此前基类完全没有这层防护。
3. **调用栈鉴权必须跳过注入帧自身**（旧坑复发风险）：闸门注入方法所在的包会恒命中本模组白名单。
   已抽出统一鉴权，内置跳过逻辑，新增闸门直接复用，不要每个闸门各写一份。
4. **自愈回填造成 section 残留重复 → 退出存档翻倍（2026-08-25 实锤）**：移除保护拦截 `onRemove`
   让实体被删时 section 里残留一份，随后不死守卫回填又 `add` 一份 → section 里同实体两份。
   退出存档时这两份都被写进存档 → 重进加载两份 → 每次重进翻倍（1→2→4→8，同 UUID 多 id）。
   修复两处都在 `WorldPresenceGuard`：① `attachToSection` 的「先摘再挂」单次 `section.remove` 只删一个引用，
   要**循环清空该实体的所有残留再 add 一次**；② `repair` 里 `attachToSection` 只在 section 缺失时执行，
   改成 section 存在也调一次做去重（幂等）。核心教训：**自愈回填必须「补缺失 + 清多余」双向幂等，不能只补不清**。

## 已知边界（不要指望这几件事）

- **渲染被前置取消无法通用对抗**：可见性判定可以抢先接管，但渲染调用一旦被取消，除非自己重做
  整套渲染否则挽不回。加固边界定在「实体真实存在、能交互」，不是「强制像素显示」。
- **注入进原版方法内部再调用**会让栈帧鉴权误放行（栈看起来就是原版）——靠自愈兜底，不靠闸门。
- **同为最高优先级时先后不确定**：抢先闸门依赖注入顺序，不能作为唯一依靠。

## 排查标志

日志 `[QZK-READD] restored ... direct=true/false`：
`direct=true` 结构直接回填成功；`direct=false` 说明结构定位失败退回了官方路径（要查类型形状定位）。
正常情况这行应大幅减少甚至不刷屏 —— 一次重建就修好，除非外部持续重打。

## 快照复活漏洞（2026-08-26 修复）

- **现象**：辖界者被真实击杀 / `/yiz remove` 后门移除后，重新进入游戏又从存档复活。
- **根因**：`YizxianMobPersistence` 快照只有「写」（saveMob 每 tick）和「还原」（restoreMobs/respawnMob 重进），缺「删」。真实击杀（die→tickDeath→remove）和后门移除（/yiz remove→forceRemoveCleanup）都没清快照 → 重进时 respawnMob 发现 `level.getEntity(uuid)==null` 就重新 spawn。
- **修复**：新增 `YizxianMobPersistence.removeMob(level, uuid)`；`YizxianMob.die()` 确认死亡（表值≤0）时删快照；`forceRemoveCleanup()` 删快照。所有 Boss 的 die() 都汇聚到基类 YizxianMob.die()，一处清理全覆盖。
- **关键约束**：停机退出存档走 `remove()` 的 `shuttingDown` 分支，**不走 die()/forceRemoveCleanup**，快照保留（正常退出重进辖界者仍在）——不能无脑在 remove() 里清快照。
- **⚠️ 自愈 reAdd 复活（2026-08-26 二修）**：光清快照不够——`/yiz remove` 后实体在**运行中**就被自愈机制 `reAddIfRemovedFromWorld` 立即拉回（日志 `QZK-READD restored` 与 YizRemoveCmd 同一毫秒）。根因：`isForceRemoving` 是 **ThreadLocal 临时标记**（beginForceRemove/endForceRemove 包住 forceRemove，结束即清），而 reAdd 经 `TickTask` 在标记清除后才检查 → 恒 false。修复：加持久 `FORCE_REMOVED` 标记（forceRemoveCleanup 添加，reAddIfRemovedFromWorld 和 immortalGuard 开头检查，clearImmortalRegistry 清空）。教训：临时 ThreadLocal「进行中」标记挡不住异步自愈，永久移除必须用持久标记。
- **补丁遗漏（2026-08-26 三修）**：FORCE_REMOVED 检查加在了 reAddIfRemovedFromWorld **方法体**（tell 之前），但真正的 reAdd 发生在 tell 排队的 **TickTask lambda 里**——lambda 里没有检查，forceRemove 之前排队的 TickTask 在 forceRemoveCleanup 加标记后仍会 reAdd（日志仍 `QZK-READD restored`）。修复：检查必须也加进 lambda（`if (this.level() != sl) return;` 之后）。教训：异步排队的 lambda 才是真正执行点，防御检查要加在 lambda 里而非排队方法体。
- **死亡检查（2026-08-26 四修）**：reAdd 的 lambda 里除 FORCE_REMOVED 外还缺 **hp 检查**——真实击杀（hp=0）后实体被 remove、结构缺失，之前排队的 TickTask 也会把死亡实体 reAdd 复活。已在方法体和 lambda 都加 `getHealth <= 0` return。教训：自愈 reAdd 必须同时排除「已永久移除（FORCE_REMOVED）」和「已死亡（hp<=0）」两种状态，缺一不可。

## 相关

- [[entity-remove-protection]] 方法级移除闸门（1.21.1 版，本文是其在 1.20.1 的架构升级）
- [[yizxianmob-remove-protection-agent]] 字节码层拦截（含一处已纠正的实现类误判）
- [[anti-tamper-test-not-dev]] 这类改动必须生产环境实测，dev 测不出
- [[deploy-mod-delete-then-copy]] 部署核对（本次两次踩坑都在部署环节）
