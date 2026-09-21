---
name: jdk-17-gc-crash
description: "生产环境 JDK 17.0+35 + -XX:-UseCompressedClassPointers 导致 GC 线程崩溃（jvm.dll + EXCEPTION_ACCESS_VIOLATION 0xc0000005），非模组代码。换新版 JDK 17 或 JDK 21，去掉该参数。"
metadata:
  type: reference
---

# 生产环境 JDK GC 崩溃（2026-08-22 两次复现）

## 现象

`EXCEPTION_ACCESS_VIOLATION (0xc0000005) at pc=... jvm.dll+0x1acae6`，崩溃线程 `GCTaskThread "GC Thread#N"`。
hs_err 里 **`Java frames` 段为空**、搜不到任何模组类（yiz/SuperSteve 等 0 命中）。

## 根因

1. **JDK 17.0+35**（2021-09 GA 首版，后续修过大量 G1 GC bug）；
2. **`-XX:-UseCompressedClassPointers`**（非常规参数，与 compressed oops 混用不稳定；当初为 agent 动态加载加的，但 agent 现用 savedProps+attach 不需要它）。

## 解决

- 换新版 JDK 17（17.0.13+，Adoptium Temurin）或 JDK 21；Forge 1.20.1 官方要求 17，JDK 21 一般也能跑。
- 去掉 `-XX:-UseCompressedClassPointers`。
- 改在 PCL 版本设置「游戏 Java」路径 +「JVM 参数」。

## 判断要点

**GC 线程崩溃 + 无 Java frames + 搜不到模组类 = JVM 环境问题，不是模组代码**。模组代码只会抛 Java 异常（有 Java frames + 能定位到类），不会让 GC 线程在 jvm.dll 里段错误。遇到这类崩溃别去查模组，先看 JDK 版本和 JVM 参数。
