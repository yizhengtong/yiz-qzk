package net.minecraft.client.yiz.api;

import net.minecraft.world.item.Item;

/**
 * 通用物品标记接口。
 * <p>
 * 实现此接口的 Item 自动归入对应下游模组的「物品」创造标签页（母页 C）。
 * 如果某模组没有实现此接口的物品，则该标签页不会注册。
 * </p>
 */
public interface IGeneralItem {
    default Item getTabIcon() {
        if (this instanceof Item item) return item;
        return (Item) this;
    }
}
