package net.minecraft.client.yiz.api;

/**
 * 伤害类型
 */
public enum DamageType {
    /** 固定数值伤害 */
    FLAT,
    /** 目标最大生命值百分比伤害 */
    PERCENT,
    /** 真实伤害（直接 setHealth，无视护甲/无敌帧/减伤） */
    TRUE,
    /** 破甲伤害（跳过护甲减伤，仍受无敌帧限制） */
    ARMOR_PIERCING,
    /** 破无敌帧（可单独或与破甲组合使用） */
    PIERCE_INVULNERABILITY
}
