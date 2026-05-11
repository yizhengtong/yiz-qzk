package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 禁疗效果处理器
 * 管理攻击后禁疗计时器，并在治疗事件中拦截治疗量。
 *
 * <p>处理两层禁疗：</p>
 * <ul>
 *   <li><b>被动禁疗</b> — 目标自身的眷恋(ATTACHMENT)属性提供永久的治疗削减</li>
 *   <li><b>主动禁疗</b> — 攻击者的眷恋属性使目标获得临时禁疗（5秒/100tick）</li>
 * </ul>
 */
public final class HealBanHandler {

    private static boolean registered = false;

    /** 禁疗条目：banFactor 0.0~1.0, expiryTick 到期游戏刻 */
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
     * 在目标受伤后（已计算护甲减免），根据攻击者的眷恋属性施加禁疗。
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        var source = event.getSource();
        if (source == null) return;

        var attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return;

        double attachment = player.getAttributeValue(ModAttributes.ATTACHMENT);
        if (attachment <= 0) return;

        float banFactor = (float) Math.min(1.0, attachment / 10.0);
        long expiry = event.getEntity().level().getGameTime() + 100; // 100 ticks = 5 seconds
        BANS.put(event.getEntity().getUUID(), new BanEntry(banFactor, expiry));
    }

    /**
     * 在实体恢复生命值时，检查禁疗状态并削减治疗量。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        float effectiveFactor = getEffectiveBanFactor(entity);
        if (effectiveFactor <= 0) return;

        if (effectiveFactor >= 1.0f) {
            // 百分百禁疗 → 完全取消治疗
            event.setCanceled(true);
        } else {
            // 按比例削减治疗量
            event.setAmount(event.getAmount() * (1.0f - effectiveFactor));
        }
    }

    // ==================== 禁疗查询 ====================

    /**
     * 获取实体的综合禁疗系数（0.0~1.0）。
     * 取临时禁疗和被动禁疗的最大值。
     * 如果实体没有眷恋属性，被动禁疗为 0。
     */
    public static float getEffectiveBanFactor(LivingEntity entity) {
        float tempBan = getBanFactor(entity);
        var attrInstance = entity.getAttribute(ModAttributes.ATTACHMENT);
        float attrBan = 0;
        if (attrInstance != null) {
            attrBan = (float) Math.min(1.0, attrInstance.getValue() / 10.0);
        }
        return Math.max(tempBan, attrBan);
    }

    /**
     * 获取实体的临时禁疗系数（来自攻击者施加）。
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

    // ==================== 内部数据类型 ====================

    private record BanEntry(float banFactor, long expiryTick) {}
}
