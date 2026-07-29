---
name: module-export-package-conflict
description: "NeoForge 模块启动崩溃 'Modules X and Y export package Z' 的根因和排查方法"
metadata:
  type: feedback
---

## 症状

```
java.lang.module.ResolutionException: Modules tizmod and yizmodqzk
export package net.minecraft.client.yiz.mixin to module yizxianmod
```

## 根因

`run/mods/` 目录下有**多个 jar 同时包含同一个 package**（如旧版本 jar 残留），Java 模块系统禁止两个模块导出同一个包。

## 排查

```bash
# 找出所有包含同名 package 的 jar
find "D:/ZM/yizgzq" -name "*.jar" | while read jar; do
  count=$(unzip -l "$jar" 2>/dev/null | grep -c "<package/path>")
  [ "$count" -gt 0 ] && echo "$jar: $count"
done
```

## 修复

删除 `run/mods/` 下的旧 jar（如 `tizmod-1.0.0.jar`），只保留当前版本。

**Why:** 本次 session 因 `run/mods/tizmod-1.0.0.jar`（旧残留）与 `yizmodqzk-1.0.0.jar`（当前）同时包含 `net/minecraft/client/yiz/mixin/` 类，导致 NeoForge 模块解析失败。删除旧 jar 后立即恢复正常。

**How to apply:** 遇到此类 Module.ResolutionException，直接 grep `run/mods/` 下的 jar 包内容，清理重复 package 的旧 jar。
