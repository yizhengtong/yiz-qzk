package net.minecraft.client.yiz.tool.health;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 禁疗效果处理器
 * <p>
 * 在 {@link LivingHealEvent} 层面拦截治疗，强制执行 {@link VitalitySeveranceConfig} 的禁疗配置。
 * 同时管理攻击者主动施加的临时禁疗计时器。
 * </p>
 *
 * <p>本处理器是第二道防线，与 {@link EntityASMUtil#modifyHealth} 和
 * {@link HealthApplier} 中的禁疗检查配合，确保所有治疗途径都被拦截。</p>
 */
public final class VitalitySeveranceHandler {

    private static boolean registered = false;

    private static final Map<UUID, BanEntry> BANS = new ConcurrentHashMap<>();

    private VitalitySeveranceHandler() {}

    /**
     * 注册到 NeoForge 事件总线。
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.register(VitalitySeveranceHandler.class);
    }

    // ==================== 事件监听 ====================

    /**
     * 在实体恢复生命值时，检查禁疗状态并削减治疗量。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        // 如果 ASM 已在 heal() 中处理过禁疗，跳过
        if (EntityASMUtil.consumeVitalitySeveranceFlag()) return;

        LivingEntity entity = event.getEntity();
        float amount = event.getAmount();

        // 1. 永久配置（百分比 + 固定值）
        var config = VitalitySeveranceConfig.get(entity);
        if (config != null) {
            amount = config.apply(amount);
        }

        // 2. 临时禁疗（factor 削减，来自绝妄生机时间属性 / 完全禁疗）
        float tempBan = getBanFactor(entity);
        if (tempBan > 0) {
            amount *= (1.0f - tempBan);
        }

        // 3. 叠加式绝妄生机（percent 0~100，辖界者每次攻击 +5% 可叠到 100%）
        float stack = getStackingPercent(entity);
        if (stack > 0) {
            amount *= (1.0f - Math.min(1.0f, stack / 100.0f));
        }

        if (amount <= 0) {
            event.setCanceled(true);
        } else {
            event.setAmount(amount);
        }
    }

    // ==================== 临时禁疗管理 ====================

    /**
     * 为实体添加临时禁疗。
     *
     * @param entity   目标实体
     * @param factor   禁疗系数（0.0~1.0）
     * @param duration 持续游戏刻
     */
    public static void addTempBan(LivingEntity entity, float factor, long duration) {
        long expiry = entity.level().getGameTime() + duration;
        BANS.put(entity.getUUID(), new BanEntry(Math.min(1.0f, Math.max(0, factor)), expiry));
    }

    /**
     * 获取实体的临时禁疗系数。
     */
    public static float getBanFactor(LivingEntity entity) {
        BanEntry entry = BANS.get(entity.getUUID());
        if (entry == null) return 0;

        if (entity.level().getGameTime() >= entry.expiryTick) {
            BANS.remove(entity.getUUID());
            return 0;
        }
        return entry.banFactor;
    }

    /**
     * 移除实体的临时禁疗。
     */
    public static void removeTempBan(LivingEntity entity) {
        BANS.remove(entity.getUUID());
    }

    // ==================== 叠加式绝妄生机（百分比累积，每次攻击 +N% 上限 100%） ====================

    private static final Map<UUID, StackEntry> STACK_BANS = new ConcurrentHashMap<>();

    /**
     * 叠加式绝妄生机：在现有基础上 +deltaPercent（上限 100%），并重置持续时长。
     * 连续攻击持续叠加（辖界者每次攻击 +5% → 最高 100% = 完全禁疗）；期限内无攻击则过期归零。
     */
    public static void addStackingBan(LivingEntity entity, float deltaPercent, long duration) {
        StackEntry prev = STACK_BANS.get(entity.getUUID());
        float next = Math.min(100.0f, (prev != null ? prev.percent : 0.0f) + deltaPercent);
        STACK_BANS.put(entity.getUUID(), new StackEntry(next, entity.level().getGameTime() + duration));
    }

    /** 获取叠加式绝妄生机当前百分比（0~100）；过期自动清除。 */
    public static float getStackingPercent(LivingEntity entity) {
        StackEntry e = STACK_BANS.get(entity.getUUID());
        if (e == null) return 0;
        if (entity.level().getGameTime() >= e.expiryTick) {
            STACK_BANS.remove(entity.getUUID());
            return 0;
        }
        return e.percent;
    }

    // ==================== Tick 级禁疗强制（防自定义血量实体绕过 heal/setHealth） ====================

    /**
     * 每个 Float 通道上一个已知的值。
     * 用于检测未经授权的健康值恢复（外部模组直接修改 DataParameter 绕过 heal()）。
     */
    private static final Map<UUID, Map<Integer, Float>> CHANNEL_SNAPSHOTS = new ConcurrentHashMap<>();

    /**
     * 周期性禁疗强制：检测实体各 Float 通道是否出现了未经授权的增长，
     * 如果是，按禁疗配置削减。
     * <p>
     * 通过 {@link DirectHealthFallback#forEachFloatItem} 直接访问
     * {@code SynchedEntityData.DataItem[]}，确保其他模组的所有自定义
     * Float 通道（含非静态定义的）都能被覆盖。
     * </p>
     * <p>
     * 由 {@code LivingEntityMixin.yizmodqzk$onTick()} 每 ~10 tick 调用一次。
     * </p>
     */
    public static void enforceTick(LivingEntity entity) {
        var config = VitalitySeveranceConfig.get(entity);
        if (config == null) {
            CHANNEL_SNAPSHOTS.remove(entity.getUUID());
            return;
        }

        Map<Integer, Float> prev = CHANNEL_SNAPSHOTS.get(entity.getUUID());
        Map<Integer, Float> current = new HashMap<>();

        // 1. 通过 itemsById 反射读取所有 Float 通道当前值
        DirectHealthFallback.forEachFloatItem(entity, (accessor, value, item) -> {
            current.put(accessor.id(), value);
        });

        // 2. 逐通道检查增长并强制禁疗
        if (prev != null) {
            for (Map.Entry<Integer, Float> entry : current.entrySet()) {
                Integer id = entry.getKey();
                float now = entry.getValue();
                Float was = prev.get(id);
                if (was != null && now > was + 0.01f) {
                    float increase = now - was;
                    float allowed = config.apply(increase);
                    float banned = increase - Math.max(0, allowed);
                    if (banned > 0.01f) {
                        // 找到对应的 DataItem 写入减少后的值
                        DirectHealthFallback.forEachFloatItem(entity, (acc, v, item) -> {
                            if (acc.id() == id) {
                                item.setValue(v - banned);
                                item.setDirty(true);
                            }
                        });
                        current.put(id, now - banned);
                    }
                }
            }
        }

        // 3. 更新快照
        CHANNEL_SNAPSHOTS.put(entity.getUUID(), current);
    }

    /**
     * 更新通道快照基线（在 <ul>
     *   <li>{@code EntityASMUtil.modifyHealth()} 主动治疗后</li>
     *   <li>{@code EntityASMUtil.addDelta()} 伤害后</li>
     *   <li>{@code setHealth()} RETURN</li>
     * </ul>
     * 调用，防止 tick 级强制将我们的修改也拦截掉）。
     */
    public static void updateBaseline(LivingEntity entity) {
        if (VitalitySeveranceConfig.get(entity) == null) return;
        Map<Integer, Float> snap = new HashMap<>();
        DirectHealthFallback.forEachFloatItem(entity, (accessor, value, item) -> {
            snap.put(accessor.id(), value);
        });
        CHANNEL_SNAPSHOTS.put(entity.getUUID(), snap);
    }

    // ==================== 字段级禁疗强制（自研血量实体回弹抵消） ====================

    /** 定位真实血量字段（totalDamageTaken 等）的基线快照，检测回弹用。 */
    private static final Map<UUID, Double> FIELD_SNAPSHOTS = new ConcurrentHashMap<>();

    /**
     * 字段级绝妄生机强制：对已绝妄生机目标，用 {@link EntityHealthLocator} 定位真实血量字段，
     * 检测「回血方向」变化（inverse 型字段减少 / 正向型字段增加 = 回血）→ 反射写回基线抵消。
     * <p>补上通道级强制（DataParameter）的盲区——自研血量实体的真实血量字段是<b>普通反射字段</b>，
     * 不在 DataParameter 里，最初梦幻能改它（applyPersistentDamage）而旧禁疗扫不到 → 回血完全不受限。</p>
     * <p>由 {@code LivingEntityMixin.yizmodqzk$onTick()} 每 ~10 tick 与 enforceTick 一起调用。</p>
     */
    public static void enforceFieldTick(LivingEntity entity) {
        var config = VitalitySeveranceConfig.get(entity);
        if (config == null) {
            FIELD_SNAPSHOTS.remove(entity.getUUID());
            return;
        }
        var slot = EntityHealthLocator.locate(entity);
        if (slot == null) {
            FIELD_SNAPSHOTS.remove(entity.getUUID()); // 原版实体走通道级强制
            return;
        }
        Double cur = EntityHealthLocator.readLocated(entity);
        if (cur == null) return;
        Double prev = FIELD_SNAPSHOTS.get(entity.getUUID());
        if (prev != null) {
            // 回血 = 字段向「血量增加」方向变化：inverse 型（totalDamageTaken）减少、正向型（血量存储）增加
            boolean healed = slot.inverse() ? (cur < prev) : (cur > prev);
            if (healed) {
                EntityHealthLocator.writeLocated(entity, prev); // 抵消回血，写回基线
                cur = prev;
            }
        }
        FIELD_SNAPSHOTS.put(entity.getUUID(), cur);
    }

    /**
     * 更新字段基线（本模组主动扣血 {@code EntityHealthLocator.applyPersistentDamage} 成功后调用，
     * 防止把这次扣血当成「回弹」抵消回去）。
     */
    public static void updateFieldBaseline(LivingEntity entity) {
        if (VitalitySeveranceConfig.get(entity) == null) return;
        Double cur = EntityHealthLocator.readLocated(entity);
        if (cur != null) FIELD_SNAPSHOTS.put(entity.getUUID(), cur);
    }

    // ==================== 内部数据类型 ====================

    private record BanEntry(float banFactor, long expiryTick) {}

    private record StackEntry(float percent, long expiryTick) {}
}
