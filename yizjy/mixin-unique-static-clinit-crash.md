---
name: mixin-unique-static-clinit-crash
description: "Mixin 陷阱：mixin 里 @Unique static 字段初始化引用 vanilla 字段（如 EntityDataSerializers.FLOAT）会被合并进目标类 <clinit>，生产环境 SRG 名未重映射 → Bootstrap 期 NoSuchFieldError。修复=把通道/常量定义移到独立 holder 类，mixin 只引用。"
metadata:
  type: project
---

# Mixin @Unique 静态字段 → 目标类 <clinit> 生产环境崩溃（2026-08-10 实锤）

## 现象

游戏启动即崩（Bootstrap 期，主菜单前）：
```
NoSuchFieldError: Class net.minecraft.network.syncher.EntityDataSerializers does not have member field 'EntityDataSerializer FLOAT'
    at LivingEntity.<clinit>(LivingEntity.java:157)
    at EntityType.<clinit> → Items.<clinit> → Bootstrap
```

## 根因

mixin 里写了：
```java
@Unique
private static final EntityDataAccessor<Float> yizmodqzk$FE_GET_HEALTH_DATA =
    SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
```
Mixin 会把 `@Unique` 静态字段的**初始化代码合并进目标类（LivingEntity）的 `<clinit>`**。
生产环境运行用 SRG 名（`EntityDataSerializers.FLOAT` → `f_110745_`），但这段合并进 vanilla 类的代码里
`EntityDataSerializers.FLOAT` 引用**不会被正确重映射** → Bootstrap 期 GETSTATIC 解析失败 → NoSuchFieldError。

**判定要点**：用排除法（mods 目录只留自己的 mod）可复现 → 必是自己的 mixin @Unique 静态字段初始化了
vanilla 字段/类。旧备份 jar 里同字段存在但不崩，是因为当时游戏 mods 目录用的是别的构建/版本。

## 修复（通用做法）

把引用了 vanilla 字段/类的静态成员定义移到**独立 holder 类**（自己 jar 里的普通类，reobf 会正确映射），
mixin 只 `HealthChannels.DELTA_HEALTH` 这样引用；不再在 mixin 里声明带初始化的 @Unique 静态字段：
```java
public final class HealthChannels {
    public static final EntityDataAccessor<Float> DELTA_HEALTH =
        SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
}
```
顺带：反射 `LivingEntity.class.getDeclaredField("yizmodqzk$FE_GET_HEALTH_DATA")` 也改读 holder。

## 补充：holder 顶层字段仍会被 mixin GETSTATIC 提前触发（2026-08-24 实锤）

把 defineId 移到 holder 类只解决了「clinit NoSuchFieldError」，但 **holder 顶层 `public static final` 字段仍会被 mixin 的 GETSTATIC 提前触发**：`SynchedEntityDataMixin` 的 set 方法里 `HealthChannels.SECURE_OBF`（GETSTATIC）在 mixin 应用阶段（class transformation 字节码链接）触发 holder `<clinit>` → defineId 抢占 vanilla Entity 的 DataParameter id 0 → 后续实体（ItemEntity 掉落物等）构造报 `Duplicate id value for 0` 崩溃。

修复=再套一层懒加载（嵌套 holder + getter）：GETSTATIC 改成 INVOKESTATIC 方法调用（字节码链接只解析方法签名、不触发类加载），defineId 延迟到首次真正访问（首实体 defineSynchedData 后，vanilla Entity 已 defineId）。引用点全部从 `HealthChannels.XXX` 字段改成 `HealthChannels.getXxx()`。

## 补充 2：实体类自己的 DATA_* 也要懒加载（2026-08-26 实锤）

辖界者（QuanshouzheEntity）的 4 个 DATA_*（CAST_PHASE/RAGING/HEAVY_ATTACK/FORM_PHASE）是普通 `static final` 字段，`SynchedEntityData.defineId(QuanshouzheEntity.class, ...)` 在类加载期执行。某个改动（删山林首者 / 改血修复）导致 QuanshouzheEntity 类被提前加载（vanilla Entity 之前）→ defineId 抢占 id 0 → 召唤报 `Duplicate id value for 0`，且全局 DataItem id 错乱 → 玩家存盘读错类型（`Byte→Integer` ClassCastException）。

修复=同样套 holder + getter 懒加载（DATA_* 移入嵌套 DataHolder，引用点改 getter 方法调用），defineId 延迟到实体构造时（vanilla 已 defineId）。

教训：**任何 `EntityDataAccessor` 的 defineId，无论放 mixin @Unique、holder 顶层、还是实体类自己的 static final 字段，都可能在 vanilla Entity 之前被触发而抢占 id 0；统一用嵌套 holder + getter 懒加载最稳。**

## 安全/危险判别

- **危险**：@Unique static 字段初始化引用 vanilla 字段/类（EntityDataSerializers/ResourceLocation/
  Component/Attribute 等）——合并进目标 <clinit>，生产重映射炸。
- **安全**：@Unique static 字段初始化为纯 Java 类型（`new ConcurrentHashMap<>()`、double 字面量、
  `Math.log(...)`）——无 vanilla 引用，无重映射问题。
- **安全**：@Inject 进 `<clinit>` 的方法体里引用 vanilla 字段（若 refmap 正确）——但这依赖 refmap，
  能避免就避免，优先 holder 类。

## 相关

- [[mixin-gotchas-1-21-1]] mixin 其它踩坑（refmap 不生成/@ModifyArg 描述符/LiquidBlock）
- [[120-blood-dataparameter-port]] 1.20.1 血量 DataParameter 差异（delta 通道正是 FE_GET_HEALTH_DATA）
