package net.minecraft.client.yiz.client.render;

import net.minecraft.client.yiz.client.render.HandheldPanelRenderer.PanelGeometry;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * 射线-FIXED 面板矩形相交检测。
 *
 * <p>面板局部坐标系：渲染时 quad 顶点是 (-hw,-hh,0) 到 (hw,hh,0)，
 * 平面方程为局部 z=0。把世界射线变换到面板局部系后求与 z=0 平面交点即可。</p>
 *
 * <p>归一化输出 (nx, ny) 适配 {@code WindowCaptureManager.sendMouseMove}：
 * nx=0 左、nx=1 右、ny=0 顶部、ny=1 底部。</p>
 */
public final class PanelRaycast {

    public static final class Hit {
        public final int panelId;
        public final double distance;
        public final double nx;
        public final double ny;
        Hit(int panelId, double distance, double nx, double ny) {
            this.panelId = panelId; this.distance = distance;
            this.nx = nx; this.ny = ny;
        }
    }

    private PanelRaycast() {}

    /** 找到最近被命中的 FIXED 面板，没命中返回 null */
    public static Hit firstHit(Vec3 eye, Vec3 lookDir, List<PanelGeometry> panels) {
        Hit best = null;
        for (PanelGeometry g : panels) {
            Hit h = raycast(eye, lookDir, g);
            if (h == null) continue;
            if (best == null || h.distance < best.distance) best = h;
        }
        return best;
    }

    /** 单面板射线相交。命中返回 Hit；未命中或射线背离/落在矩形外返回 null */
    public static Hit raycast(Vec3 eye, Vec3 lookDir, PanelGeometry g) {
        // 1. 把 eye 和 lookDir 变换到面板局部坐标系
        //    局部坐标 = rotation^-1 * (worldPoint - anchor)
        Quaternionf invRot = new Quaternionf(g.rotation).conjugate(); // 单位四元数的逆 = 共轭

        Vector3f relEye = new Vector3f(
                (float) (eye.x - g.anchor.x),
                (float) (eye.y - g.anchor.y),
                (float) (eye.z - g.anchor.z));
        invRot.transform(relEye);

        Vector3f localDir = new Vector3f((float) lookDir.x, (float) lookDir.y, (float) lookDir.z);
        invRot.transform(localDir);

        // 2. 射线与 z=0 平面求交：localEye.z + t * localDir.z = 0
        if (Math.abs(localDir.z) < 1e-6f) return null; // 平行平面
        float t = -relEye.z / localDir.z;
        if (t <= 0) return null; // 在射线背面

        float lx = relEye.x + t * localDir.x;
        float ly = relEye.y + t * localDir.y;

        if (lx < -g.halfWidth || lx > g.halfWidth) return null;
        if (ly < -g.halfHeight || ly > g.halfHeight) return null;

        // 3. 归一化：UV 系 ny=0 是顶部（局部 +Y 是上 → ny = 1 - (ly + hh) / (2*hh)）
        //    渲染时 U 做了水平翻转（修正镜像），nx 也要相应翻转
        double nx = 1.0 - (lx + g.halfWidth) / (2.0 * g.halfWidth);
        double ny = 1.0 - (ly + g.halfHeight) / (2.0 * g.halfHeight);

        return new Hit(g.id, t, nx, ny);
    }
}
