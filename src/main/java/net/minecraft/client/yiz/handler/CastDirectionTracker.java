package net.minecraft.client.yiz.handler;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 施法方向追踪器 — 客户端按键时捕获移动方向，服务端 onCast 读取。
 * 存储 double 值而非 Vec3，避免 cross-project 类引用问题。
 */
public final class CastDirectionTracker {

    private static double dirX, dirZ;
    private static boolean hasDir;

    private CastDirectionTracker() {}

    /** 客户端侧：捕获玩家当前键盘输入方向（无输入时为 0,0，由技能自行 fallback）。 */
    public static void capture(Player player) {
        float f = player.zza;  // W/S
        float s = player.xxa;  // A/D
        if (f == 0 && s == 0) {
            hasDir = false;
            return;
        }
        float yRad = player.getYRot() * ((float) Math.PI / 180f);
        float sin = (float) Math.sin(yRad);
        float cos = (float) Math.cos(yRad);
        Vec3 dir = new Vec3(s * cos - f * sin, 0, f * cos + s * sin).normalize();
        dirX = dir.x;
        dirZ = dir.z;
        hasDir = true;
    }

    /** 服务端侧：读取方向（Vec3，y=0），无捕获数据时返回 null。 */
    public static Vec3 consume() {
        if (!hasDir) return null;
        hasDir = false;
        return new Vec3(dirX, 0, dirZ);
    }
}
