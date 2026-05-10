package net.minecraft.client.yiz.tool.damage;

import net.minecraft.network.chat.Component;

/**
 * 伤害标签系统
 * 三种标签可以独立使用，也可以组合使用。
 */
public enum DamageTag {

    /**
     * 真实伤害标签
     * 特性：直接修改目标 Health，天生具备穿透无敌帧效果。
     */
    TRUE_DAMAGE("true_damage", "真实伤害"),

    /**
     * 破甲伤害标签
     * 特性：跳过护甲减伤计算，跳过 CombatRules.getDamageAfterAbsorb()。
     */
    ARMOR_PIERCING("armor_piercing", "破甲伤害"),

    /**
     * 破除无敌帧标签
     * 特性：无视目标的无敌帧（hurtTime / invulnerableTime）。
     */
    PIERCE_INVULNERABILITY("pierce_invulnerability", "破除无敌帧");

    private final String id;
    private final String displayName;

    DamageTag(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Component getLocalizedDisplayName() {
        return Component.translatable("damage_tag.yizmodqzk." + id);
    }
}
