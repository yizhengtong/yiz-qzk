package net.minecraft.client.yiz.api;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 伤害来源判断 API
 * <p>
 * 提供便捷方法用于判断伤害来源的类型和归属。
 * 下游模组可通过此 API 区分近战/远程攻击、判断攻击者是否为玩家等。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 判断是否为玩家的远程攻击
 * if (DamageSourceAPI.isPlayerAttacker(source) && DamageSourceAPI.isIndirectAttack(source)) {
 *     // 远程攻击增幅处理...
 * }
 * }</pre>
 */
public final class DamageSourceAPI {

    private DamageSourceAPI() {}

    /**
     * 判断伤害来源是否由玩家造成（直接或间接）
     */
    public static boolean isPlayerAttacker(DamageSource source) {
        return source.getEntity() instanceof Player;
    }

    /**
     * 获取造成伤害的实体（攻击者）。
     * 对于间接伤害（如弓箭），这是射出箭的实体而非箭本身。
     */
    @Nullable
    public static Entity getAttacker(DamageSource source) {
        return source.getEntity();
    }

    /**
     * 获取直接造成伤害的实体（如箭矢、火球、直接攻击者等）。
     * 近战时与 {@link #getAttacker} 返回同一实体。
     */
    @Nullable
    public static Entity getDirectAttacker(DamageSource source) {
        return source.getDirectEntity();
    }

    /**
     * 判断是否为近战攻击。
     * <p>近战判定条件：直接实体与攻击者是同一实体（如剑/拳直接击中）。</p>
     */
    public static boolean isMeleeAttack(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct != null && direct == source.getEntity();
    }

    /**
     * 判断是否为间接攻击（远程/投射物/魔法等）。
     * <p>间接攻击：直接实体与攻击者不是同一实体（如箭由弓射出、火球由发射器发射）。</p>
     */
    public static boolean isIndirectAttack(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct == null || direct != source.getEntity();
    }
}
