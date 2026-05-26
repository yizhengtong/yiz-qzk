package net.minecraft.client.yiz.api;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 母模板2 — 60° 锥自动扫描，使用第二套角片纹理。
 * 不会被自动注册，模组按需 register。
 */
// 大白话: 母模板2方法
public class ConeTargetProvider2 implements TargetFrameProvider {

    private static final double RANGE = 32.0;
    private static final double CONE_DOT = 0.5;
    private final int priority;

    public static final ResourceLocation[] CORNER_TEX = {
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_tr.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_tl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_bl.png"),
        ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/gui/lock2_br.png"),
    };

    public ConeTargetProvider2(int priority) {
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
    @Override public ResourceLocation[] getCornerTextures() { return CORNER_TEX; }
}
