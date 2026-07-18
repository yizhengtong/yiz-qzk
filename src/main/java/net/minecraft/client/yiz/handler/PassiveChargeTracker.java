package net.minecraft.client.yiz.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.yiz.api.PlayerDataAPI;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 被动充能追踪器 — 攻击计数 + 满 6 次触发"临时上限 buff"。
 *
 * <p>服务端 hurt Mixin 触发攻击计数。达到 6 时：对所有装载槽技能 max_charges 临时 +1 并补满
 * （即每个技能多 1 次可用次数），同时对本次命中的目标施加一次感电（伤害由被动物品公式决定），
 * 重置计数为 0。玩家使用任意一次技能后，临时 +1 buff 被消耗（多余次数作废），不再冻结 CD。</p>
 *
 * <h3>同步给客户端</h3>
 * <p>状态写 {@code yizmodqzk:charge_state}（JSON：{@code {count, buff}}），供 HUD 读攻击进度与
 * 是否处于 buff 态。</p>
 */
public final class PassiveChargeTracker {

    private static final int CHARGE_MAX = 6;
    private static final String STATE_KEY = "yizmodqzk:charge_state";

    /** 内存态：UUID → 是否持有临时+1 buff（服务端权威，配合 SkillChargeManager 判定有效上限）。 */
    private static final ConcurrentHashMap<UUID, Boolean> TEMP_BUFF = new ConcurrentHashMap<>();

    private PassiveChargeTracker() {}

    public static int chargeMax() { return CHARGE_MAX; }
    public static String stateKey() { return STATE_KEY; }

    /** 服务端：是否持有临时 +1 上限 buff。 */
    public static boolean hasTempBuff(Player player) {
        return TEMP_BUFF.getOrDefault(player.getUUID(), false);
    }

    /** 玩家登录：重置内存态，写默认状态供 HUD。 */
    public static void onLogin(Player player) {
        TEMP_BUFF.remove(player.getUUID());
        writeState(player, 0, false);
    }

    /** 玩家攻击时调用（服务端 hurt Mixin）。仅被动槽有物品时才充能。 */
    public static void onAttack(Player player) {
        onAttack(player, null);
    }

    /**
     * 玩家攻击时调用，可传入被攻击目标。仅被动槽有物品时充能；满 6 次触发临时上限 buff
     * 并对命中目标施加感电（伤害=被动物品公式，范围/间隔/时间默认）。
     */
    public static void onAttack(Player player, net.minecraft.world.entity.LivingEntity target) {
        if (!hasPassiveEquipped(player)) return;
        if (hasTempBuff(player)) {
            // 已持 buff 期间不再累积（避免重复触发）
            return;
        }
        int count = readCount(player) + 1;
        if (count >= CHARGE_MAX) {
            // 满 6 次：触发临时 +1 上限 buff + 补满所有技能充能
            grantTempBuff(player);
            writeState(player, 0, true);
            // 对命中目标施加感电
            if (target != null && !target.level().isClientSide()) {
                float dmg = computePassiveDamage(player);
                if (dmg > 0) {
                    net.minecraft.client.yiz.core.StatusEffectDispatcher.applyShockWithDamage(
                        target, player, dmg, 0, 0, 0);
                }
            }
        } else {
            writeState(player, count, false);
        }
    }

    /**
     * 玩家使用技能时调用（C2S 包处理）。若持有临时 +1 buff → 消耗它（移除临时上限，多余次数作废）。
     * 不再涉及 CD 冻结。
     */
    public static void onSkillCast(Player player) {
        if (hasTempBuff(player)) {
            TEMP_BUFF.remove(player.getUUID());
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                net.minecraft.client.yiz.handler.SkillChargeManager.removeTempBuff(sp);
            }
            writeState(player, readCount(player), false);
        }
    }

    // ── 临时 buff ──

    private static void grantTempBuff(Player player) {
        TEMP_BUFF.put(player.getUUID(), true);
        // 所有装载槽 max_charges +1 并补满到新上限
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.client.yiz.handler.SkillChargeManager.grantTempBuff(sp);
        }
    }

    // ── 状态读写（charge_state: {count, buff}）──

    private static int readCount(Player player) {
        JsonObject s = parseState(player);
        return s.has("count") ? s.get("count").getAsInt() : 0;
    }

    private static void writeState(Player player, int count, boolean buff) {
        JsonObject s = new JsonObject();
        s.addProperty("count", count);
        s.addProperty("buff", buff);
        PlayerDataAPI.set(player, STATE_KEY, s.toString());
    }

    /** 客户端读攻击进度（0..6）。 */
    public static int getClientCount(Player player) {
        JsonObject s = parseState(player);
        return s.has("count") ? s.get("count").getAsInt() : 0;
    }

    /** 客户端读是否处于临时 buff 态。 */
    public static boolean getClientBuff(Player player) {
        JsonObject s = parseState(player);
        return s.has("buff") && s.get("buff").getAsBoolean();
    }

    private static JsonObject parseState(Player player) {
        String raw;
        try { raw = PlayerDataAPI.get(player, STATE_KEY); } catch (Exception e) { raw = null; }
        if (raw == null || raw.isEmpty()) return new JsonObject();
        try { return JsonParser.parseString(raw).getAsJsonObject(); }
        catch (Exception e) { return new JsonObject(); }
    }

    // ── 被动伤害公式 ──

    /** 读取被动槽首个带伤害公式的物品，算 damage_base + spell_power × damage_spell_coeff/100。 */
    private static float computePassiveDamage(Player player) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return 0;
        net.minecraft.world.item.ItemStack passive = net.minecraft.world.item.ItemStack.EMPTY;
        for (int i = 0; i < 3; i++) {
            net.minecraft.world.item.ItemStack s = data.passiveLoad().getItem(i);
            if (!s.isEmpty()) { passive = s; break; }
        }
        if (passive.isEmpty()) return 0;
        double base = readItemAttr(passive, net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_BASE);
        double coeff = readItemAttr(passive, net.minecraft.client.yiz.attribute.YizAttributes.DAMAGE_SPELL_COEFF);
        double spellPow = net.minecraft.client.yiz.attribute.YizAttributes.getEffectiveSpellPower(player);
        return (float) (base + spellPow * coeff / 100.0);
    }

    private static double readItemAttr(net.minecraft.world.item.ItemStack stack,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var mods = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        double val = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().is(attr)) val += e.modifier().amount();
        }
        return val;
    }

    private static boolean hasPassiveEquipped(Player player) {
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return false;
        for (int i = 0; i < 3; i++) {
            if (!data.passiveLoad().getItem(i).isEmpty()) return true;
        }
        return false;
    }
}
