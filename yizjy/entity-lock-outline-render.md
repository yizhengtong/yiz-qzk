---
name: entity-lock-outline-render
description: "1.20.1 锁定系统实体描边渲染完整演进与最终解：FBO+全屏后处理（穿墙 + 屏幕固定宽度 + 纹理alpha过滤 + 发光），复刻原版光灵箭，不依赖 Fabulous"
metadata:
  type: reference
---

# 1.20.1 实体描边渲染（锁定系统/会心渴攻）— 最终方案

## 结论（2026-08-28，用户确认效果 99% 完美）

**最终方案**：FBO + 全屏后处理（复刻原版光灵箭 glow，不依赖 Fabulous 画质，自己管 FBO）。两条 pass：

1. **填充 pass**（`rendertype_lock_fill`，AFTER_ENTITIES）：
   - 把锁定实体**穿墙（NO_DEPTH_TEST）渲染成纯白填充**到专用 FBO（`LockOutlineFramebuffer`，RenderTarget 匿名子类）。
   - fill fsh 采样实体主纹理 alpha，`<0.1 discard`（**纹理过滤**：透明镂空区不填充）。
   - 用 `EntityRenderer.render` 直接调用（跳过 shadow/火焰/hitbox）。
2. **后处理 pass**（`rendertype_lock_post`，全屏 NDC quad）：
   - 采样 FBO 做**邻域填充密度高斯**：density≈0.5（填充边缘带）→ 描边色，内部(≈1)/外部(≈0)→透明。
   - 描边宽度 = **屏幕固定像素**（POST_RADIUS），远近一致；高斯（EDGE_SHARPNESS）产生**发光晕**。
   - 青色，透明度=充能进度 charge。

## 关键坑（踩了多轮才通）

1. **RenderType 默认 `outputState=MAIN_TARGET`**：`CompositeStateBuilder` 默认 outputState，`endBatch` 时会把 framebuffer **切回主缓冲** → 填充 pass 实际画到主缓冲、FBO 空 → 描边完全丢失。必须自定义空 `OutputStateShard`（`KEEP_TARGET`，setup/clear 均为空 lambda）让填充/后处理**保持当前绑定**。
2. **后处理 quad 的 z 裁剪**：`ortho(0,w,h,0,100,300)`+z=0、`setOrtho(0,w,0,h,0.1,1000)`+z=500 都会被 GPU 裁剪（ortho z 是"沿 -z 距离"语义，正 z 在相机后方）。**正解：NDC 全屏 quad（顶点 -1..1、z=0）+ 单位投影矩阵**，clip=顶点坐标必然通过。
3. **`RenderSystem.setProjectionMatrix` 需两个参数**（Matrix4f, `VertexSorting`）；恢复投影要连带保存 `VertexSorting`（`RenderSystem.getVertexSorting()`）。
4. **RenderTarget 是抽象类**：`new RenderTarget(true) {}` 匿名子类用基类 createBuffers 建标准颜色+深度 FBO。
5. **fill shader 采样实体纹理**：渲染前 `RenderSystem.setShaderTexture(0, er.getTextureLocation(target))`，fill RenderType 保持 NO_TEXTURE 不覆盖手动绑定。
6. **EntityRenderDispatcher.render 重绘会画 shadow/火焰**（导致"脚下四格方块被涂色"）→ 用 `EntityRenderer.render` 直接调用，或过滤 `type.toString().equals("entity_shadow")` 走原版 buffer。
7. **1.20.1 `RenderSystem` 无 `glCullFace`**（只有 enableCull/disableCull），`CullStateShard` 只能 GL_BACK → Inverse Hull 版用 LWJGL `GL11.glCullFace(GL_FRONT)` 直透（FBO 版不需要，只作参考）。
8. **fill 的 FBO target `clearTask` 必须切回主缓冲**（2026-08-30，症状=实体本体消失只剩描边）：`LockOutlineRenderType.FILL_TARGET` 早期 `clearTask` 为 `() -> {}`（setup 绑 FBO、clear 空）。fill buffer 初始容量仅 256，描边复杂实体（GeckoLib 辖界者，顶点上千）时 `immediate` 在渲染中途**自动 endBatch 刷帧** → FBO 被绑定且**不切回主缓冲** → 之后实体本体 normal 渲染写进 FBO 而非主缓冲 → 主缓冲无实体本体（实体不渲染）、FBO 有描边（描边显示）。**修复**：FILL_TARGET `clearTask` 改 `Minecraft.getInstance().getMainRenderTarget().bindWrite(false)` + fill buffer 容量加到 65536（双保险）。

## 方案演进（避免重复探索）

- **v1**：自定义纯色 shader 重绘实体（穿墙填充，整片青色覆盖 + 脚下阴影被涂色）。
- **v2 Inverse Hull 细线描边**：膨胀背面+LEQUAL+GL_FRONT；径向膨胀保持棱角共点防断裂；纹理 alpha 过滤；rim 法线内外侧判定；CPU 按距离补偿宽度。缺点：**不能穿墙**（依赖场景深度）、宽度靠几何膨胀。
- **v3 FBO+后处理（最终）**：穿墙 + 屏幕固定宽度 + 纹理过滤 + 发光，全部满足。Inverse Hull 版被取代。

## v4（2026-08-29）：集成实体 render + 通用描边接口

**去掉 AFTER_ENTITIES 单独重渲染实体**，改为 vanilla `OutlineBufferSource` 模式：实体渲染时用 `VertexMultiConsumer.create()` **一次渲染双写**主缓冲 + 描边 FBO。

- **通用描边接口**（api 包）：`OutlineEntity`（实体实现 `getOutlineColor()`）+ `OutlineRegistry`（注册表：接口实现 → 按类型默认 → 动态 Provider）+ `EntityOutline`（公开门面：setWidth/setSharpness/register/registerDefault/getColor）。**任意实体可描边，锁定系统只是注册的一个 Provider**。
- **`LockOutlineBufferSource`**（MultiBufferSource 包装）：当前实体需描边时 `getBuffer(rt)` 返回 `VertexMultiConsumer.create(fillConsumer, normal)` 双写；fill 用 `FillColorConsumer`（继承 `DefaultedVertexConsumer`，**覆盖顶点色为描边色**，保留全部 NEW_ENTITY 属性）。
- **fill RenderType 带实体纹理 + FBO target**：`LockOutlineRenderType.fill(ResourceLocation tex)`（TextureStateShard 做 alpha 过滤 + OutputStateShard 绑 FBO），按纹理缓存。
- **mixin `EntityRenderDispatcher.render`**（所有实体渲染汇聚点）：`@Inject HEAD/RETURN` 设/清 `LockOutlineBufferSource.setCurrent(entity)`；`@ModifyArg`（index=4，**单参数 handler**，多参数 handler 0.8.5 报 invalid signature）把 buffer 换成 `LockOutlineBufferSource`。
- **fill fsh 输出 `vColor`（描边色）**；后处理 fsh **采样 FBO 颜色输出**（`c.rgb, c.a*edge`，不再硬编码青色）→ **支持任意描边色**。
- `LockOutlineRenderer`：AFTER_SKY 清 FBO（**注意 Stage 是 final class 非枚举，不能用 switch**）+ AFTER_ENTITIES flush fill + 后处理。

⚠️ `@ModifyArg` 多参数 handler（含被修改方法所有参数）在 mixin 0.8.5 报 `invalid signature`，必须用**单参数 handler + 静态字段判断**。

## 文件位置

`yizmodqzk`：
- `LockOutlineRenderer.java`（AFTER_SKY 清 FBO + AFTER_ENTITIES flush/后处理）
- `LockOutlineBufferSource.java`（双写 + FillColorConsumer）
- `LockOutlineFramebuffer.java`（RenderTarget 匿名子类 + bindAndClear/unbind/resize）
- `LockOutlineRenderType.java`（fill(tex) 带纹理+绑 FBO、post + `KEEP_TARGET`）
- `LockOutlineShaders.java`（MOD 总线 RegisterShadersEvent 注册 rendertype_lock_fill/post）
- `mixin/EntityRenderDispatcherOutlineMixin.java`（集成实体 render）
- `api/OutlineEntity.java`、`api/OutlineRegistry.java`、`api/EntityOutline.java`
- `shaders/core/rendertype_lock_fill.{json,vsh,fsh}`、`rendertype_lock_post.{json,vsh,fsh}`

参数：`LockOutlineRenderer.setPostRadius(px)` / `setEdgeSharpness(f)`（或 `EntityOutline.setWidth/setSharpness`）。旧 Inverse Hull 的 `rendertype_lock_outline.*` 已不注册，可留可删。
