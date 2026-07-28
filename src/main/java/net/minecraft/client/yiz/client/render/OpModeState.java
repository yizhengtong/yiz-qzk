package net.minecraft.client.yiz.client.render;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 世界面板状态机（最终版：光屏贴方块上方，多面板留存）。
 *
 * <p>每个被打开的容器方块对应一条 {@link PanelRecord}，按 {@link BlockPos} 存储：</p>
 * <ul>
 *   <li>光屏锚点 = 方块上方 1 格（世界固定，不跟玩家）</li>
 *   <li>光屏朝向 = 第一次打开时玩家相机的朝向（正对玩家）</li>
 *   <li>持有的 ContainerScreen 实例（ESC 后 vanilla 丢弃 mc.screen，但留存面板继续离屏渲染，内容冻结）</li>
 * </ul>
 * <p>最多 {@link #MAX_PANELS} 个，超过时最旧的清除。本类不涉及相机分离/视角锁定（方案已废弃）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class OpModeState {

    private static final Logger LOG = LoggerFactory.getLogger("OpModeState");

    /** 最多同时保留的面板数。超过时最旧的被清除。 */
    public static final int MAX_PANELS = 3;

    /** 一个容器方块的面板记录。 */
    public static final class PanelRecord {
        public final BlockPos blockPos;
        /** 光屏中心世界坐标（方块上方 1 格）。 */
        public final Vec3 panelAnchor;
        /** 光屏朝向（正对第一次打开时的玩家）。 */
        public final Quaternionf panelRotation;
        /** 接管的 ContainerScreen 实例（持有引用，ESC 后继续离屏渲染，内容冻结）。 */
        public AbstractContainerScreen<?> screen;
        /** 该面板专属的离屏 FBO（由 WorldGuiPanelManager 创建/销毁）。 */
        public com.mojang.blaze3d.pipeline.RenderTarget fbo;

        PanelRecord(BlockPos blockPos, Vec3 panelAnchor, Quaternionf panelRotation, AbstractContainerScreen<?> screen) {
            this.blockPos = blockPos;
            this.panelAnchor = panelAnchor;
            this.panelRotation = panelRotation;
            this.screen = screen;
        }
    }

    /** 按方块坐标存储的面板记录。LinkedHashMap 保序，便于「最旧清除」。 */
    private static final LinkedHashMap<Long, PanelRecord> records = new LinkedHashMap<>();

    private OpModeState() {}

    /** 新增/刷新一条面板记录（按 blockPos 去重，已存在则移到末尾=最近使用）。返回该记录。 */
    public static PanelRecord put(BlockPos pos, Vec3 anchor, Quaternionf rotation, AbstractContainerScreen<?> screen) {
        long key = pos.asLong();
        synchronized (records) {
            records.remove(key);
            while (records.size() >= MAX_PANELS) {
                Long oldest = records.keySet().iterator().next();
                PanelRecord removed = records.remove(oldest);
                destroyFbo(removed);
                LOG.info("面板数超过 {}，清除最旧：{}", MAX_PANELS, removed.blockPos);
            }
            PanelRecord r = new PanelRecord(pos.immutable(), anchor, rotation, screen);
            records.put(key, r);
            return r;
        }
    }

    /** 按 blockPos 查记录（不含则 null）。 */
    public static PanelRecord get(BlockPos pos) {
        synchronized (records) {
            return records.get(pos.asLong());
        }
    }

    /** 列出所有记录快照（供渲染器遍历）。 */
    public static List<PanelRecord> list() {
        synchronized (records) {
            return new ArrayList<>(records.values());
        }
    }

    public static boolean isEmpty() {
        synchronized (records) {
            return records.isEmpty();
        }
    }

    /** 销毁一条记录的 FBO（在渲染线程调）。 */
    private static void destroyFbo(PanelRecord r) {
        if (r == null || r.fbo == null) return;
        com.mojang.blaze3d.systems.RenderSystem.recordRenderCall(() -> {
            try { r.fbo.destroyBuffers(); } catch (Exception ignored) {}
        });
        r.fbo = null;
    }

    /** 清除所有记录（销毁所有 FBO）。 */
    public static void clearAll() {
        synchronized (records) {
            for (PanelRecord r : records.values()) destroyFbo(r);
            records.clear();
        }
    }
}
