---
name: forge-classload-enumeration
description: "Forge 1.20.1 已加载类枚举 + agent instrumentation 的坑：单一 TransformingClassLoader（无每 mod 独立 loader）、JDK 17 已移除 ClassLoader.classes、agent 的 resolveBridge 借游戏 loader 强制加载 AgentBridge 才存得住 Instrumentation。"
metadata:
  type: reference
---

# Forge 1.20.1 已加载类枚举 + agent instrumentation（2026-08-14）

> 排查「/yiz key scan 已扫描类 0」的根因链，以及 agent 动态加载的关键坑。

## Forge 1.20.1 类加载架构（纠正旧认知）

- **所有 mod 类在单一 `TransformingClassLoader`**（`cpw.mods.modlauncher.TransformingClassLoader extends cpw.mods.cl.ModuleClassLoader`，SecureJarHandler 库）。GAME layer 就这一个主类加载器，**没有「每 mod 独立 SecureJarClassLoader 子加载器」**。
- 所以「ModList → 各 mod jar 的 getClassLoader」这条枚举路是错的——`ModFileInfo` 根本没有 `getClassLoader()`（只有 `getFile()`），反射必抛 NoSuchMethodException 静默失败。

## JDK 17 已移除 ClassLoader.classes

- `ClassLoader.classes`（`Vector<Class<?>>`）字段 **JDK 9+ 已移除**，`getDeclaredField("classes")` 抛 NoSuchFieldException → Unsafe 定位 offset=-1 → 读恒 0。
- 结论：**无 agent 时，Unsafe 直读 ClassLoader.classes 枚举已加载类在 JDK 17 下彻底不可用**，枚举只能靠 `Instrumentation.getAllLoadedClasses()`。

## agent instrumentation 存不住的根因 + 修复

- 根因：`HealthAgent.agentmain` 里 `storeInstrumentation` 在 retransform **前**调用，那时 `AgentBridge` 类还没被加载；`resolveBridge` 两条路（`Class.forName` 用 attach 线程的 context loader = 系统 loader、`getAllLoadedClasses` 枚举未加载类）都失败 → `setInstrumentation` 从未执行 → `instrumentation` 字段永久 null。
- 假象：`/yiz agent` 显示「已执行」读的是 `agentActive`（被 `recordTransform` 兜底置 true），不是 `instrumentation` 字段本身。
- 修复：`resolveBridge` 加第三条路——**借任一已加载的本模组类（如 tizMod）的游戏 loader 强制 `Class.forName(BRIDGE_CLASS, true, game)` 加载 AgentBridge**，让 `storeInstrumentation` 在 retransform 前就能成功。
- 附带：retransform 可能被第三方 ILaunchPluginService 的异常中断（如某 mod 的 `GenericTransformer.isSubclass` 对 `classLoader==null` 不判空抛 NPE），所以 instrumentation 的存存不能依赖 retransform 是否跑完。

## agent self-attach 失败：多模组提前触发 HotSpotVirtualMachine

- 现象：加载含复杂 coremod 的第三方模组时，agent self-attach 失败，`/yiz agent` 显示「attach/loadAgent: 失败」→ 降级模式（字节码注入/delta 软压失效）。
- 根因：`HotSpotVirtualMachine.<clinit>` 在 savedProps 修改**之前**就被第三方模组触发，`ALLOW_ATTACH_SELF` 定型为 false → `VirtualMachine.attach(pid)` 返回 null → 后续 `loadAgent/detach` 的 `invoke(null,...)` 抛 NPE。
- 修复（已做）：attach 后判空，返回 null 时显式抛「self-attach 被拒」明确异常。
- 根治方向：改 savedProps 太晚，需走 loadAgent0 备选（见 skill agent-self-load）或更早触发 savedProps 修改。
- 附带：降级模式下 `DeathMarkerAccessor`（强制判死标记）仍可用——它不依赖我们的 agent，依赖目标模组自己的 coremod 注入。

## 相关

- [[health-map-tamper]] 藏血 Map 篡改（同一轮工作的另一主题）
- [[quanshouzhe-mhzy-defense-1-20-1]] 1.20.1 血量主文档
