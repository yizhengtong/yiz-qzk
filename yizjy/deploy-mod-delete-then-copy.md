---
name: deploy-mod-delete-then-copy
description: "模组部署到 PCL mods 的必做工作流：先删除 mods 里的旧 jar 再复制最新，保证百分百最新，不要怀疑部署版本。"
metadata:
  type: feedback
---

每次把模组部署到 PCL 实例 mods（`D:\桌面\.minecraft\versions\1.20.1-Forge_47.4.22\mods`）前，
**先 `rm` 删除 mods 里旧的 yizmodqzk-1.0.jar / yizxianmod-1.0.jar，再从 build/libs 复制最新的**。
不要"覆盖复制"或反复怀疑运行的不是最新版——删旧+复制新才是百分百保证。

**Why:** 2026-08-11 生产调试多次出现"怀疑 mods 里是旧 jar / 字节码不一致"的胶着，浪费大量排查时间。
覆盖复制可能残留旧版本（如内嵌类、缓存），删除再复制最干净。

**How to apply:** 每次构建后部署固定三步：① `./gradlew build`（yizmodqzk 先 publishToMavenLocal 供下游依赖）；
② `rm -f mods/yizmodqzk-1.0.jar mods/yizxianmod-1.0.jar`；③ `cp build/libs/*.jar mods/`。备份文件 `.bak-*` 保留不删。

## ⚠️ 生产部署必须用 reobf 后的 SRG jar（2026-08-12 实锤）

**yizmodqzk 的 `finalizedBy 'reobfJar'` 已移除**（build.gradle 271 行）：`./gradlew build` 产物是 **mapped（official）版**，
供下游 dev 环境 fg.deobf 引用。直接部署到生产（PCL，SRG 运行时）会 Bootstrap 期崩溃：
```
NoSuchFieldError: FLOAT  at HealthChannels.<clinit>(HealthChannels.java:26)
```
根因：mapped jar 里 `EntityDataSerializers.FLOAT` 引用未 reobf → 生产 SRG 名 `f_135029_` 找不到 `FLOAT`。
（触发链：LivingEntityMixin 的 @Unique 字段 `yizmodqzk$HEALTH_DELTA = HealthChannels.DELTA_HEALTH` 初始化合并进
LivingEntity.<clinit> → 触发 HealthChannels.<clinit>。此 @Unique 字段引用 holder 已避免直接 vanilla 字段，但 holder 自身的
`EntityDataSerializers.FLOAT` 仍需 reobf。）

**正确生产部署流程（yizmodqzk 必须显式 reobf）**：
① yizmodqzk `./gradlew build` → `publishToMavenLocal`（mapped，供下游编译）；
② yizmodqzk `./gradlew reobfJar`（build/libs 变 SRG 版）；
③ yizxianmod `./gradlew build`（finalizedBy reobfJar 仍保留 → 产物已是 SRG）；
④ 复制 yizmodqzk/build/libs/*.jar + yizxianmod/build/libs/yizxianmod-1.0.jar 到部署目录。
验证：javap HealthChannels 应见 `EntityDataSerializers.f_135029_` 而非 `FLOAT`。

**agent 注入生产必须同时认 official + SRG 方法名**：`LivingHealthTransformer.visitMethod` 匹配
`getHealth`→也要 `m_21223_`、`getMaxHealth`→`m_21233_`、`isAlive`→`m_6084_`、`isDeadOrDying`→`m_21224_`，
否则生产（SRG 方法名）注入完全不生效（不崩但辖界者防御/攻击线失效）。agent jar 用 ASM 字符串+mod 类调用，无需 reobf。

## ⚠️ 改 mixin 后下游必须 clean build（2026-08-12 反复踩坑）

改任何 mixin 注解（priority/method/注入点）后，**yizxianmod 必须 `./gradlew clean build`**——
增量 `build` 时 annotationProcessor 不重新生成 `yizxianmod.refmap.json`，部署的 jar 丢 refMap，
生产启动即崩：`InvalidInjectionException ... could not find any targets matching 'setRemoved(...)'. No refMap loaded`。
**部署前验证**：`unzip -l yizxianmod-1.0.jar | grep refmap` 必须有输出。

## ✅ Agent 动态加载方案（2026-08-12 生产实测）

**savedProps + attach 方案（成功，`/yiz agent` 显示 isLoaded=true）**：
1. Unsafe 修改 `jdk.internal.misc.VM.savedProps`（HashMap）的 `jdk.attach.allowAttachSelf=true`（用 Map.put，非 setProperty）；
2. `Class.forName("sun.tools.attach.HotSpotVirtualMachine", true, providerLoader)` 触发 `<clinit>` 读到 true → ALLOW_ATTACH_SELF=true；
3. `VirtualMachine.attach(pid)` + `vm.loadAgent(agentPath)` + `detach`。
注意：`ALLOW_ATTACH_SELF` 是 final static，直接 Unsafe 改会被 `<clinit>` 重置 + JIT 折叠失效（HelperLib 方案=我们最初方案，生产失败）；`loadAgent0` 加载完整 agent jar 抛 InternalError；只有改 savedProps 后触发 `<clinit>` 才成功。

**`/yiz agent` 完整诊断**（避免误判）：AgentBridge 维护 `agentmain已执行/transformer已注册/实际transform类数/最后错误`，
agent 的 transformer 每次 transform 反射调 `AgentBridge.recordTransform()` 上报。判定：
`transformCount>0`=注入真正生效；`已注册但 transform=0`=类未重载；`attach成功但 agentmain未执行`=静默失败。

## ⚠️ 部署后必须验证 jar 内容，不能只看文件时间戳（2026-08-21 一轮里连踩两次）

**坑 1：改了哪个模组就要部署哪个模组。** 本次改动全在 yizxianmod，却只部署了 yizmodqzk，
mods 里的 yizxianmod 还是前一天的 —— 测试结果「改动完全没生效」，白跑一轮。
双模组项目部署后**两个 jar 的时间戳都要看**。

**坑 2：jar 可能内容残缺。** 出现过 mixin 已引用某类、但该类不在 jar 里，
运行到那条路径就 `NoClassDefFoundError` 崩服（本次是 `SustainedHealthSuppression`，
玩家击杀触发 die 时崩）。文件存在、时间新，不代表内容完整。

**部署后三项验证（都用 unzip/javap 查 jar 内部，别靠推测）：**
① 新增/修改的类在不在：`unzip -l xxx.jar | grep 类名`；
② refmap 在不在、条目全不全：`unzip -p xxx.jar xxx.refmap.json`，逐个核对新 mixin；
③ 前置库 reobf 到不到位：`javap` 看 HealthChannels 应是 `EntityDataSerializers.f_135029_` 而非 `FLOAT`。
判断「最新代码是否真在 jar 里」用 `javap` 查新方法的 SRG 名，比看 mtime 可靠。

相关：[[quanshouzhe-mhzy-defense-1-20-1]] [[mixin-unique-static-clinit-crash]] [[entity-presence-hardening-1-20-1]]
