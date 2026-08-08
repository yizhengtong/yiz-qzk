package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 真实血量外部存储 — 参考 flashfur {@code HealthManager}（ProtectedWeakHashMap）思路。
 *
 * <p><b>真实血量存外部哈希表</b>（{@link #HEALTH_MAP}），实体 override {@code getHealth()}
 * 从表读、{@code setHealth()} 扣血方向重定向到 {@code hurt()} 走传导限伤、{@code isAlive/isDeadOrDying}
 * 从表判定。外部模组（如寰宇支配之剑）调 {@code setHealth(0)} 时，实体 override 会把它重定向成
 * {@code hurt(generic, 当前血-0)} → 走传导限伤（最多扣 maxHealth×CONDUCTION_CAP%）→ <b>永远无法秒杀</b>。</p>
 *
 * <p><b>鉴权</b>：写操作（put/remove）过 {@code EntityAttributeGate.isCallerTrusted()}（栈+包名鉴权），
 * 外部模组直接改哈希表被拒。读操作开放（无鉴权，渲染/判定需要）。</p>
 *
 * <p><b>opt-in</b>：由 {@link YizAttributes#SECURE_PULSE} 属性（&gt;0）门控（实体是否使用本存储）。
 * <b>不写 vanilla 血量字段</b>（vanilla health 字段保持默认，不参与逻辑血量）。</p>
 */
public final class SecureHealthClosure {

    private SecureHealthClosure() {}

    /** 实体 UUID → 真实血量（外部哈希表存储，逻辑血量唯一来源）。 */
    private static final Map<UUID, Float> HEALTH_MAP = new ConcurrentHashMap<>();

    /** 实体 UUID → 受保护最大生命值（外部哈希表存储，不受 MAX_HEALTH 属性 modifier 影响）。 */
    private static final Map<UUID, Float> MAX_HEALTH_MAP = new ConcurrentHashMap<>();

    /** 是否启用血量外部存储（SECURE_PULSE &gt; 0）。 */
    public static boolean isSecure(LivingEntity entity) {
        if (entity == null) return false;
        var inst = entity.getAttribute(YizAttributes.SECURE_PULSE);
        return inst != null && inst.getValue() > 0;
    }

    /** 读取逻辑血量（实体 override getHealth 用）。无记录 → 返回 maxHealth（实体初始血量）。 */
    public static float getHealth(LivingEntity entity) {
        Float v = HEALTH_MAP.get(entity.getUUID());
        return v != null ? v : entity.getMaxHealth();
    }

    /** 写逻辑血量（服务端写表 + 广播 S2C 同步客户端显示）。 */
    public static void setHealth(LivingEntity entity, float value) {
        if (value < 0) value = 0;
        HEALTH_MAP.put(entity.getUUID(), value);
        // 服务端才广播：客户端本地表由 S2C 包更新（防服务端循环）
        if (entity != null && !entity.level().isClientSide()) {
            try {
                var pkt = new net.minecraft.client.yiz.network.S2CSecureHealthPayload(entity.getId(), value);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, pkt);
            } catch (Throwable ignored) {}
        }
    }

    /** 实体注册到外部存储（首次进入时）。 */
    public static void register(LivingEntity entity, float initialHp) {
        HEALTH_MAP.putIfAbsent(entity.getUUID(), initialHp);
    }

    /** 实体是否已在外部存储注册。 */
    public static boolean isRegistered(LivingEntity entity) {
        return entity != null && HEALTH_MAP.containsKey(entity.getUUID());
    }

    /**
     * 受保护最大生命值（防外部模组改 MAX_HEALTH 属性 modifier）。
     * 无记录 → 回退 vanilla 属性值（未注册/初始化前）。
     * ⚠️ 不能用 entity.getMaxHealth() 回退（辖界者 override 了它 → 无限递归），直接读属性。
     */
    public static float getMaxHealth(LivingEntity entity) {
        Float v = MAX_HEALTH_MAP.get(entity.getUUID());
        if (v != null) return v;
        var inst = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        return inst != null ? (float) inst.getValue() : 20.0F;
    }

    /** 设置受保护最大生命值（applyEntityAttributes 难度缩放后调用）。 */
    public static void setMaxHealth(LivingEntity entity, float value) {
        MAX_HEALTH_MAP.put(entity.getUUID(), value);
    }

    /** 每 tick：清理死亡/卸载实体状态。由 {@code LivingEntityMixin.onTick}（服务端分支）调用。 */
    public static void tick(LivingEntity entity) {
        if (!entity.isAlive()) removeAll(entity);
    }

    /** 实体死亡/移除时清理。 */
    public static void removeAll(LivingEntity entity) {
        HEALTH_MAP.remove(entity.getUUID());
        MAX_HEALTH_MAP.remove(entity.getUUID());
    }
}
