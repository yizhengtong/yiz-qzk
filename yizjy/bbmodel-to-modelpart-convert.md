---
name: bbmodel-to-modelpart-convert
description: "Blockbench bbmodel 转原版 ModelPart 的关键坑：group origin 是世界坐标、原版渲染 scale(-1,-1,1) 需 X/Y 坐标与绕 X/Y 旋转取反、box_uv 逐面 UV 需图集重排、up/down 面 V 翻转、Renderer scale 的 translate 方向"
metadata:
  type: project
---

# Blockbench bbmodel → 原版 ModelPart 转换（加生物必读）

用 `tools/quanshouzhe_convert.py`（下游 yizxian1.21.1）把 Blockbench bbmodel（optifine_entity/free 格式）转成原版 Java ModelPart + 合并图集 + `createBodyLayer()` 代码。原全首者 Boss 已改为「辖界者」（同人坚守者，2026-08-04），模型完全照抄原版 WardenModel 层级、纯近战、中立 AI、狂暴计时——**完整当前状态见 `warden-animation-reuse.md`（新窗口接续必读）**，此文件只保留转换坑。已废弃的历史实现（全首者雷系技能、`QuanshouzheCastingGoal` 已删文件）勿再参考。

## 关键坑（每个都是重启验证过的）

1. **bbmodel 的 group `origin` 是世界坐标（绝对），不是相对父**。PartPose offset = origin − 父.world_origin（不是直接取反）。错用父链累加会导致子骨骼（翅膀等）偏移错乱、左右高度不对称。
2. **原版 LivingEntityRenderer 渲染实体时 `poseStack.scale(-1,-1,1)`**（X/Y 都翻转）。因此 ModelPart 坐标的 **X/Y 与原版相反**：cube 局部 x/y 取反、bone offset x/y 取反（z 不变）、**绕 X/Y 的 rotation 取反（绕 Z 不变）**——否则带旋转的骨骼（翅膀）上下/左右反。
3. **box_uv 逐面 UV ≠ 原版 ModelPart box 布局**：bbmodel 每面 UV 是原版 6 面槽位的**置换/镜像**，必须做**图集重排**（每面像素搬到原版槽位：down(u+d,v) up(u+d+w,v+d) west(u,v+d) north(u+d,v+d) east(u+d+w,v+d) south(u+d+w+d,v+d)）。3 个纹理（warden/wither/dragon）合并成单图集，解决原版单纹理限制。
4. **up/down 槽位 V 方向与 bbmodel 相反**：翼膜等水平大面要垂直翻转，否则"翼纹理上下反"。
5. **Blockbench 官方导出 Java 的负 texOffs**（如 -55/-56）是 Blockbench 特有编码，**无法直接生成匹配纹理**——负 texOffs 的 cube 用 mirror()，纹理在纹理外 clamp。别用 Blockbench Java 的 texOffs，用自己装箱的图集 texOffs + Blockbench 的几何（offset/addBox 参考验证）。
6. **Renderer.scale() 在 LivingEntityRenderer 的 scale(-1,-1,1) 之前执行**：其中的 `translate` 的 y 会被 -1 翻转，**要下移模型必须用正数 translate**（负数反而上移）。
7. **模型基准差 24 像素**：Blockbench 官方导出的根骨骼 offset y 比"直接用 bbmodel origin"高 24（Blockbench 模型基准在 y=24）——这是脚悬空的根因，用 translate 补偿。
8. 碰撞箱按**身体**（不含展开的翅膀）；模型整体用 renderer scale 缩小；测试实体用 `/summon yizxianmod:<id>` + 刷怪蛋。

## 流程
写转换脚本 → 提取 base64 纹理 + 生成图集 + 生成 createBodyLayer → 建 EntityType/Entity/HierarchicalModel/Renderer → 注册（EntityRenderersEvent + EntityAttributeCreationEvent + SpawnEggItem）→ `/summon` 目检（朝向/上下/左右/纹理/脚踩地）。
