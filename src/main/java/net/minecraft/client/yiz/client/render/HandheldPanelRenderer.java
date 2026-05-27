package net.minecraft.client.yiz.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.client.yiz.windowmapper.WindowCaptureManager;
import net.minecraft.client.yiz.windowmapper.WindowTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 摄像机跟随/固定面板渲染器 — Phase 2.2。
 *
 * <h3>模式</h3>
 * <ul>
 *   <li>{@link Mode#FOLLOW}：面板在相机前方 distance 处 billboard，朝向相机。
 *       全局最多 1 个 FOLLOW 面板；新建时若已有，旧的自动转 FIXED。</li>
 *   <li>{@link Mode#FIXED}：面板锁定在世界中的某个位置和朝向（拍快照），双面渲染。</li>
 * </ul>
 *
 * <h3>多面板</h3>
 * 每个面板由整数 id 标识，存储在 {@link #panels} 中。
 * 静默切换不向玩家发送提示消息（命令层负责反馈）。
 */
public final class HandheldPanelRenderer {

    private static final Logger LOG = LoggerFactory.getLogger("HandheldPanelRenderer");

    public enum Mode { FOLLOW, FIXED }

    public static final class PanelInfo {
        public final int id;
        public final String title;
        public final Mode mode;
        public final int width;
        public final int height;
        PanelInfo(int id, String title, Mode mode, int width, int height) {
            this.id = id; this.title = title; this.mode = mode;
            this.width = width; this.height = height;
        }
    }

    /** FIXED 面板的世界几何快照（供射线检测/输入投影使用） */
    public static final class PanelGeometry {
        public final int id;
        public final Vec3 anchor;
        public final Quaternionf rotation;
        public final float halfWidth;
        public final float halfHeight;
        PanelGeometry(int id, Vec3 anchor, Quaternionf rotation, float halfWidth, float halfHeight) {
            this.id = id; this.anchor = anchor; this.rotation = rotation;
            this.halfWidth = halfWidth; this.halfHeight = halfHeight;
        }
    }

    private static final class Panel {
        final int id;
        final WindowCaptureManager.ManagedSession session;
        final WindowTexture texture;
        final String title;
        Mode mode;
        // FIXED 模式快照：世界坐标 + 朝向
        Vec3 anchor;
        Quaternionf rotation;
        boolean debugFirstCaptureLogged = false;

        Panel(int id, WindowCaptureManager.ManagedSession session, WindowTexture texture, String title) {
            this.id = id;
            this.session = session;
            this.texture = texture;
            this.title = title;
            this.mode = Mode.FOLLOW;
        }

        void closeResources() {
            try { session.close(); } catch (Exception ignored) {}
            // GL 纹理必须在渲染线程清理
            RenderSystem.recordRenderCall(texture::close);
        }
    }

    private static final Map<Integer, Panel> panels = new LinkedHashMap<>();
    private static final AtomicInteger nextId = new AtomicInteger(1);

    private HandheldPanelRenderer() {}

    // ════════════════════════════════════════════
    //  查询
    // ════════════════════════════════════════════

    public static synchronized boolean isActive() {
        return !panels.isEmpty();
    }

    public static synchronized int activeCount() {
        return panels.size();
    }

    public static synchronized List<PanelInfo> listPanels() {
        List<PanelInfo> out = new ArrayList<>(panels.size());
        for (Panel p : panels.values()) {
            out.add(new PanelInfo(p.id, p.title, p.mode, p.texture.getWidth(), p.texture.getHeight()));
        }
        return out;
    }

    /** 返回所有 FIXED 面板的世界几何（供 raycast）；若纹理尚未初始化则跳过 */
    public static synchronized List<PanelGeometry> listFixedGeometry() {
        List<PanelGeometry> out = new ArrayList<>();
        for (Panel p : panels.values()) {
            if (p.mode != Mode.FIXED) continue;
            if (p.anchor == null || p.rotation == null) continue;
            if (p.texture.getWidth() <= 1 || p.texture.getHeight() <= 1) continue;
            float[] sz = computePanelSize(p.texture.getWidth(), p.texture.getHeight());
            out.add(new PanelGeometry(p.id, p.anchor, p.rotation, sz[0] * 0.5f, sz[1] * 0.5f));
        }
        return out;
    }

    /** @return [panelW, panelH]，保持源宽高比缩到 UIConfig 框内 */
    private static float[] computePanelSize(int srcW, int srcH) {
        float configW = UIConfig.getHandheldPanelWidth();
        float configH = UIConfig.getHandheldPanelHeight();
        float srcAspect = (float) srcW / srcH;
        if (configW / configH > srcAspect) {
            return new float[]{configH * srcAspect, configH};
        } else {
            return new float[]{configW, configW / srcAspect};
        }
    }

    /** 获取面板的 ManagedSession，用于发送输入事件 */
    public static synchronized WindowCaptureManager.ManagedSession getSession(int id) {
        Panel p = panels.get(id);
        return p == null ? null : p.session;
    }

    // ════════════════════════════════════════════
    //  生命周期
    // ════════════════════════════════════════════

    /**
     * 按窗口标题（子串匹配）打开一个 FOLLOW 模式面板。
     * 若已有 FOLLOW 面板，旧的会自动转 FIXED 锁在当前快照位置。
     * @return 新面板 id，失败返回 -1
     */
    public static synchronized int openByTitle(String titleSubstring) {
        if (!WindowCaptureManager.isAvailable()) {
            LOG.warn("WindowCapture.dll 不可用");
            return -1;
        }
        String[] raw = WindowCaptureManager.enumWindows();
        if (raw == null || raw.length == 0) return -1;

        long matchedHwnd = 0;
        String matchedTitle = null;
        for (String s : raw) {
            int sep = s.indexOf(':');
            if (sep <= 0) continue;
            String title = s.substring(sep + 1);
            if (title.contains(titleSubstring)) {
                matchedHwnd = Long.parseLong(s.substring(0, sep));
                matchedTitle = title;
                break;
            }
        }
        if (matchedHwnd == 0) {
            LOG.info("未找到包含 \"{}\" 的窗口", titleSubstring);
            return -1;
        }
        return openByHwnd(matchedHwnd, matchedTitle);
    }

    public static synchronized int openByHwnd(long hwnd, String titleForDisplay) {
        WindowCaptureManager.ManagedSession s = WindowCaptureManager.createManaged(hwnd);
        if (s == null) {
            LOG.warn("initCapture 失败：hwnd={}", hwnd);
            return -1;
        }
        // 新 FOLLOW 面板：先把现有 FOLLOW 降级为 FIXED
        demoteCurrentFollow();

        int id = nextId.getAndIncrement();
        Panel p = new Panel(id, s, new WindowTexture(), titleForDisplay);
        panels.put(id, p);
        LOG.info("面板 #{} 已打开：{}", id, titleForDisplay);
        return id;
    }

    /** 关闭指定面板 */
    public static synchronized boolean close(int id) {
        Panel p = panels.remove(id);
        if (p == null) return false;
        p.closeResources();
        return true;
    }

    /** 关闭所有面板 */
    public static synchronized int closeAll() {
        int n = panels.size();
        for (Panel p : panels.values()) p.closeResources();
        panels.clear();
        return n;
    }

    /**
     * Ctrl+C 切换：
     * - 若存在 FOLLOW 面板 → 把它拍快照转 FIXED
     * - 否则若存在 FIXED 面板 → 取最近添加的 FIXED 转回 FOLLOW
     * @return 切换后受影响面板的 id，-1 表示没有可切换的
     */
    public static synchronized int toggleFixCurrent() {
        Panel follow = findFollow();
        if (follow != null) {
            captureSnapshot(follow);
            follow.mode = Mode.FIXED;
            return follow.id;
        }
        // 找最近一个 FIXED（LinkedHashMap 末尾遍历）
        Panel last = null;
        for (Panel p : panels.values()) {
            if (p.mode == Mode.FIXED) last = p;
        }
        if (last != null) {
            last.mode = Mode.FOLLOW;
            return last.id;
        }
        return -1;
    }

    private static Panel findFollow() {
        for (Panel p : panels.values()) {
            if (p.mode == Mode.FOLLOW) return p;
        }
        return null;
    }

    private static void demoteCurrentFollow() {
        Panel f = findFollow();
        if (f != null) {
            captureSnapshot(f);
            f.mode = Mode.FIXED;
            LOG.info("面板 #{} 被新 FOLLOW 顶替，已转 FIXED", f.id);
        }
    }

    /** 用当前相机位置+朝向给面板拍快照（FOLLOW → FIXED 用） */
    private static void captureSnapshot(Panel p) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        org.joml.Vector3f look = cam.getLookVector();
        Vec3 lookVec = new Vec3(look.x, look.y, look.z);
        float distance = UIConfig.getHandheldPanelDistance();
        p.anchor = camPos.add(lookVec.scale(distance));
        p.rotation = new Quaternionf(cam.rotation()); // 拷贝当前相机旋转作为快照
    }

    // ════════════════════════════════════════════
    //  渲染
    // ════════════════════════════════════════════

    private static boolean debugRenderEnterLogged = false;

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<Panel> snapshot;
        synchronized (HandheldPanelRenderer.class) {
            if (panels.isEmpty()) return;
            snapshot = new ArrayList<>(panels.values());
        }

        if (!debugRenderEnterLogged) {
            debugRenderEnterLogged = true;
            LOG.info("onRenderLevelStage entered with {} panels", snapshot.size());
        }

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack ps = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                               GlStateManager.DestFactor.ZERO);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        for (Panel p : snapshot) {
            renderOne(p, camera, camPos, ps);
        }

        RenderSystem.disableBlend();
    }

    private static void renderOne(Panel p, Camera camera, Vec3 camPos, PoseStack ps) {
        // 抓帧 + 上传纹理
        ByteBuffer frame = p.session.captureFrame();
        int[] size = p.session.getSize();
        boolean updated = false;
        if (frame != null && size != null && size.length == 2 && size[0] > 0 && size[1] > 0) {
            int bufW = size[0];
            int bufH = size[1];
            int maxPixels = frame.capacity() / 4;
            if (bufW * bufH > maxPixels) {
                bufH = maxPixels / bufW;
                if (bufH <= 0) { bufW = 1; bufH = 1; }
            }
            int needed = bufW * bufH * 4;
            if (needed > 0) {
                ByteBuffer safe = java.nio.ByteBuffer.allocateDirect(needed);
                try {
                    safe.put(frame);
                    safe.flip();
                    frame.rewind();
                    p.texture.update(safe, bufW, bufH);
                    updated = true;
                } catch (Exception e) {
                    LOG.warn("buffer copy failed: {}", e.toString());
                    ps.popPose();
                    return;
                }
            }
        }
        if (!p.debugFirstCaptureLogged) {
            p.debugFirstCaptureLogged = true;
            LOG.info("panel #{} first render: frame={}, size={}, updated={}, texW={}, texH={}",
                p.id,
                frame == null ? "null" : ("buf cap=" + frame.capacity()),
                size == null ? "null" : ("[" + size[0] + "," + size[1] + "]"),
                updated, p.texture.getWidth(), p.texture.getHeight());
        }
        if (p.texture.getWidth() <= 1 || p.texture.getHeight() <= 1) return;

        // 面板尺寸：保持源宽高比，缩到配置框内
        float[] sz = computePanelSize(p.texture.getWidth(), p.texture.getHeight());
        float hw = sz[0] * 0.5f;
        float hh = sz[1] * 0.5f;

        Vec3 center;
        Quaternionf rot;
        if (p.mode == Mode.FOLLOW) {
            org.joml.Vector3f look = camera.getLookVector();
            float distance = UIConfig.getHandheldPanelDistance();
            center = camPos.add(new Vec3(look.x, look.y, look.z).scale(distance));
            rot = camera.rotation();
        } else {
            center = p.anchor != null ? p.anchor : camPos;
            rot = p.rotation != null ? p.rotation : camera.rotation();
        }

        ps.pushPose();
        ps.translate(center.x - camPos.x, center.y - camPos.y, center.z - camPos.z);
        ps.mulPose(rot);

        RenderSystem.setShaderTexture(0, p.texture.getGlTextureId());

        var mat = ps.last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(mat, -hw, -hh, 0).setUv(0f, 1f);
        builder.addVertex(mat,  hw, -hh, 0).setUv(1f, 1f);
        builder.addVertex(mat,  hw,  hh, 0).setUv(1f, 0f);
        builder.addVertex(mat, -hw,  hh, 0).setUv(0f, 0f);
        BufferUploader.drawWithShader(builder.buildOrThrow());

        ps.popPose();
    }
}
