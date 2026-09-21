---
name: agent-self-load
description: "Java Agent 动态加载整套技术流程（Forge 1.20.1）：savedProps 改 ALLOW_ATTACH_SELF + attach + loadAgent 动态加载 agent；含方案对比（为何其他方案失败）、/yiz agent 完整诊断、部署坑（reobf/refMap/clean build）、agent 注入 SRG 适配。yiz 模组 agent 动态加载/修复/部署时使用。"
---

# Java Agent 动态加载整套技术流程（yiz 1.20.1 Forge）

## 背景

- agent 用于对 LivingEntity 子类的 `getHealth/isAlive/isDeadOrDying/getMaxHealth` 做**字节码 FRETURN 包装**（`special*` 裁决器：secure 实体读表值 / delta 截断）+ **调用点包装**（所有类里对血量的调用点 DUP/SWAP+裁决）+ **强制双 tick**（EntityTickList/ServerLevel 注入）。
- 生产环境（PCL，JDK 17）动态加载 agent 受 **self-attach 限制**（`ALLOW_ATTACH_SELF`），需正确绕过。
- **用户红线**：不能加 JVM 启动参数（`-javaagent` / `-D`），模组必须自包含动态加载。
- agent jar 打包：jarjar 内嵌于主 jar（`META-INF/jarjar/yizmodqzk-agent.jar`），运行期提取到临时文件后加载。

## 方案对比（实测结论，2026-08-12）

| 方案 | 结果 |
|---|---|
| 直接 Unsafe 改 `ALLOW_ATTACH_SELF` + attach | ❌ `Can not attach to current VM`（`ALLOW_ATTACH_SELF` 是 final static，`<clinit>` 首次执行重置回默认值 + JIT 常量折叠） |
| `InstrumentationImpl.loadAgent0` + **完整** agent jar | ❌ `InternalError`（JPLISAgent 加载入口类时依赖解析失败） |
| `loadAgent0` + 空 jar + `defineClass0`（学 mhzy） | ⏳ 未生产验证（可作备选） |
| **改 `VM.savedProps` + 触发 `<clinit>` + attach + loadAgent** | ✅ **成功**（`/yiz agent` 显示 isLoaded=true） |

## 成功方案（savedProps + attach）

**原理**：`sun.tools.attach.HotSpotVirtualMachine.ALLOW_ATTACH_SELF` 是 `private static final boolean`，`<clinit>` 从 `jdk.internal.misc.VM.savedProps` 的 `jdk.attach.allowAttachSelf` 初始化。直接 Unsafe 改 final 字段会被 `<clinit>` 重置 + JIT 折叠失效；正确做法是**改 savedProps 后触发 `<clinit>`**，让 `ALLOW_ATTACH_SELF=true`，self-attach 放行。

`HotSpotAttachLoader.prepareAndAttach` 三步：

```java
// 1. 改 VM.savedProps（注意：savedProps 是 HashMap 非 Properties，用 Map.put）
Class<?> vmClass = Class.forName("jdk.internal.misc.VM");
Field savedPropsField = vmClass.getDeclaredField("savedProps");
long offset = unsafe.staticFieldOffset(savedPropsField);
Object base = unsafe.staticFieldBase(savedPropsField);
Object propsObj = unsafe.getObject(base, offset);
((java.util.Map) propsObj).put("jdk.attach.allowAttachSelf", "true");

// 2. 触发 HotSpotVirtualMachine.<clinit>（initialize=true 读 savedProps → ALLOW_ATTACH_SELF=true）
ClassLoader providerLoader = AttachProvider.providers().get(0).getClass().getClassLoader();
Class.forName("sun.tools.attach.HotSpotVirtualMachine", true, providerLoader);

// 3. attach + loadAgent + detach（标准动态加载，agent jar 需 Agent-Class + agentmain）
Object vm = Class.forName("com.sun.tools.attach.VirtualMachine").getMethod("attach", String.class).invoke(null, pid);
vm.getClass().getMethod("loadAgent", String.class).invoke(vm, agentPath);   // agentPath = jarjar 提取的临时 jar
vm.getClass().getMethod("detach").invoke(vm);
```

关键坑：
- `savedProps` 类型是 **HashMap**（非 Properties），`setProperty` 静默不生效——必须 `Map.put`。
- 改 savedProps **必须**在 `HotSpotVirtualMachine` `<clinit>` 执行**前**完成（`Class.forName(..., true, ...)` 触发）。
- agent jar 的 manifest 需 `Agent-Class`（+ `Premain-Class` + `Can-Retransform-Classes: true`）；`loadAgent` 走 agentmain。

## 诊断（/yiz agent）

- `AgentBridge`（主 jar）维护状态：`agentActive`（agentmain 执行）/ `transformerRegistered` / `transformCount`（**实际注入类数**）/ `lastError`。
- agent 的 `LivingHealthTransformer.transform` 每次处理类后反射调 `AgentBridge.recordTransform()`（context classloader）。
- `/yiz agent` 输出：attach 状态 / agentmain 已执行 / transformer 已注册 / 实际 transform 类数 / 最后错误 / 判定。
- 判定规则：
  - `transformCount > 0` = **注入真正生效**（最硬证据）
  - 已注册但 `transform=0` = 实体类已加载未重载（重新召唤实体触发）
  - attach 成功但 `agentmain 未执行` = 静默失败
- **classloader 坑**：agentmain 线程的 context classloader 可能拿不到主 jar `AgentBridge`（agentActive/transformerRegistered 上报失败，但 transform 线程能访问）。解决：`recordTransform()` 兜底置 `agentActive/transformerRegistered=true`（有 transform 必已注册）。

## Agent 注入 SRG 适配

`LivingHealthTransformer.visitMethod` 方法名必须**同时认 official + SRG 名**（生产字节码是 SRG）：

| 方法 | official | SRG |
|---|---|---|
| getHealth | getHealth | `m_21223_` |
| getMaxHealth | getMaxHealth | `m_21233_` |
| isAlive | isAlive | `m_6084_` |
| isDeadOrDying | isDeadOrDying | `m_21224_` |

- 只认 official 名 → 生产 SRG 注入完全不生效（不崩但功能失效）。
- agent jar 用 ASM 字符串 + mod 类调用，**无需 reobf**（方法名匹配是字符串，mod 类名 reobf 不变）。

## 部署坑（务必遵守）

1. **前置库必须显式 `reobfJar`**：`build` 产物是 mapped（official）版，生产 SRG 运行 `NoSuchFieldError: FLOAT`。流程：`build`（mapped，publishToMavenLocal 供下游 dev）→ `reobfJar`（build/libs 变 SRG）→ 复制。
2. **改 mixin 注解后下游必须 `clean build`**：增量 `build` 丢 `yizxianmod.refmap.json` → 生产 `InvalidInjectionException ... No refMap loaded`。
3. 部署前验证：`unzip -l yizxianmod-1.0.jar | grep refmap` 有输出；`javap HealthChannels` 见 `f_135029_`（SRG）。

## 相关

- 记忆库 [[deploy-mod-delete-then-copy]]（agent 方案 + reobf/refMap 坑）
- 记忆库 [[quanshouzhe-mhzy-defense-1-20-1]]（血量技术主文档）
- 参考：mhzy `AgentSelfLoad.java`（loadAgent0 备选方案）、`HelperLib.java`（直接 Unsafe 方案，生产失败）
