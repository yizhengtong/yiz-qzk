package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 世界空间容器 GUI（最终版：光屏贴方块上方，多面板留存，准星命中）。
 *
 * <h3>工作流</h3>
 * <ol>
 *   <li>右键箱子 → {@link PlayerInteractEvent.RightClickBlock} 记录方块 pos；原版正常 setScreen（拿 screen 实例 + Menu 同步）。</li>
 *   <li>{@link ScreenEvent.Render.Pre}：取消原版贴脸渲染；把 screen 实例存进 {@link OpModeState}（光屏锚点=方块上方1格）。</li>
 *   <li>{@link RenderLevelStageEvent#AFTER_TRANSLUCENT_BLOCKS}：遍历所有面板记录，
 *       每个离屏渲染到自己的 FBO（screen.render），再用 FBO 纹理画世界四边形。</li>
 *   <li>ESC 关 GUI → screen 实例仍由 OpModeState 持有，面板留存（内容冻结）。</li>
 * </ol>
 *
 * <h3>命中检测</h3>
 * 玩家用准星指向光屏、左键点击 → {@link PanelRaycast} 射线-平面求交得 UV → 换算 GUI 像素 → 调
 * {@code mc.screen.mouseClicked}（仅当前 mc.screen 可操作，其他留存面板冻结显示）。
 *
 * <h3>关键约束</h3>
 * 离屏 GuiGraphics 必须用独立 bufferSource（非 RenderBuffers 单例）；
 * fbo.clear() 内部会 unbindWrite，clear 后必须重新 bindWrite。
 */
@OnlyIn(Dist.CLIENT)
public final class WorldGuiPanelManager {

    private static final Logger LOG = LoggerFactory.getLogger("WorldGuiPanelManager");

    /** 世界化总开关。 */
    private static volatile boolean enabled = true;

    /** 最近一次右键的方块 pos（供 ScreenEvent.Render.Pre 关联 screen 与方块）。 */
    private static volatile BlockPos lastClickedPos = null;
    /** 已处理过的右键 pos（区分「新右键=面前重新生成」vs「同次重复」）。 */
    private static volatile BlockPos lastProcessedClickPos = null;

    /** GUI 面板尺寸（方块），保持源宽高比缩到此框内。 */
    private static final float GUI_PANEL_WIDTH = 2.5f;
    private static final float GUI_PANEL_HEIGHT = 1.8f;
    /** 光屏在玩家前方的距离（格），右键时拍快照。 */
    private static final float PANEL_DISTANCE_IN_FRONT = 0.5f;
    /** FBO 超采样倍数（提升文字清晰度；FBO 尺寸 = guiScaled × 此值）。 */
    private static final int FBO_SUPERSAMPLE = 2;

    /** 离屏渲染进行中标志（供 Mixin 拦截 renderBackground 全屏背景用）。 */
    private static volatile boolean offscreenRendering = false;
    public static boolean isOffscreenRendering() { return offscreenRendering; }

    /** 保存世界渲染的投影矩阵（透视），供鼠标逆投影命中用——比读 RenderSystem 全局值准（点击时全局值可能已变）。 */
    private static volatile org.joml.Matrix4f worldProjection = new org.joml.Matrix4f();
    public static org.joml.Matrix4f getWorldProjection() { return worldProjection; }

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }

    /** 独立 bufferSource（避免污染 RenderBuffers 单例）。 */
    private static ByteBufferBuilder sharedBuilder = null;
    private static MultiBufferSource.BufferSource bufferSource = null;

    private WorldGuiPanelManager() {}

    private static MultiBufferSource.BufferSource bufferSource() {
        if (bufferSource == null) {
            sharedBuilder = new ByteBufferBuilder(786432);
            bufferSource = MultiBufferSource.immediate(sharedBuilder);
        }
        return bufferSource;
    }

    /** @return [panelW, panelH]，保持源宽高比缩到 GUI 尺寸框内。 */
    private static float[] computeGuiPanelSize(float srcW, float srcH) {
        float srcAspect = srcW / srcH;
        if (GUI_PANEL_WIDTH / GUI_PANEL_HEIGHT > srcAspect) {
            return new float[]{GUI_PANEL_HEIGHT * srcAspect, GUI_PANEL_HEIGHT};
        } else {
            return new float[]{GUI_PANEL_WIDTH, GUI_PANEL_WIDTH / srcAspect};
        }
    }

    // ════════════════════════════════════════════════════
    //  事件：记录右键方块
    // ════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!enabled) return;
        if (event.getEntity().level().isClientSide) {
            // 记录客户端右键的方块 pos，供下次 ScreenEvent.Render.Pre 关联
            lastClickedPos = event.getPos().immutable();
        }
    }

    // ════════════════════════════════════════════════════
    //  事件：取消贴脸渲染 + 接管 screen
    // ════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (!enabled) return;
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        @SuppressWarnings("unchecked")
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;

        // 右键箱子 → 每次都在玩家面前重新生成光屏（即使同箱子已有旧光屏，也用新视角拍快照覆盖）。
        // 用 lastProcessedClickPos 区分「新右键」vs「同一帧重复触发 Render.Pre」。
        if (lastClickedPos != null && !lastClickedPos.equals(lastProcessedClickPos)) {
            captureNewPanel(lastClickedPos, container);
            lastProcessedClickPos = lastClickedPos;
        }

        // 在 GUI 渲染阶段（ScreenEvent.Render.Pre）对当前 screen 离屏渲染——此时机全局状态干净
        // （光照纹理/atlas 已就绪），物品颜色正常。留存面板（ESC 后无 mc.screen）不进这里，用冻结的旧 FBO。
        OpModeState.PanelRecord current = (lastClickedPos != null) ? OpModeState.get(lastClickedPos) : null;
        if (current != null && current.screen == container) {
            current.screen = container; // 刷新实例引用（同箱子重开时 screen 是新实例）
            renderToOffscreen(current, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }

        // 取消原版贴脸渲染（玩家看世界光屏，不看贴脸 GUI）
        event.setCanceled(true);
    }

    /** 新建一条面板记录：光屏锚点=玩家相机前方0.5格，朝向=当前相机（正对玩家）。 */
    private static void captureNewPanel(BlockPos pos, AbstractContainerScreen<?> screen) {
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        org.joml.Vector3f look = cam.getLookVector();
        Vec3 lookVec = new Vec3(look.x, look.y, look.z);
        // 锚点 = 玩家眼睛前方 0.5 格
        Vec3 anchor = camPos.add(lookVec.scale(PANEL_DISTANCE_IN_FRONT));
        // 朝向 = 当前相机（让光屏正对玩家）
        Quaternionf rotation = new Quaternionf(cam.rotation());
        OpModeState.put(pos, anchor, rotation, screen);
        LOG.info("新建世界面板 @ {} 前方{}格，anchor=({},{},{})", pos, PANEL_DISTANCE_IN_FRONT, anchor.x, anchor.y, anchor.z);
    }

    // ════════════════════════════════════════════════════
    //  事件：离屏渲染所有面板 + 画世界四边形
    // ════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!enabled) return;
        List<OpModeState.PanelRecord> records = OpModeState.list();
        if (records.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();

        // 保存世界渲染的投影矩阵，供鼠标逆投影命中用
        worldProjection = new org.joml.Matrix4f(RenderSystem.getProjectionMatrix());

        // 只画世界四边形（离屏渲染已在 ScreenEvent.Render.Pre 完成，用各自的 FBO 纹理）。
        // 当前面板用刚更新的 FBO；留存面板（ESC 后）用冻结的旧 FBO。
        for (OpModeState.PanelRecord r : records) {
            if (r.fbo == null) continue;
            drawWorldQuad(r, camPos, ps);
        }
    }

    /** 把 screen 离屏渲染到 record.fbo（无则按窗口尺寸创建）。在 ScreenEvent.Render.Pre（GUI 时机）调用。 */
    private static void renderToOffscreen(OpModeState.PanelRecord r, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreen<?> screen = r.screen;
        int guiW = mc.getWindow().getGuiScaledWidth();
        int guiH = mc.getWindow().getGuiScaledHeight();
        int fboW = guiW * FBO_SUPERSAMPLE;
        int fboH = guiH * FBO_SUPERSAMPLE;

        // 创建/调整 FBO（超采样分辨率）
        if (r.fbo == null) {
            r.fbo = new TextureTarget(fboW, fboH, true, Minecraft.ON_OSX);
        } else if (r.fbo.width != fboW || r.fbo.height != fboH) {
            r.fbo.resize(fboW, fboH, Minecraft.ON_OSX);
        }
        RenderTarget fbo = r.fbo;

        // save 全局状态
        Matrix4f savedProj = new Matrix4f(RenderSystem.getProjectionMatrix());
        var savedSorting = RenderSystem.getVertexSorting();
        Matrix4fStack mvStack = RenderSystem.getModelViewStack();
        mvStack.pushMatrix();

        offscreenRendering = true;
        try {
            // fbo.clear() 内部会 unbindWrite，clear 后必须重新 bindWrite
            fbo.clear(Minecraft.ON_OSX);
            fbo.bindWrite(true);

            // 超采样：正交投影用 FBO 尺寸（fboW×fboH，填满整个 FBO），
            // ModelView 只 scale x/y（把 screen 的 guiScaled 坐标放大到 FBO 尺寸），z 保持 translation 不被放大。
            Matrix4f ortho = new Matrix4f().setOrtho(0.0f, fboW, fboH, 0.0f, 1000.0f,
                    net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
            RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z);
            // 顺序（Matrix4fStack 右乘，顶点先经后写的变换）：
            // translation（z→9900）后 scale(xy) → 顶点先 scale xy 再 translation，z 不被 xy scale 放大。
            mvStack.identity();
            mvStack.translation(0.0f, 0.0f, 10000.0f - net.neoforged.neoforge.client.ClientHooks.getGuiFarPlane());
            mvStack.scale((float) FBO_SUPERSAMPLE, (float) FBO_SUPERSAMPLE, 1.0f);
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            // 绑定光照纹理到 unit 2（物品渲染的 shader 从 unit 2 采样光照）；
            // GUI 阶段可能已 turnOffLightLayer 解绑，离屏渲染物品前必须重新绑定，否则物品会渲染成纯色（纯蓝）。
            mc.gameRenderer.lightTexture().turnOnLightLayer();

            GuiGraphics g = new GuiGraphics(mc, bufferSource());
            try {
                screen.render(g, mouseX, mouseY, partialTick);
                g.flush();
            } catch (Throwable t) {
                LOG.error("离屏渲染失败 @ {}", r.blockPos, t);
            }
        } finally {
            offscreenRendering = false;
            RenderSystem.disableScissor();
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProj, savedSorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    /** 用 record.fbo 纹理画世界四边形（整个 FBO 投影，V 翻转）。 */
    private static void drawWorldQuad(OpModeState.PanelRecord r, Vec3 camPos, PoseStack ps) {
        if (r.fbo == null) return;
        float[] sz = computeGuiPanelSize(r.fbo.width, r.fbo.height);
        float hw = sz[0] * 0.5f;
        float hh = sz[1] * 0.5f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        HandheldPanelRenderer.renderQuadWithTexture(r.fbo.getColorTextureId(), r.panelAnchor, r.panelRotation,
                hw, hh, camPos, ps, 0f, 1f, 1f, 0f);
        RenderSystem.disableBlend();
    }
}
