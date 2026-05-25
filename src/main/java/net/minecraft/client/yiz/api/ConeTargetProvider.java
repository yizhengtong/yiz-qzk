package net.minecraft.client.yiz.api;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 母效果模板：60° 锥自动扫描最近实体。
 * 不会被自动注册，模组按需 register。
 *
 * <pre>{@code
 * TargetFrameManager.register(new ConeTargetProvider(5)); // priority=5 的母效果
 * }</pre>
 */
public class ConeTargetProvider implements TargetFrameProvider {

    private static final double RANGE = 32.0;
    private static final double CONE_DOT = 0.5; // cos(60°)
    private final int priority;

    public ConeTargetProvider(int priority) {
        this.priority = priority;
    }

    @Override
    public Entity getTarget(Player player) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Vec3 eye = player.getEyePosition();
        var look = player.getLookAngle();
        Entity best = null;
        double bestDot = CONE_DOT;
        for (var e : mc.level.getEntities(player, player.getBoundingBox().inflate(RANGE),
                e -> e instanceof LivingEntity && e != player && e.isAlive())) {
            Vec3 to = e.position().subtract(eye);
            double d2 = to.lengthSqr();
            if (d2 > RANGE * RANGE) continue;
            double dot = look.dot(to) / Math.sqrt(d2);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }
        return best;
    }

    @Override public float getCharge() { return 1f; }
    @Override public boolean isReady() { return false; }
    @Override public int getPriority() { return priority; }
}
