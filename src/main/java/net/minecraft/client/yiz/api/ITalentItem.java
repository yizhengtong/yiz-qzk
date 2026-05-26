package net.minecraft.client.yiz.api;

import net.minecraft.world.item.Item;

/**
 * 天赋物品标记接口。
 * <p>
 * 实现此接口的 Item 自动归入对应下游模组的「天赋」创造标签页（母页 A）。
 * 如果某模组没有实现此接口的物品，则该标签页不会注册。
 * </p>
 */
// 大白话: 天赋标签方法
public interface ITalentItem {
    /** 创建该物品天赋页使用的默认图标物品。 */
    default Item getTabIcon() {
        if (this instanceof Item item) return item;
        return (Item) this;
    }
}
