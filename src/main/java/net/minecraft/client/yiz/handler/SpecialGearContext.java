package net.minecraft.client.yiz.handler;

import net.minecraft.world.item.ItemStack;

/**
 * 单个装备槽编译后的上下文数据。
 * 由 {@link SpecialGearRouter} 生成，供 {@link SpecialMechanismEngine} 调度。
 *
 * @param slot      装备槽索引 0-5
 * @param stack     装备物品栈
 * @param passiveGroup 唯一被动组（空=无限制），路由层用于去重
 */
public record SpecialGearContext(int slot, ItemStack stack, String passiveGroup) {

    /** 组件不可变，超快速判断 */
    public boolean hasPassiveGroup() {
        return !passiveGroup.isEmpty();
    }
}
