package net.minecraft.client.yiz.handler;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能后首击追踪器 — 技能释放后标记，下一次攻击消费标记并附加伤害+回血。
 *
 * <p>伤害公式：damage_base + spell_power × damage_spell_coeff/100</p>
 * <p>回血公式：heal_base + max_health × heal_hp_coeff/100</p>
 */
public final class PostSkillAttackTracker {

    private static final Map<UUID, Boolean> MARKED = new ConcurrentHashMap<>();
    /** 标记时用的技能物品（用于读取公式参数）。 */
    private static final Map<UUID, ItemStack> MARKED_ITEM = new ConcurrentHashMap<>();

    private PostSkillAttackTracker() {}

    /** 技能释放时标记（C2S 包处理）。 */
    public static void mark(Player player, ItemStack skillItem) {
        UUID uuid = player.getUUID();
        MARKED.put(uuid, true);
        MARKED_ITEM.put(uuid, skillItem.copy());
    }

    /** 攻击时尝试消费标记（服务端 hurt Mixin）。返回 [bonusDamage, bonusHeal]。 */
    public static float[] tryConsume(Player player) {
        UUID uuid = player.getUUID();
        if (!MARKED.getOrDefault(uuid, false)) return null;
        MARKED.remove(uuid);
        ItemStack item = MARKED_ITEM.remove(uuid);
        if (item == null || item.isEmpty()) return null;

        float damage = computeDamage(player, item);
        float heal = computeHeal(player, item);
        return new float[]{damage, heal};
    }

    private static float computeDamage(Player player, ItemStack item) {
        float base = (float) readAttr(item, YizAttributes.DAMAGE_BASE);
        double spellPow = YizAttributes.getEffectiveSpellPower(player);
        return (float)(base * spellPow / 100.0);
    }

    private static float computeHeal(Player player, ItemStack item) {
        float base = (float) readAttr(item, YizAttributes.HEAL_BASE);
        float hpCoeff = (float) readAttr(item, YizAttributes.HEAL_HP_COEFF);
        float maxHp = player.getMaxHealth();
        return base + maxHp * hpCoeff / 100f;
    }

    private static double readAttr(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double val = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().is(attr)) val += e.modifier().amount();
        }
        return val;
    }

    private static float readPlayerAttr(Player player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
        var inst = player.getAttribute(attr);
        return inst != null ? (float) inst.getValue() : 0f;
    }
}
