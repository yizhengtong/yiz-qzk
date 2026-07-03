package net.minecraft.client.yiz.api;

import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 技能施法槽位条目 — 描述快捷栏下方单个施法槽位的内容。
 * <p>
 * 供下游模组通过 {@link YizModQZKAPI#getSkillSlotEntries} 查询
 * 当前玩家技能槽位中放置了哪些武器及其关联信息。
 * </p>
 *
 * @param slotIndex   槽位索引 (0-8)
 * @param item        槽位中的物品堆叠（空槽位返回 {@link ItemStack#EMPTY}）
 * @param skillType   武器技能类型
 * @param attackDamage 攻击伤害
 * @param attackSpeed  攻击速度
 * @param effects     该武器关联的效果列表（通过 NBT 或注册表查找）
 */
public record SkillSlotEntry(
    int slotIndex,
    ItemStack item,
    ISkillWeapon.SkillType skillType,
    double attackDamage,
    double attackSpeed,
    List<AbstractEffect> effects
) {
    public boolean isEmpty() { return item.isEmpty(); }
    public boolean isOccupied() { return !item.isEmpty(); }
}
