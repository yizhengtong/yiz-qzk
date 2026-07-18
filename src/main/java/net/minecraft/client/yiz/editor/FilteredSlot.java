package net.minecraft.client.yiz.editor;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 条件过滤槽——mayPlace 检查物品是否实现指定接口。 */
public class FilteredSlot extends Slot {

    private final Class<?> requiredInterface;

    public FilteredSlot(Container container, int index, int x, int y, Class<?> requiredInterface) {
        super(container, index, x, y);
        this.requiredInterface = requiredInterface;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (requiredInterface == null) return true;
        return requiredInterface.isInstance(stack.getItem());
    }
}
