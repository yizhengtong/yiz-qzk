---
name: photon-code-particle-api
description: "Photon 纯代码粒子 API 与 8 个坑：纹理完整路径、ARGB 颜色、ADDITIVE 亮度累积、billboard 非体积圆、ObjModelSource 加载模型、simulationSpace Local/World、反射构造 protected 发射器；以及调参应理解渲染特性而非重启试错"
metadata:
  type: project
---

# Photon 纯代码粒子（不走编辑器 FX 文件）

## 代码创建链路
`ParticleConfig`（Unity 式配置）→ 反射构造 `ParticleEmitter(config)`（**该构造 protected**，Photon 设计走 FX 文件反序列化，纯代码需 `getDeclaredConstructor().setAccessible(true)`）→ `new FXData(List.of(emitters))` → `new FXRuntime(data)` → `runtime.emmit(IEffectExecutor)`。Executor 的 `updateFXObjectFrame(fxObj, partialTicks)` 每帧回调，在这里 `emitter.setPos(...)` 跟随位置最平滑。

## 8 个坑（每个都是重启验证过的）
1. **纹理路径要完整**：`TextureManager` 按 id 原样找资源，`TextureMaterial` 的 id 必须是 `photon:textures/particle/circle.png`（含 `textures/` 前缀 + `.png`）。`photon:particle/circle` 或 `photon:textures/particle/circle` 都 FileNotFoundException。
2. **颜色是 ARGB**：`NumberFunction.color(0xAARRGGBB)`。如 `0xCCFFAA33`（A=CC R=FF G=AA B=33 橙黄）。传错方向会变绿/橙。
3. **ADDITIVE 加色混合会亮度累积**：粒子 RGB 叠加 → 白亮、均匀色块。要半透明质感必须换 alpha 混合：反射替换 `MaterialSetting.blendMode` 为 `new BlendMode(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, BlendFuc.ADD)`。
4. **billboard 是平面圆片不是体积圆**：`Mode.Billboard` 粒子始终面向相机（椭圆问题来自默认非 Billboard 模式，需显式 setRenderMode）。要"体积圆"用 `Mode.Model` + sphere.obj + `setShade(true)`（法线明暗）。
5. **模型加载必须走 ObjModelSource**：`MeshData(ResourceLocation)` 会被 ModelBakery 当 JSON 模型（找 `models/models/x.json` 报错）。正确：`new MeshData(new ObjModelSource(ResourceLocation.parse("photon:models/sphere.obj")))`。
6. **simulationSpace 决定跟随还是残留**：`Space.Local` 粒子跟随发射器 transform（光球本体用）；`Space.World` 粒子留在出生点（拖尾用，发射器移动即留下尾迹）。粒子默认发射后独立，不设 Local 光球会停在原地。
7. **Cone shape 做火焰**：`c.shape.setShape(new Cone())` + `cone.setAngle/setRadius` + `setStartSpeed` 沿锥面喷射（火焰向上；方向不对需调 rotation）。
8. **调参教训**：Photon 视觉（亮度/混合/形状）受多个机制叠加影响（混合模式、HDR、rate、RGB 值），**别靠"改一参数→重启游戏→用户看"的慢循环试错**——先想清楚渲染特性（如"太亮"的根因是 ADDITIVE 累积而非 HDR），或让用户用 `/photon_editor` 可视化调参一次到位。

## 内置资源
- 模型（6 个几何体，无火焰）：capsule/cube/cylinder/plane/quad/sphere
- 粒子纹理：circle/kila_tail/laser/ring/smoke/thaumcraft
- 依赖：photon-neoforge-1.21.1:2.2.2 + 显式 ldlib2 2.2.31:all（POM 里是 runtime scope，编译需显式声明）；仓库 maven.firstdark.dev/snapshots

## 结论（2026-08-04）：本 mod 已放弃 Photon，回退自研着色器
昭明光特效在深改 Minecraft（sodium/iris + agent 注入）环境下，Photon 粒子 HDR 管线过曝不可控（任何颜色最终洗白），纯代码 + 官方 FX 文件两条路都验证失败，**已回退到自研 shader 的 Plasma 球**（`ZhaoMingLightWorldRenderer` + `ZhaoMingLightShaders`）。Photon 依赖已从下游 build.gradle 移除。**不要轻易再为特效引入 Photon**；如需粒子特效优先自研/vanilla 粒子。另外**特效设计要"思维拆解"**（多子系统独立行为+相互作用）与**同色系藏色**（暗色阶多色搭配），这两点是本用户的核心方法论。
