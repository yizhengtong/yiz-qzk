---
name: mixin-gotchas-1-21-1
description: "Mixin 1.21.1 踩坑清单：refmap 缺失导致部分注解失效、方法名映射、LiquidBlock 双重陷阱"
metadata:
  type: feedback
---

## Mixin 1.21.1 关键踩坑

### refmap 缺失 → 注解选取规则
项目 `yizmodqzk.refmap.json` 并不实际生成（jar 里没有该文件，但 `mixins.json` 仍声明了它）。这意味着依赖 refmap 做「短方法名→运行时映射名」解析的注解会静默失败。

**经验证各注解可用性（2026-07-30 复核）：**
- ✅ `@Inject(method=..., at=@At("HEAD/RETURN/TAIL"), cancellable=true)` + `cir.setReturnValue/ci.cancel()` —— 最稳，首选。**短方法名可命中**（已验证 `getMaxStackSize`/`limitSize` 等无重载简单方法名 HEAD 注入成功，日志 "does use it's CallbackInfo"）。`@At("RETURN")` + 完整描述符定位接口 default 方法也成功。
- ✅ `@ModifyVariable(at=@At("HEAD"), argsOnly=true, index=N)` —— 按参数位置匹配，不依赖 refmap，可用（`StartupItemRegistryReplaceMixin`）。但**单纯 `@ModifyVariable(method="短名")` 不带 index 不可靠**（曾在 `limitSize` 上静默失效）。
- ✅ `@Redirect` —— 可用（`HumanoidArmorLayerStarMixin` 用了且工作）。需显式 `target`。
- ⚠️ `@ModifyArg` / `@ModifyExpressionValue` —— 这两类对「短方法名 + refmap 解析」依赖最强，历史上报过 `No refMap loaded` / `Scanned 0 target(s)`。要用必须给**完整描述符 target**（如 `@At(value="INVOKE", target="Lnet/.../Class;method(...)V")`），否则不要用。

**How to apply:** 优先 `@Inject` + 短名/描述符。改方法参数用 `@Inject` HEAD 捕获参数 + `ci.cancel()` 自行处理（比 `@ModifyArg` 稳），或 `@ModifyVariable(argsOnly=true, index=N)`。`@ModifyArg`/`@ModifyExpressionValue` 仅在能写完整描述符 target 时考虑。方法名在 NeoForm 源码 `.build/neoForm/.../transformed/` 中查找。

### `GameRenderer.pick(float)` 不存在于运行时映射
编译通过但运行时 `No refMap loaded`。需选取准星事件时，目标改为 `Entity.pick(double,float,boolean)`（已验证可用）或直接 `Minecraft.getInstance()`。

### 客户端 Mixin 注入 common 类
`@Mixin` 目标为 common 类（如 `LiquidBlock`）但只需客户端生效时，将 Mixin 放入 `mixins.json` 的 `client` 数组。可在 Mixin 内安全调用 `Minecraft.getInstance()` 获取玩家实例。

## LiquidBlock 双重陷阱 — 为什么流体挖不掉

### 陷阱 1：`getShape()` 返回 `Shapes.empty()`
`LiquidBlock.getShape(BlockState, BlockGetter, BlockPos, CollisionContext)` 始终返回空形状，射线穿透流体。即使设置 `ClipContext.Fluid.ANY` 也无效——`getBlockShape()` 返回空，`blockhitresult` 永远为 null。
**修复**：Mixin 覆写 `getShape()` 返回 `Shapes.block()`（client 侧）。

### 陷阱 2：`onDestroyedByPlayer` → `createLegacyBlock()` 原地复活
服务端 `destroyBlock` 流程中 `BlockStateBase.onDestroyedByPlayer` 调用 `level.setBlock(pos, fluidState.createLegacyBlock(), 3)`。对流体方块，`createLegacyBlock()` 返回的就是流体自身——方块被"破坏"后原地复活。
**修复**：Mixin `ServerPlayerGameMode#removeBlock` HEAD，是流体且满足条件时直接用 `Blocks.AIR` 替换。

**Why:** 本次实现挖掘属性时，先后踩了 `@ModifyExpressionValue` 崩溃、`GameRenderer.pick` 崩溃、`FluidPickContext` 放 mixin 包崩溃、ThreadLocal 方案两次无效、最后才发现 `LiquidBlock.getShape()` 返回空。浪费了数小时。

**How to apply:** 以后涉及流体方块交互，直接覆写 `getShape()`（client）和 `removeBlock`（server），不要绕弯子。
