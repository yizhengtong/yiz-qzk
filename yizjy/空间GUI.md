---
name: 空间GUI
description: 把原版贴脸 GUI 离屏渲染到 FBO 再贴成世界光屏的整套踩坑（clear unbind、离屏时机回归、超采样数学、逆投影命中）
metadata:
  type: project
---

世界空间容器 GUI（箱子面板世界化）的核心实现路径与踩坑。代码在 `client/render/WorldGuiPanelManager`、`WorldGuiInputHandler`、`OpModeState`、`HandheldPanelRenderer.renderQuadWithTexture`、`mixin/AbstractContainerScreenBackgroundMixin`。详见工作区 `.context/note.md` 和 yiz1.21.1 会话 `.context/pure-scribbling-gadget.md`。

## 5 个非显而易见的硬坑（每个都让面板变黑/错位/纯蓝/糊过）

1. **`RenderTarget.clear()` 内部最后调 `unbindWrite()`**（绑定回 framebuffer 0）。所以 `fbo.clear()` 之后**必须重新 `fbo.bindWrite(true)`**，否则后续所有绘制画到主屏幕而非 FBO，FBO 永远是 clear 的透明色 → 世界面板纯黑/不显示。这是最隐蔽的坑。

2. **离屏渲染时机决定物品颜色是否正常**。必须在 `ScreenEvent.Render.Pre`（GUI 渲染阶段，光照纹理/atlas/shader 都就绪）里做离屏渲染。**不能在 `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS`**（世界渲染中途，状态被方块/实体渲染占用）——此时离屏渲染物品会变纯蓝（物品 shader 从 unit 2 采样光照纹理失败）。回归就是从 Render.Pre 移到 level stage 引起的。
   - 代价：留存面板（ESC 后 mc.screen=null，Render.Pre 不触发）只能用「冻结的最后一帧 FBO」，无法持续刷新（符合冻结语义）。

3. **离屏 GuiGraphics 必须用独立 bufferSource**：`MultiBufferSource.immediate(new ByteBufferBuilder(786432))`，**不能复用 `RenderBuffers.bufferSource()` 单例**（包路径 `net.minecraft.client.renderer.MultiBufferSource`，非 blaze3d.vertex）。复用会导致 batch 顶点跨 RenderTarget 串味，主世界出现幽灵 GUI 碎片。

4. **FBO 超采样的矩阵顺序**（否则画面错位到右下角只剩左上角）：正交投影用 **FBO 尺寸**（`setOrtho(0, fboW, fboH, 0)`，填满 FBO），ModelView `translation(z→9900)` **后再** `scale(ss, ss, 1)`（只 scale x/y，z 不放大；Matrix4fStack 右乘，顶点先 scale 再 translate，z 不被 xy scale 影响）。FBO 尺寸 = guiScaled × 2。screen 内部坐标仍是 guiScaled，被 scale 放大到 FBO 尺寸。

5. **FBO 纹理贴世界四边形的 UV**：FBO 纹理原点左下（OpenGL），GUI 内容原点左上 → V 要翻转。世界顶边(+hh) 用 vTop、底边(-hh) 用 vBottom。`renderQuadWithTexture(glTexId, center, rot, hw, hh, camPos, ps, uLeft, vTop, uRight, vBottom)`。

## 鼠标命中（逆投影四步法）

玩家右键箱子后 mc.screen 非空 → 视角天然锁（MouseHandler 不 turnPlayer）、鼠标光标显示。鼠标屏幕坐标逆投影成 3D 射线 → 与光屏平面求交 → GUI 像素坐标：
- **射线方向用世界渲染时保存的投影矩阵**（在 RenderLevelStageEvent 里存 `RenderSystem.getProjectionMatrix()`），**不能在点击事件里现读 RenderSystem.getProjectionMatrix()**（此时已被 GUI 正交投影覆盖，算出的射线偏）。鼠标 NDC → `invProj` 反投影近/远裁面 → 方向 → 用 cam.rotation 转世界系。
- 射线-平面求交在**面板局部系**做（z=0 平面，线性，规避透视畸变），命中 `(lx,ly)∈[-hw,hw]×[-hh,hh]` → GUI 像素。
- 注入点：`ScreenEvent.MouseButtonPressed.Pre` / `MouseButtonReleased.Pre` **取消原版**（原版用真实鼠标坐标对世界光屏是错的），改用算出的坐标调 `screen.mouseClicked(guiX, guiY, button)`。
- **拖拽物品跟随鼠标：不用单独 Mixin MouseHandler**（曾误判需待实现）。vanilla `AbstractContainerScreen.render` 画 floating item 和 mouseDragged 完全用传给 render 的 mouseX/mouseY（`renderFloatingItem(g, stack, mouseX-leftPos-8, mouseY-topPos-i2)`）。只要离屏渲染 `renderToOffscreen` 传的不是真实鼠标坐标、而是 `WorldGuiInputHandler.getHoverGuiPos()`（与点击同源的准星命中 GUI 坐标），拖拽物品就贴在准星命中处。之前传 `event.getMouseX()/getMouseY()`（真实鼠标）→「中心贴合、四角偏」（与点击未修时同症）。未命中（准星移出面板）回落真实鼠标坐标即可。
- **命中端 hw/hh 必须与渲染端 drawWorldQuad 同源**（中心贴合、四角偏=尺寸漂移的典型症状）。`WorldGuiInputHandler.raycastPanel` 算面板半宽/半高时，必须调渲染端同一个 `WorldGuiPanelManager.computeGuiPanelSize`（已提为 public），且入参统一用 `r.fbo.width/height`（和 `drawWorldQuad` 一致）。曾经命中端手写硬编码 2.5/1.8 + `r.screen.width/height`，与渲染端的 `computeGuiPanelSize(r.fbo.*)` 宽高比来源不同→命中框形状与实际面板不一致，中心(u=v=0.5)对尺寸不敏感所以准、越靠四角偏差越大。NDC 换算用 `xpos/screenWidth`，guiScaled 在分子分母会被约掉勿绕。求交数学本身无近似误差，误差全在尺寸。

## 其他要点
- **世界 GUI 只接管「有世界面板的容器屏」，不能无差别拦截所有 AbstractContainerScreen**（"按E背包GUI消失/点不动"症状）。`InventoryScreen`（玩家背包）也是 AbstractContainerScreen 子类。曾用 `instanceof AbstractContainerScreen` 就 `setCanceled(true)` + 吞点击 → 按 E 开背包时原版渲染被取消、又没有世界面板接替 → 什么都看不到、点击也被吞。正解：加 `OpModeState.isManagedScreen(screen)`（遍历 records 比 `record.screen == screen`），渲染的 setCanceled、点击的 onMousePressed/Released 拦截、都**只在 isManagedScreen 为 true 时**执行；否则放行原版。判断标准是「当前 screen 实例 == 某条面板记录的 screen」，而非靠 lastClickedPos 间接关联。
- **右键→拍快照的状态机别用 pos 相等去重**（"第1次对、第2次起光屏停在老位置"症状）。`onScreenRenderPre` 里判断是否要 `captureNewPanel`，曾用 `!lastClickedPos.equals(lastProcessedClickPos)`——右键**同一箱子**时 pos 不变，被误判为"同一帧重复"而跳过，光屏不刷新、玩家已转身/移位后命中与视觉全错位。正解：用 `pendingClick` 布尔标志（`RightClickBlock` 置 true、`Render.Pre` 消费后清 false），既能去重同帧多次 Render.Pre，又不漏同箱子重开。
- **`OpModeState.put` 同 key 覆盖前要销毁旧 record 的 FBO**（`records.remove(key)` 返回值非 null 就 `destroyFbo`），否则每次同箱子重开漏一份 GL FBO/纹理。新 PanelRecord 的 fbo 字段是 null，由 `renderToOffscreen` 重建。
- 去全屏背景：Mixin `AbstractContainerScreen.renderBackground`，离屏标志（`WorldGuiPanelManager.isOffscreenRendering()`）为 true 时只调 renderBg（箱子背景图）跳过 renderTransparentBackground（全屏暗色），用 `@SubscribeEvent static` 方法 + `NeoForge.EVENT_BUS.register(Class)` 注册。
- PanelRaycast 已实现桌面面板的射线-平面求交（返回 nx/ny，但 U 有翻转约定，给世界 GUI 用要自算或对齐）。
- `setColorTextureId` 是 protected → 用 public `getColorTextureId()`。`getProjectionMatrix()` 返回引用 → 存拷贝 `new Matrix4f(原)`。
- 路线演进：路1（屏幕空间 pose 变换）因正交投影推远不变小而失败；路2A（相机分离）被废弃（晕眩/视锥剔除）；**最终=光屏世界固定 + 视角锁(原版天然) + 鼠标逆投影命中 + 准星右键全操作**。
- **组合面板**（开发中）：`C2SCombinePanelsPayload` 已建（服务端 CompoundContainer+ChestMenu.sixRows 合并两个箱子），但最终方向确认为**纯视觉拼凑**——两个独立光屏并排展示，点哪边透明切换。箱子+熔炉自动配对的检测逻辑已写（`isChest`/`findNearbyFurnace` 4 格范围）但 RightClickBlock 事件触发不稳定，待把检测移到 `captureNewPanel` 内。

## 准星右键操作光屏（ESC 假关闭 + 自由视角）

玩家**没打开任何 GUI**（mc.screen==null，自由转视角，准星可见）时，准星对准世界里已存在的留存光屏、右键 → 直接操作槽位（拿一半/放一个）。让原本"ESC 后冻结的留存光屏"变得可交互。两套模式共存：右键箱子打开=锁视角+鼠标光标（原样保留）；ESC 假关闭后=自由视角+准星右键（新增）。
- **假关闭是准星操作的前提**：真 ESC 走 `AbstractContainerScreen.onClose`→`player.closeContainer()` 发 `ServerboundContainerClosePacket`，服务端把 `player.containerMenu` 切回背包，之后对留存 screen 调 mouseClicked 发的 click 包被服务端 `handleContainerClick`（containerId 不匹配）**静默忽略**，槽位不变。所以 ESC 必须**假关闭**：Mixin `AbstractContainerScreen.onClose` HEAD，仅当 `OpModeState.isManagedScreen(self)`（世界面板接管的）时 `ci.cancel()` 跳过 closeContainer + `setScreen(null)`（恢复视角+准星，不发 close 包）+ `markFakeClosed`。setScreen(null) 安全：不调 onClose、不发 close 包、removed()→menu.removed() 客户端空操作、grabMouse 恢复视角。非世界面板（背包等）放行真关。
- **右键事件点**：`InputEvent.InteractionKeyMappingTriggered`（在 Minecraft.startUseItem 内、仅 mc.screen==null、可 cancel、在原版右键分支之前）。比 RightClickBlock/Item/Empty 统一（一处拦全部分支）。`isUseItem()`=右键；只处理 `MAIN_HAND`（事件对 MAIN/OFF 各 fire 一次）。准星没命中光屏→不 cancel 放行原版（放方块/吃东西）。
- **准星命中=屏幕中心 NDC(0,0)**：与鼠标命中共用 `raycastWithNdc(mc, ndcX, ndcY)` 核心（重构出，避免两套逆投影漂移），只 NDC 来源不同。`getCrosshairHit()` 返回 public `CrosshairHit{record,guiX,guiY}`。
- **状态机**（OpModeState）：`activePanel`（服务端活跃容器 blockPos）、`fakeClosed`（screen关但容器开）、`switchingTo`（切换中防重入）。准星右键命中 activePanel→直 `screen.mouseClicked(guiX,guiY,1)`（button=1=拿一半/放一个）；命中非活跃光屏→需切换（多光屏切换未实现，当前放行）。
- **多光屏切换**（已实现）：非活跃光屏左右键均可触发切换→`useItemOn`→`pendingSwitchCapture`→关联新 screen 到已有 record→假关闭。`switchingTo` 防重入。
- **边界处理**（已实现）：`ClientTickEvent.Post` 每 tick 检测 `getBlockEntity(pos)==null`→方块被摧毁自动销毁面板+复位状态。`OpModeState.remove(pos)` 单条移除。
- **左键操作**（已实现）：`InteractionKeyMappingTriggered.isAttack()` (button=0=拿全部/放全部)。左右键共用同一套拾取/放下逻辑。
- **左键保护**（已实现）：左键不 cancel 事件（否则 `startAttack()` 调 `keyAttack.release()`→无限循环拿起又放下）。改为 `mc.hitResult=BlockHitResult.miss()` 让后续攻击走空。长按左键用 `MinecraftHoldKeyMixin` 在 `handleKeybinds` HEAD 每帧重置 hitResult 为 MISS。
- **空白区穿透**（已实现）：用 `menu.slots` 容器槽位包围盒（过滤玩家背包槽）替代 `imageWidth×imageHeight`，只保护实际交互区域。槽位外装饰空白穿透。
- **拿起/放下防抖**（已实现）：100ms 时间窗口，拿起和放下后都设 `lastActionMs`，跳过 while(consumeClick) 累积事件。
- **FBO 全面板实时更新**（已实现）：`RenderGuiEvent.Post` 每帧更新所有留存面板的 FBO，不只是活跃面板。
- **容器摧毁同步**（已实现）：`ClientTickEvent.Post` 每 tick 检测 `getBlockEntity` 是否 null→自动 remove 面板+复位状态。
