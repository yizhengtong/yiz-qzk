package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 健康值字段写入守卫 — 对受管理实体的真实血量字段做「回血方向」写入拦截（零织入反射钩子）。
 *
 * <p>本质是把 {@link VitalitySeveranceHandler#enforceFieldTick} 的字段级回弹抵消<b>泛化</b>：
 * 去掉「仅绝妄生机目标」限制，改为所有<b>被管理</b>的自研血量实体——外部模组直接反射写真实血量字段
 * （绕过 hurt/setHealth/heal）给实体回血时，每 tick 检测并写回基线抵消。</p>
 *
 * <p><b>为什么是反射钩子而非 ASM PUTFIELD 织入</b>：全字段 PUTFIELD 织入对 mod 类有 VerifyError 风险
 * （Agent 对 mod 类 COMPUTE_FRAMES 已失败回退 COMPUTE_MAXS，DUP2 栈操作在注入点附近有控制流即可能失败）。
 * 反射钩子零织入零风险，且实体在 {@code onTick} 服务端分支每 10 tick 检查一次足够及时。</p>
 *
 * <p><b>管理方式</b>：自研血量实体在 {@code applyEntityAttributes()}（第一 tick）调用 {@link #register} 登记，
 * 即纳入本守卫；不需要绝妄生机配置即可生效（与 enforceFieldTick 的区别）。</p>
 *
 * <p>注意：<b>不拦截向下（扣血方向）写入</b>——本模组扣血（{@code EntityHealthLocator.applyPersistentDamage}）
 * 是合法写入，且写后调 {@link #updateBaseline} 更新基线；只拦「回血方向」的外部篡改。</p>
 */
public final class HealthWriteGuard {

    private HealthWriteGuard() {}

    /** 受管理实体 UUID → 真实血量字段基线（回血方向检测基准）。 */
    private static final Map<UUID, Double> FIELD_BASELINE = new ConcurrentHashMap<>();

    /**
     * 登记受管理实体：纳入写入守卫（在 applyEntityAttributes 第一 tick 调用）。
     * 基线与实体已定位字段对齐（未定位则无守卫，等定位后再生效）。
     */
    public static void register(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        Double cur = EntityHealthLocator.readLocated(entity);
        if (cur != null) {
            FIELD_BASELINE.put(entity.getUUID(), cur);
        }
    }

    /**
     * 注销实体（死亡/卸载清理）。由 {@code LivingEntityMixin.onDie} 调用。
     */
    public static void remove(LivingEntity entity) {
        FIELD_BASELINE.remove(entity.getUUID());
    }

    /**
     * 每 tick 强制：检测受管理实体真实血量字段是否出现「回血方向」变化（外部篡改），写回基线抵消。
     * <p>回血方向判定（与 {@code enforceFieldTick} 一致）：
     * inverse 型（totalDamageTaken，血量 = maxHealth − 字段）字段<b>减少</b>、正向型（血量存储）字段<b>增加</b>
     * = 回血 → 写回基线。扣血方向（inverse 增加 / 正向减少）不拦。</p>
     * <p>由 {@code LivingEntityMixin.yizmodqzk$onTick()} 服务端分支每 ~10 tick 调用。</p>
     */
    public static void enforce(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        Double prev = FIELD_BASELINE.get(uuid);
        if (prev == null) {
            // 未登记但在定位缓存中（如 EntityHealthLocator 已定位且本守卫未被 register）→ 忽略，需显式 register
            return;
        }
        var slot = EntityHealthLocator.locate(entity);
        if (slot == null) {
            // 实体不再有定位槽（类型被移除/缓存清了）→ 清登记
            FIELD_BASELINE.remove(uuid);
            return;
        }
        Double cur = EntityHealthLocator.readLocated(entity);
        if (cur == null) return;

        // 回血 = 字段向「血量增加」方向变化：inverse 型减少、正向型增加；超容差才干预
        boolean healed = slot.inverse() ? (cur < prev - 0.01) : (cur > prev + 0.01);
        if (healed) {
            // 外部写回血 → 写回基线抵消
            EntityHealthLocator.writeLocated(entity, prev);
            cur = prev;
        }
        FIELD_BASELINE.put(uuid, cur);
    }

    /**
     * 更新基线（本模组主动扣血 {@code EntityHealthLocator.applyPersistentDamage} 成功后调用，
     * 防止把这次扣血当成「回弹」抵消回去）。
     */
    public static void updateBaseline(LivingEntity entity) {
        if (!FIELD_BASELINE.containsKey(entity.getUUID())) return;
        Double cur = EntityHealthLocator.readLocated(entity);
        if (cur != null) FIELD_BASELINE.put(entity.getUUID(), cur);
    }
}
