---
name: modding-tech-landscape
description: "模组技术深度谱系、关键术语释义（Instrumentation/AT/TransformationService/VTable）、与竞品强度对比"
metadata:
  type: reference
---

## 模组技术深度谱系

```
第 6 层: JVM Shellcode / jvm.dll hook          ← 尚未实现，之前讨论过
第 5 层: VTable Method* 覆写                    ← yiz 独有
第 4 层: Unsafe 字段直写 + EntityLookup Map 反射 ← yiz 实现了
第 3 层: Java Agent + Instrumentation           ← yiz + DDDD 都有
第 2 层: TransformationService (新版 coremod)    ← DDDD 有，yiz 没有
第 1 层: Mixin                                  ← yiz 主力，大部分模组
第 0 层: NeoForge Event / API                   ← 常规模组
```

## 关键术语

- **Instrumentation**: Java Agent 从 JVM 拿到的 API。可以 `redefineClasses()`（运行时替换类字节码）、`retransformClasses()`（重新触发已注册 Transformer）、读任意类原始字节码。比 Mixin 强在"已加载的类也可以随时改"，不限于类加载时。

- **Access Transformer (AT)**: 一个配置文件，告诉 ModLauncher "把 private/protected 改成 public"。让代码可以直接访问内部字段，不需要反射。DDDD 用它把 EntityLookup/EntitySectionStorage 全开了。

- **TransformationService**: NeoForge 的新版 coremod。在 ModLauncher 最底层启动阶段执行，可以修改类的字节码、父类、接口。比 Mixin 早两轮（Mixin > Event）。DDDD 用它把 ServerLevel 子类注入到了世界创建流程中。

- **VTable 替换**: yiz 的独有技术。HotSpot JVM 虚方法分发走 vtable（每个类一个，存 Method* 指针）。VTableReplace 用 Unsafe 直接覆写 Method* 的 `_from_interpreted_entry` 和 `_from_compiled_entry`，相当于在 C++ 层"撤销"了子类的 override。Java 反射看不到任何异常。用于 ItemAbolitionHelper/EntityAbolitionHelper。

- **MethodHandle unreflectSpecial**: `MethodHandles.Lookup.IMPL_LOOKUP` + `unreflectSpecial()` 锁定基类的具体方法实现，调用时跳过虚方法分发表。效果：即使子类 override 了 `remove()`，也能直调 `Entity.remove()` 原实现。

- **EntityLookup.Map 反射**: 不等 `Entity.remove()` 管道的最后手段。无论 Entity.remove/ChunkSource.removeEntity 被 override 多少次，直接找到 EntityLookup 内部的 `byId`(Int2ObjectMap) 和 `byUuid`(Map<UUID,Entity>) 按 key 删。实体瞬间从世界消失，不经过任何方法调用。

## 竞品分析

### Infinity God Sword (最终幻想:寰宇支配之剑, more_avaritia)
- 攻击力 ∞ (Float.POSITIVE_INFINITY)，256 格射线 AOE，Shift 左键"强制清除实体"
- **清除机制**: `setHealth(0)` + `remove(RemovalReason.KILLED)` — 纯 API 调用
- **弱点**: 遇到 yiz 的 die/remove 拦截 + isProtectedByUuid → 全被拦截
- **级别**: API 层（第 0 层），yiz 碾压

### DDDD (seraphina, dddd-1.0-SNAPSHOT)
- 技术栈: TransformationService + Agent + AT + LaunchPluginService
- **清除机制**: FuckEntityGetter 替换 EntityLookup 的 getter，在 getAll() 里过滤 KKK 标记实体（byId.remove + byUuid.remove）
- **级别**: EntityLookup 数据结构层（和 yiz EntityForceRemoveMixin 同深度）
- **优势**: TransformationService 可以替换整个 ServerLevel 子类
- **劣势**: 没有 VTable、没有 Unsafe 字段直写
- **结论**: 深度相当，路线不同。yiz 走 Unsafe/VTable，DDDD 走 TransformationService/AT

## yiz vs 全体对比

| 技术 | yiz | DDDD | 寰宇支配之剑 | 常规模组 |
|------|-----|------|-----------|---------|
| Mixin | ✅ | ❌ | ❌ | ✅ |
| Agent | ✅ | ✅ | ❌ | ❌ |
| AT | ❌(用反射替代) | ✅ | ❌ | 少数 |
| TransformationService | ❌ | ✅ | ❌ | 极少数 |
| VTable | ✅ | ❌ | ❌ | ❌ |
| Unsafe 字段 | ✅ | ❌ | ❌ | ❌ |
| EntityLookup Map | ✅ | ✅ | ❌ | ❌ |
