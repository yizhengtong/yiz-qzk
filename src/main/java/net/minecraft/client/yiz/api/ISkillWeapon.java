package net.minecraft.client.yiz.api;

import net.minecraft.world.item.ItemStack;

/**
 * 技能武器标记接口。
 * <p>
 * 实现此接口的 Item 声明自己属于哪种技能武器类型，
 * 用于技能施法槽位的放置判定。
 * </p>
 *
 * <h3>五种技能武器类型</h3>
 * <table>
 * <tr><th>类型</th><th>说明</th><th>示例物品</th></tr>
 * <tr><td>{@link SkillType#MELEE MELEE}</td><td>近战武器 — 刀/剑/斧等物理攻击</td><td>泰拉刃</td></tr>
 * <tr><td>{@link SkillType#ACTIVE_SPELL ACTIVE_SPELL}</td><td>主动法术 — 右键施放，有冷却/消耗</td><td>—</td></tr>
 * <tr><td>{@link SkillType#PASSIVE_SPELL PASSIVE_SPELL}</td><td>被动法术 — 常驻增益效果</td><td>—</td></tr>
 * <tr><td>{@link SkillType#SUPPORT_SPELL SUPPORT_SPELL}</td><td>辅助法术 — 治疗/护盾/净化等</td><td>—</td></tr>
 * <tr><td>{@link SkillType#SUMMON SUMMON}</td><td>召唤武器 — 召唤仆从/飞剑/灵体</td><td>泰拉棱镜</td></tr>
 * </table>
 *
 * @see net.minecraft.client.yiz.ui.InventoryPanel 技能施法槽位
 */
public interface ISkillWeapon {

    /** 技能武器类型枚举 */
    enum SkillType {
        MELEE,
        ACTIVE_SPELL,
        PASSIVE_SPELL,
        SUPPORT_SPELL,
        SUMMON
    }

    /**
     * 获取此武器的技能类型。
     */
    SkillType getSkillType();

    /**
     * 获取武器的攻击伤害。
     */
    default double getAttackDamage(ItemStack stack) {
        return 0;
    }

    /**
     * 获取武器的攻击速度。
     */
    default double getAttackSpeed(ItemStack stack) {
        return 1.0;
    }

    /**
     * 武器主手交互（右键）。
     */
    default boolean onWeaponUse(ItemStack stack) {
        return false;
    }

    /**
     * 武器潜行交互（Shift+右键）。
     */
    default boolean onWeaponShiftUse(ItemStack stack) {
        return false;
    }
}
