---
name: neoforge-maven-offline-build
description: "maven.neoforged.net 被 TLS 阻断时的离线构建方案：本地 maven 仓库 + gradle/neoform 缓存 + build.gradle 改动，含全部坑与已下载文件清单"
metadata:
  type: project
---

# NeoForge maven 离线构建（网络阻断绕过）

## 现状（2026-08-04）

- **maven.neoforged.net 的 HTTPS 被 TLS/SNI 阻断**（`Connection reset`，curl 与 Java 均失败；HTTP 301 到无效 `0.0.0.0`；ping 通但所有 IP 握手失败，典型 GFW 特征）。
- **`maven.minecraftforge.net`、`piston-meta.mojang.com` 可用**（200）。
- 系统代理 31181 是 **DeepSeek 反代**（`DS-Proxy`，未知请求返回 500），不可用作翻墙代理；本机无 Clash/V2Ray。
- 影响：yizxian1.21.1 任何 `./gradlew` 构建/启动都需要 NeoForge 依赖，网络不通则失败。**改库后必须走离线方案**。

## 离线方案（已跑通，2026-08-04）

1. **手动下载缺失文件到 `D:\ZM\yizgzq\dl-deps\`**（用户用其他网络下载）。清单见下。
2. **搭本地 maven 仓库 `D:\ZM\yizgzq\local-maven-repo\`**：按 maven 斜杠路径放 jar + **手写最小 pom**；**neoforge / neoform-runtime 必须放官方完整 `.module`**（否则 capability variant 匹配失败）。
3. **build.gradle**（repositories 块）加：
   ```groovy
   maven {
       url = uri('file:///D:/ZM/yizgzq/local-maven-repo')
       metadataSources { mavenPom(); gradleMetadata() }   // 关键：默认只读 pom，不读 .module
   }
   // 项目底部：
   afterEvaluate { /* 把本地仓库移到 index 0 */ }
   ```
   `afterEvaluate` 把本地仓库移到最前，**覆盖 ModDevGradle 的 `sortFirst`**（否则 maven.neoforged.net 排第一，连接失败即整体报错，本地仓库不被尝试）。
4. **neoform 运行时（`createMinecraftArtifacts`）有独立 ArtifactManager 缓存**：`~/.gradle/caches/neoformruntime/artifacts/net/<斜杠group>/<artifact>/<version>/`，缺 jar 时**手动复制到该路径**（如 srgutils-0.4.15.jar）。

## 关键坑（每条都真实踩过）

1. **gradle modules-2 缓存 group 目录用「点号」**（`net.neoforged` 是单个目录名），不是斜杠；`files-2.1/<group>/<module>/<version>/<SHA1>/<文件名>` 的子目录名 = **文件内容 SHA1**。
2. **metadata-2.107/descriptors 缓存**：网络通时解析过的模块 descriptor 已缓存。只缺 jar 时，放 `<SHA1>/<filename>` 即命中。descriptor 来自 `.module`（含 files sha1）→ 内容寻址命中；来自 pom（无 sha1）→ 走 URL 缓存（`resource-at-url.bin`），**必须让 Gradle 从本地仓库重新解析**（排最前）。
3. **capability variant 匹配失败**（`Unable to find a variant with the requested capability: ...external-tools / ...moddev-bundle`）= 缺完整 `.module`。neoform-runtime 的 `externalTools` variant、neoforge 的 `modDevBundle`/`modDevConfig`/`installerJar`/`universalJar`/`sourcesElements` 都在官方 `.module` 里。**neoforge.module 里 modDevBundle 依赖 `net.neoforged:neoform:1.21.1-...`（zip），其已在缓存**。
4. **`.minecraft\libraries\net\neoforged\`**（游戏运行时库）有部分开发工具正确版本（at-modlauncher-10.0.1、mergetool-2.0.0-api、accesstransformers 等），可直接复制——先搜这里再让用户下载。
5. **`build/moddev/artifacts/` 的 jar 是 ModDevGradle 处理后的产物**（大小/sha1 与 maven 原始不同），不能当 maven artifact 用。
6. 运行 `runClient` 还需 **DevLaunch-1.0.2.jar**（开发启动器，`net.neoforged:DevLaunch:1.0.2`），本地无缓存，需手动下载。
7. 全首者模型骨骼坑（本次崩溃根因）：`right_tendril` 在 **`head → left_tendril2 → right_tendril`** 深层，不是 root 直接子；`left_tendril` 才是 root 直接子。`ModelPart.getChild` 找不到会 `NoSuchElementException` 直接崩。

## 已下载文件清单（dl-deps，共 13 个）

mergetool-1.1.7-fatjar / mergetool-1.1.7-api / srgutils-0.4.15（从 minecraftforge 直下）；
mergetool-2.0.3-fatjar / mergetool-2.0.3-api / neoforge-21.1.230-userdev / -sources / -installer / -universal / -moddev-config.json / .module / neoform-runtime-2.0.18-all / at-parser-13.0.1 / accesstransformers-13.0.1 / DevLaunch-1.0.2 / at-modlauncher-10.0.1 + mergetool-2.0.0-api（从 .minecraft/libraries 复制）。

**网络恢复前的每次构建/启动都依赖上述本地配置，勿删 `local-maven-repo` 和 `dl-deps`。**
