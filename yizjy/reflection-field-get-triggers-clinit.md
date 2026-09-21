---
name: reflection-field-get-triggers-clinit
description: 反射 Field.get 读静态字段会触发声明类 <clinit>（类初始化），遍历所有类时把第三方库的 <clinit> 引爆；修复=用 Unsafe.staticFieldBase/staticFieldOffset+getObject 读静态字段，绕过类初始化
metadata:
  type: project
---

# 反射 Field.get 触发第三方 <clinit>（2026-08-24 实锤）

## 现象

`NoSuchFieldException: REGISTRY`（Forge 包成 `ObfuscationReflectionHelper$UnableToFindFieldException`），
栈从 `RegistrateLootTableProvider.<clinit>` 抛，但**真正的调用方是 yizmodqzk 的
`ExternalHealthStore.scanMaps` 里的 `Field.get(null)`**。

## 根因

`ExternalHealthStore.scanMaps` 为扫描"外部静态藏血 Map"，反射遍历**所有已加载类**的静态字段，
对匹配的字段 `f.setAccessible(true); f.get(null)`。

**`Field.get` 会触发声明类的 `<clinit>`（类初始化）**。遍历所有类时，碰到了 `RegistrateLootTableProvider`
这个第三方库类，它的 `<clinit>` 里用 `ObfuscationReflectionHelper` 找 `LootContextParamSets.REGISTRY` 字段，
但 Registrate 1.3.11 与 MC 1.20.1 Forge 47.4.22 不匹配（`REGISTRY` 字段不存在）→ 类初始化失败抛异常。
异常被 `scanMaps` 的 try-catch 吞了（游戏没当场崩），但 `RegistrateLootTableProvider` 被 JVM 标记为
"初始化失败"，后续访问抛 `NoClassDefFoundError`。

## 修复

用 Unsafe 读静态字段，**不触发 `<clinit>`**：

```java
sun.misc.Unsafe u = UnsafeAccess.get();
Object base = u.staticFieldBase(f);      // static 字段的 base = 声明类 Class
long offset = u.staticFieldOffset(f);
Object v = u.getObject(base, offset);    // 绕过类初始化
// 兜底：Unsafe 不可用时才回退 f.get(null)
```

## 核心教训

- **反射 `Field.get`（尤其 `get(null)` 读 static）会初始化声明类**，遍历"所有类"的反射扫描会把第三方库的
  `<clinit>` 全部引爆。凡是"扫全量类/字段"的反射逻辑，静态字段一律用 `Unsafe.staticFieldBase/Offset + getObject` 读。
- 这条和 [[mixin-unique-static-clinit-crash]] 是两码事：那是 mixin @Unique 静态字段合并进目标类 `<clinit>`；
  这是反射读字段触发**别人**的 `<clinit>`。

相关：[[health-map-tamper]] [[external-mod-set-byte-boolean-crash]]
