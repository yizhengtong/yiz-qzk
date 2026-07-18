package net.minecraft.client.yiz.editor;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * 标记槽——存储自定义宽高供 Screen 渲染与鼠标检测使用。
 * 不覆写 isMouseOver（1.21.1 已移除），交互由 Screen 层处理。
 */
public class SizedSlot extends Slot {

    private final int slotWidth;
    private final int slotHeight;

    public SizedSlot(Container container, int index, int x, int y, int width, int height) {
        super(container, index, x, y);
        this.slotWidth = width;
        this.slotHeight = height;
    }

    public int getSlotWidth()  { return slotWidth; }
    public int getSlotHeight() { return slotHeight; }
}
