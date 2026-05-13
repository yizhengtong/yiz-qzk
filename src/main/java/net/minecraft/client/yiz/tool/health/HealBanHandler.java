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

/**
 * 禁疗效果处理器
 * <p>
 * 在 {@link LivingHealEvent} 层面拦截治疗，强制执行 {@link HealBanConfig} 的禁疗配置。
 * 同时管理攻击者主动施加的临时禁疗计时器。
 * </p>
 *
 * <p>本处理器是第二道防线，与 {@link EntityASMUtil#modifyHealth} 和
 * {@link HealthApplier} 中的禁疗检查配合，确保所有治疗途径都被拦截。</p>
 */
public final class HealBanHandler {

    private static boolean registered = false;

    private static final Map<UUID, BanEntry> BANS = new HashMap<>();

    private HealBanHandler() {}

    /**
     * 注册到 NeoForge 事件总线。
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.register(HealBanHandler.class);
    }

    // ==================== 事件监听 ====================

    /**
     * 在实体恢复生命值时，检查禁疗状态并削减治疗量。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        // 如果 ASM 已在 heal() 中处理过禁疗，跳过
        if (EntityASMUtil.consumeHealBanFlag()) return;

        LivingEntity entity = event.getEntity();

        // 1. 检查 API 禁疗配置（百分比 + 固定值）
        var config = HealBanConfig.get(entity);
        if (config != null) {
            float original = event.getAmount();
            float banned = config.apply(original);
            if (banned <= 0) {
                event.setCanceled(true);
            } else {
                event.setAmount(banned);
            }
            return;
        }

        // 2. 检查临时禁疗（来自攻击者主动施加）
        float tempBan = getBanFactor(entity);
        if (tempBan > 0) {
            if (tempBan >= 1.0f) {
                event.setCanceled(true);
            } else {
                event.setAmount(event.getAmount() * (1.0f - tempBan));
            }
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

    // ==================== Tick 级禁疗强制（防泰坦绕过 heal/setHealth） ====================

    /**
     * 每个 Float 通道上一个已知的值。
     * 用于检测未经授权的健康值恢复（外部模组直接修改 DataParameter 绕过 heal()）。
     */
    private static final Map<UUID, Map<Integer, Float>> CHANNEL_SNAPSHOTS = new HashMap<>();

    /**
     * 周期性禁疗强制：检测实体各 Float 通道是否出现了未经授权的增长，
     * 如果是，按禁疗配置削减。
     * <p>
     * 由 {@code LivingEntityMixin.yizmodqzk$onTick()} 每 ~10 tick 调用一次。
     * 主要应对泰坦类实体在 tick()/aiStep() 中直接修改自定义 DataParameter 的恢复方式。
     * </p>
     */
    public static void enforceTick(LivingEntity entity) {
        var config = HealBanConfig.get(entity);
        if (config == null) {
            CHANNEL_SNAPSHOTS.remove(entity.getUUID());
            return;
        }

        List<EntityDataAccessor<Float>> channels = HealthChannelScanner.getAllFloatChannels(entity);
        Map<Integer, Float> prev = CHANNEL_SNAPSHOTS.get(entity.getUUID());
        Map<Integer, Float> current = new HashMap<>();

        // 1. 读取当前各通道值
        for (EntityDataAccessor<Float> ch : channels) {
            current.put(ch.id(), entity.getEntityData().get(ch));
        }

        // 2. 逐通道检查增长并强制禁疗
        if (prev != null) {
            for (EntityDataAccessor<Float> ch : channels) {
                float now = current.get(ch.id());
                Float was = prev.get(ch.id());
                if (was != null && now > was + 0.01f) {
                    float increase = now - was;
                    float allowed = config.apply(increase);
                    float banned = increase - Math.max(0, allowed);
                    if (banned > 0.01f) {
                        entity.getEntityData().set(ch, now - banned);
                        current.put(ch.id(), now - banned);
                    }
                }
            }
        }

        // 3. 更新快照
        CHANNEL_SNAPSHOTS.put(entity.getUUID(), current);
    }

    /**
     * 更新通道快照基线（在 {@code EntityASMUtil.modifyHealth()} 主动治疗后调用）。
     * 防止 tick 级强制将我们自己的治疗也拦截掉。
     */
    public static void updateBaseline(LivingEntity entity) {
        if (HealBanConfig.get(entity) == null) return;
        List<EntityDataAccessor<Float>> channels = HealthChannelScanner.getAllFloatChannels(entity);
        Map<Integer, Float> snap = new HashMap<>();
        for (EntityDataAccessor<Float> ch : channels) {
            snap.put(ch.id(), entity.getEntityData().get(ch));
        }
        CHANNEL_SNAPSHOTS.put(entity.getUUID(), snap);
    }

    // ==================== 内部数据类型 ====================

    private record BanEntry(float banFactor, long expiryTick) {}
}
