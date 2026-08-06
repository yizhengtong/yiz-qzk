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
    PIERCE_INVULNERABILITY("pierce_invulnerability", "破除无敌帧"),

    /**
     * 直接健康值修改标签
     * 特性：不走 hurt() 流程，直接调用 HealthModificationManager
     * 修改实体的健康值，绕过伤害免疫、闪避、护盾等机制。
     *
     * 工作流程（与 TRUE_DAMAGE 不同）：
     * - TRUE_DAMAGE：直接 setHealth(health - damage)
     * - DIRECT_HEALTH_MOD：走 HealthModificationManager 的完整管道，
     *   支持多修正器汇总、Delta 模式、BAN_HEALING 检查等
     */
    DIRECT_HEALTH_MOD("direct_health_mod", "直接健康值修改");

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
