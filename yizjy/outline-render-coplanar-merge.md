---
name: outline-render-coplanar-merge
description: 穿墙方块轮廓渲染——"共面合并"才是中间分割棱消失的正解，不是去重、不是渲染层配置
metadata:
  type: feedback
---

做穿墙发光轮廓（EndPortalGlowRenderer，画 128 格内所有 END_PORTAL_FRAME 的外轮廓）时，相邻方块朝同一方向暴露的两个面，中间那条分割棱**应该消失**（合并成大矩形），而不是保留。

## 真正的需求区分（踩了 9 轮才定位）

用户说"接触面被画了/重复边线"，极易被误解成下面三种之一，但它们是**不同的问题、不同的解法**，必须先和用户用图对齐：

- **边去重**：同一条棱被画两遍（坐标相同）→ 用 record/long 的精确 EdgeKey Set 去重即可。
- **接触面整体跳过**：两个方块夹住的接触面（如 A 的 EAST / B 的 WEST）→ `frameSet.contains(pos.relative(face))` 命中则 `continue` 跳过整个面。
- **共面合并**（真正要的）：相邻方块**同向暴露**的两个面，中间那条棱虽然几何上是"外露棱"（该画），但用户希望它消失。这**只能靠"共面内部接缝"判定丢弃**。

前两类做对了，第三类现象依然存在——因为那条分割棱在几何上确实是外轮廓的一部分。

## 共面合并的正确算法

对每个暴露面的每条棱，判定它是不是"两个共面相邻单元的内部接缝"：
1. 棱 e 在方块 (x,y,z) 的 face 面上。face 法向轴 = axis，面内两轴 = a1,a2。
2. e 沿 a1 延伸则侧向轴 = a2，反之侧向轴 = a1。
3. 沿侧向轴（根据 e 在方块的上/下侧决定 +1/-1）迈一步得到邻居 nb。
4. 若 `frameSet.contains(nb)` **且** nb 的同 face 也暴露（`!contains(nb.relative(face))`）**且** e 也是 nb 该 face 的一条边 → e 是共面接缝，**丢弃**。

这个判定对 L 形、T 形、环形都成立（接缝是局部判定，不依赖整体形状）。

## 避坑

### 渲染层坑

- **不要在渲染层找"中间分割棱"答案**：LINES shader 的 normal/layering/output/writeMask 反复改都没用——因为画的本来就是几何正确的边。改 `VIEW_OFFSET_Z_LAYERING→NO_LAYERING` 等都不影响"中间分割棱是否出现"。
- **TRANSLUCENT_TRANSPARENCY + Sodium 透明排序**：大量线顶点时 BSP 树排序极慢（严重卡顿），且排序结果在特定角度会丢弃几何体（"转头就消失"）。`NO_TRANSPARENCY` 免排序但 alpha 不生效。
- **`glLineWidth(3.0)` 在 NVIDIA RTX 5060 是性能天坑**：OpenGL 核心模式废弃宽线，NVIDIA 驱动走软件光栅化回退。直接调 `RenderSystem.lineWidth()` 触发此路径——每帧几千条宽线直接吃掉 GPU 帧预算。通过 MultiBufferSource 的 `LineStateShard` 设置则走 Sodium 管线（不触发慢路径）。
- **Tesselator + BufferUploader.drawWithShader() 会画出来**，但 1px 线条太细、且绕过 Sodium 管线可能引发管线刷新开销。**MultiBufferSource + 自定义穿墙 RenderType** 才是最兼容 Sodium 的方式。

### 扫描层坑

- **`chunk.getSection(int)` 接受数组下标（0-23），不是 section Y（-4-19）**。传入 section Y 会 `ArrayIndexOutOfBounds`。正确做法：`level.getSectionIndexFromSectionY(sectionY)` 先转下标。
- 区块级扫描需跳过 `!level.hasChunk()` 的未加载区块、用 `section.hasOnlyAir()` 跳全空区段。配合 PriorityQueue 边扫边裁（max-heap 保留最近 N 个方块），避免扫后 O(N log N) 全量排序。

### 几何层坑

- **手工位压缩边 key 会碰撞**：`(a<<1)^b` 在 -3..3 小坐标空间就有 10w+ 次碰撞。改用 `record EdgeKey(long lo, long hi)`（每轴 21bit packVert，端点规范化），JVM 自动 hashCode/equals，零碰撞。

## 自检方法论（定位很有效）

怀疑"画了不该画的边"时，加临时自检：遍历 draw 列表，对每条边判定"是否完全被 frameSet 包裹的内部棱"。这次自检显示 `external=160 internal=0`，反而证明算法画的边几何上全对——从而把问题从"算法画错"转向"需求理解错"，最终问出"要合并成大矩形"。**自检结果与现象矛盾时，是需求定义错了，不是自检错了。**

## 文件位置

`yizxian1.21.1/src/main/java/net/minecraft/client/yiz/xian/render/EndPortalGlowRenderer.java`
