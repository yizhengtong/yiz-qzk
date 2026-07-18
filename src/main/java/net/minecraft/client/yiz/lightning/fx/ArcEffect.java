package net.minecraft.client.yiz.lightning.fx;

import net.minecraft.world.phys.Vec3;

/**
 * 线段电弧特效实例（纯数据）。
 *
 * <p>里程碑1：空气定向 A→B 单段。里程碑2 将扩展 SURFACE 类型（沿实体包围盒表面游走），
 * 届时 from/to 改由 {@code PositionSupplier} 动态提供。</p>
 */
public class ArcEffect {

    public Vec3 from;
    public Vec3 to;

    /** 随机种子 — 决定抖动形态，每条电弧独立。 */
    public final int seed;

    /** 电弧带宽（格），renderer 取 width*0.5 作半宽。 */
    public final float width;

    public final float maxLife;
    public float life;

    /** 颜色（线性 0..1）。默认蓝白等离子由 LightningFX 注入。 */
    public final float r, g, b;

    public ArcEffect(Vec3 from, Vec3 to, float life, float width, int seed, float r, float g, float b) {
        this.from = from;
        this.to = to;
        this.maxLife = life;
        this.life = life;
        this.width = width;
        this.seed = seed;
        this.r = r;
        this.g = g;
        this.b = b;
    }
}
