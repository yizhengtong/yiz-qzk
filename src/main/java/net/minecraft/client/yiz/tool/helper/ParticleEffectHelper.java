package net.minecraft.client.yiz.tool.helper;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 粒子效果辅助工具
 * 提供通用的粒子效果创建方法。
 */
public final class ParticleEffectHelper {

    private ParticleEffectHelper() {}

    /**
     * 在位置周围生成环形粒子。
     */
    public static void spawnEffectRing(Level level, Vec3 position, int color, int count) {
        if (!(level instanceof ClientLevel clientLevel)) return;

        double radius = 1.0;
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            double x = position.x + radius * Math.cos(angle);
            double z = position.z + radius * Math.sin(angle);
            clientLevel.addParticle(
                ParticleTypes.END_ROD,
                x, position.y + 0.5, z,
                0, 0.05, 0
            );
        }
    }

    /**
     * 在两个位置之间生成轨迹粒子。
     */
    public static void spawnTrail(Level level, Vec3 from, Vec3 to, int density) {
        if (!(level instanceof ClientLevel clientLevel)) return;

        Vec3 step = to.subtract(from).scale(1.0 / density);
        Vec3 current = from;

        for (int i = 0; i < density; i++) {
            clientLevel.addParticle(
                ParticleTypes.FLAME,
                current.x, current.y, current.z,
                0, 0, 0
            );
            current = current.add(step);
        }
    }

    /**
     * 在位置生成爆发粒子。
     */
    public static void spawnBurst(Level level, Vec3 position, int count, double speed) {
        if (!(level instanceof ClientLevel clientLevel)) return;

        for (int i = 0; i < count; i++) {
            double vx = (level.random.nextDouble() - 0.5) * speed;
            double vy = (level.random.nextDouble() - 0.5) * speed;
            double vz = (level.random.nextDouble() - 0.5) * speed;

            clientLevel.addParticle(
                ParticleTypes.CRIT,
                position.x, position.y + 0.5, position.z,
                vx, vy, vz
            );
        }
    }

    /**
     * 在实体位置生成伤害数字粒子。
     */
    public static void spawnDamageNumber(Level level, Vec3 position, double damage) {
        if (!(level instanceof ClientLevel clientLevel)) return;

        for (int i = 0; i < Math.min((int) damage / 2 + 1, 10); i++) {
            clientLevel.addParticle(
                ParticleTypes.DAMAGE_INDICATOR,
                position.x + (level.random.nextDouble() - 0.5) * 0.5,
                position.y + 1.0 + level.random.nextDouble() * 0.5,
                position.z + (level.random.nextDouble() - 0.5) * 0.5,
                0, 0.1, 0
            );
        }
    }

    /**
     * 生成治疗粒子。
     */
    public static void spawnHealParticles(Level level, Vec3 position, int count) {
        if (!(level instanceof ClientLevel clientLevel)) return;

        for (int i = 0; i < count; i++) {
            clientLevel.addParticle(
                ParticleTypes.HEART,
                position.x + (level.random.nextDouble() - 0.5) * 0.5,
                position.y + level.random.nextDouble(),
                position.z + (level.random.nextDouble() - 0.5) * 0.5,
                0, 0.1, 0
            );
        }
    }
}
