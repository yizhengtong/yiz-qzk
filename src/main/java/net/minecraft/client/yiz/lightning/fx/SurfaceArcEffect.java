package net.minecraft.client.yiz.lightning.fx;

import net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * 表面游离电弧 — 在 AABB 表面循环生成短寿命小电弧（游离态闪电群）。
 *
 * <p>两种 AABB 来源：
 * <ul>
 *   <li>Entity 模式：跟随实体包围盒（自适应大小 + 跟随移动，目标失效自动移除）</li>
 *   <li>球模式：AABB 实时 = center ± radius（给飞行/悬浮球加表面游离电弧）</li>
 * </ul>
 * 每条小电弧两端在同一表面、跨度为该面尺寸的 40~80%，独立短寿命（0.15~0.3s），不断生灭。</p>
 */
public final class SurfaceArcEffect {

    /** Entity 模式时的目标（球模式为 null）。 */
    public final Entity target;
    public final float maxLife;
    public float life;
    public final float halfWidth;
    public final float r, g, b;
    public final int seed;

    public final CopyOnWriteArrayList<MiniArc> arcs = new CopyOnWriteArrayList<>();

    private final Supplier<AABB> boxSupplier;
    private final Random rnd;
    private static final int SPAWN_PER_TICK = 2;
    private static final float MINI_LIFE_BASE = 0.15f;
    private static final float MINI_LIFE_VAR = 0.20f;

    /** Entity 模式：AABB = target.getBoundingBox()。 */
    public SurfaceArcEffect(Entity target, float life, float halfWidth, float r, float g, float b, int seed) {
        this.target = target;
        this.boxSupplier = target::getBoundingBox;
        this.maxLife = life; this.life = life;
        this.halfWidth = halfWidth; this.r = r; this.g = g; this.b = b; this.seed = seed;
        this.rnd = new Random(seed);
    }

    /** 球模式：AABB 实时 = center ± radius（跟随球的 PositionSupplier）。 */
    public SurfaceArcEffect(PositionSupplier center, float radius, float life, float halfWidth, float r, float g, float b, int seed) {
        this.target = null;
        final float rad = radius;
        this.boxSupplier = () -> {
            Vec3 c = center.get(0f);
            return new AABB(c.x - rad, c.y - rad, c.z - rad, c.x + rad, c.y + rad, c.z + rad);
        };
        this.maxLife = life; this.life = life;
        this.halfWidth = halfWidth; this.r = r; this.g = g; this.b = b; this.seed = seed;
        this.rnd = new Random(seed);
    }

    public boolean tick() {
        if (target != null && (!target.isAlive() || target.isRemoved())) return false;
        AABB box = boxSupplier.get();
        for (int i = 0; i < SPAWN_PER_TICK; i++) arcs.add(spawnMini(box));
        arcs.removeIf(m -> {
            m.life -= 0.05f;
            return m.life <= 0f;
        });
        life -= 0.05f;
        return life > 0f;
    }

    private MiniArc spawnMini(AABB box) {
        int face = rnd.nextInt(6);
        double ua = rnd.nextDouble(), va = rnd.nextDouble();
        double ang = rnd.nextDouble() * 6.2831853;
        double span = 0.40 + rnd.nextDouble() * 0.40;
        double ub = Math.clamp(ua + Math.cos(ang) * span, 0, 1);
        double vb = Math.clamp(va + Math.sin(ang) * span, 0, 1);
        Vec3 from = faceToWorld(box, face, ua, va);
        Vec3 to = faceToWorld(box, face, ub, vb);
        float ml = MINI_LIFE_BASE + rnd.nextFloat() * MINI_LIFE_VAR;
        return new MiniArc(from, to, rnd.nextInt(), ml);
    }

    private static Vec3 faceToWorld(AABB box, int face, double u, double v) {
        double mnX = box.minX, mxX = box.maxX, mnY = box.minY, mxY = box.maxY, mnZ = box.minZ, mxZ = box.maxZ;
        double x, y, z;
        switch (face) {
            case 0:  x = mnX + u * (mxX - mnX); y = mnY;               z = mnZ + v * (mxZ - mnZ); break;
            case 1:  x = mnX + u * (mxX - mnX); y = mxY;               z = mnZ + v * (mxZ - mnZ); break;
            case 2:  x = mnX;                  y = mnY + u * (mxY - mnY); z = mnZ + v * (mxZ - mnZ); break;
            case 3:  x = mxX;                  y = mnY + u * (mxY - mnY); z = mnZ + v * (mxZ - mnZ); break;
            case 4:  x = mnX + u * (mxX - mnX); y = mnY + v * (mxY - mnY); z = mnZ;               break;
            default: x = mnX + u * (mxX - mnX); y = mnY + v * (mxY - mnY); z = mxZ;               break;
        }
        return new Vec3(x, y, z);
    }

    /** 一条表面游离小电弧（短 A→B，独立寿命）。 */
    public static final class MiniArc {
        public final Vec3 from, to;
        public final int seed;
        public final float maxLife;
        public float life;

        MiniArc(Vec3 from, Vec3 to, int seed, float life) {
            this.from = from;
            this.to = to;
            this.seed = seed;
            this.maxLife = life;
            this.life = life;
        }
    }
}
