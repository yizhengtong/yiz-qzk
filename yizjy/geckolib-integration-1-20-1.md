---
name: geckolib-integration-1-20-1
description: GeckoLib 4.8.4 接入 yizxianmod 渲染两只独立 Boss（邪狱龙 xieyulong + 踏虚体 taxuti）的方法与坑（离线装 jar、GeoModel 直写路径、弹道空渲染器 NPE、YizxianMob 非 PathfinderMob）。⚠️ 2026-09-10 两只实体与 GeckoLib 依赖已全部移除，本文留作将来再接入的参考。
metadata:
  type: project
---

# GeckoLib 4.8.4 接入 1.20.1（邪狱龙 xieyulong + 踏虚体 taxuti）

> ⚠️ **2026-09-10 已移除**：用户要求“移除除辖界者外全部实体 + 去掉 GeckoLib 依赖”——邪狱龙/踏虚体及全部技能弹道实体、GeoModel/GeoRenderer/ShadowLayer、刷怪蛋与星级蛋、geo/animations/纹理/模型 json/配方/语言条目均已删除，`build.gradle` 与 `mods.toml` 的 geckolib 依赖也已移除。当前模组只剩辖界者（原版 Warden 模型，不用 GeckoLib）。**本文保留为将来再接入时的参考**，勿再按它去代码里找这些类。

2026-08-15 起 yizxianmod 引入 GeckoLib 渲染**两只独立 Boss**（⚠️ 踏虚体不是邪狱龙的影分身，是另一个实体）：

- **邪狱龙** `xieyulong` = opensrp 的 `draconite`（85 骨/356 cube/6 动画，飞行三技能：火球/陨石/毒云）
- **踏虚体** `taxuti` = opensrp 的 `kirin`（73 骨/256×256/4 动画，地面近战 + orb 球 + 黑色影子层 + 传送）

## 依赖接入（离线）

- `dl.geckolib.com` 被 TLS 阻断，jar 直接取自游戏 mods 目录 `geckolib-forge-1.20.1-4.8.4.jar`，手动装 mavenLocal（`~/.m2/.../software/bernie/geckolib/geckolib-forge-1.20.1/4.8.4/` + 手写 pom），build.gradle 加 `implementation fg.deobf("software.bernie.geckolib:geckolib-forge-1.20.1:4.8.4")`，mods.toml 加 geckolib 依赖。

## 实体模式（与血量保护兼容）

`X extends YizxianMob implements GeoEntity`——YizxianMob 是 `Mob` 非 `PathfinderMob`，GeckoLib 只要求 `GeoEntity` 接口，全套血量保护保留。

## 坑

1. **DefaultedGeoModel 路径约定**：`subtype()` 会加进 geo/animations 路径子目录 → 找 `geo/entity/xxx.geo.json` 崩 `Unable to find model`。**改直接继承 `GeoModel` 显式写三资源路径**。
2. **YizxianMob 非 PathfinderMob**：`WaterAvoidingRandomStrollGoal`/`MeleeAttackGoal` 构造要 PathfinderMob，编译不过；用 `FloatGoal`/`LookAtPlayerGoal`/`RandomLookAroundGoal` + 自写近战/追击 goal。
3. **弹道实体无渲染器会 NPE**：`EntityRenderDispatcher.shouldRender` 对 null renderer 直接 NPE（非忽略）→ 火球/陨石/毒云/orb 球都要注册 `NoopEntityRenderer`（空渲染器，靠 vanilla 粒子显示）；火球/陨石也可用 `FireballEntityRenderer`（billboard 贴 fire_ball.png）。
4. **GeckoLib 渲染层**：`GeoRenderLayer`（不是 RenderLayer），`GeoEntityRenderer.addRenderLayer(...)` 加自定义层；踏虚体黑色影子 = `TaxutiShadowLayer`（dark 纹理半透明叠加，上移 1.5、alpha 0.5，用 `getRenderer().reRender(...)`）。

## 文件位置

- 邪狱龙 `entity/XieyulongEntity.java` + `entity/skill/Xieyulong{Fire,Meteor,PoisonCloud}Entity.java` + 模型/渲染器 + 刷怪蛋
- 踏虚体 `entity/TaxutiEntity.java` + `entity/skill/TaxutiOrbEntity.java` + 模型/渲染器/`TaxutiShadowLayer` + 刷怪蛋
- 资源 `assets/yizxianmod/{geo,animations,textures/entity}/{xieyulong,taxuti}.*`（+ `_dark` 暗形态纹理）
- 注册 `YizxianEntityTypes` + `YizxianMod` + `YizxianModClient`

## 已知无害警告（勿误判）

- `Agent failed to start!` = agent self-attach 失败（已知 issue，agent 有降级不影响血量扫描）。
- `EntityActuallyHurt reflection init failed: health` = 1.20.1 血量在 DATA_HEALTH_ID 无 health 字段，反射失败是预期。
