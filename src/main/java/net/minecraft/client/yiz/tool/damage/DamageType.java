package net.minecraft.client.yiz.tool.damage;

import net.minecraft.network.chat.Component;

/**
 * 伤害类型枚举
 * 定义标准化的伤害类型，用于伤害源创建和统计。
 */
public enum DamageType {

    // 物理伤害
    PHYSICAL("physical", "物理伤害"),
    SLASHING("slashing", "斩击伤害"),
    PIERCING("piercing", "穿刺伤害"),
    BLUNT("blunt", "钝击伤害"),

    // 元素伤害
    FIRE("fire", "火焰伤害"),
    ICE("ice", "冰霜伤害"),
    LIGHTNING("lightning", "闪电伤害"),
    POISON("poison", "毒素伤害"),

    // 魔法伤害
    MAGIC("magic", "魔法伤害"),
    ARCANE("arcane", "奥术伤害"),
    VOID("void", "虚空伤害"),

    // 特殊伤害
    TRUE("true", "真实伤害（无视护甲）"),
    PERCENTAGE("percentage", "百分比伤害"),
    REFLECT("reflect", "反伤");

    private final String id;
    private final String displayName;

    DamageType(String id, String displayName) {
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
        return Component.translatable("damage_type.yizmodqzk." + id);
    }
}
