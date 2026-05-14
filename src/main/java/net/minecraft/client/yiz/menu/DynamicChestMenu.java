package net.minecraft.client.yiz.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 通用变列容器菜单 — 根据槽位数动态计算列数，使容器区域视觉比例接近正方形。
 *
 * <p>布局参数由 {@link #calcLayout(int)} 计算，支持 9~16 列自适应。
 * 列数超过 9 时，底部玩家背包在内容区中水平居中。
 */
public class DynamicChestMenu extends AbstractContainerMenu {
    public static final int SLOT = 18;
    public static final int BORDER = 5;

    private final Container container;
    private final int containerSlots;
    private final int cols;
    private final int rows;

    /**
     * 完整构造（服务端使用持久容器、客户端使用 MenuType factory 新建容器均可）。
     */
    public DynamicChestMenu(MenuType<?> type, int id, Inventory playerInv, int slots, Container container) {
        super(type, id);
        this.container = container;
        this.containerSlots = slots;

        int[] layout = calcLayout(slots);
        this.cols = layout[0];
        this.rows = layout[1];

        int invOffset = cols > 9 ? (cols - 9) * SLOT / 2 : 0;
        int playerInvY = BORDER + rows * SLOT + 14;
        int hotbarY = playerInvY + 3 * SLOT + 4;

        // 容器槽位
        for (int i = 0; i < slots; i++) {
            int col = i % cols;
            int row = i / cols;
            addSlot(new Slot(container, i, BORDER + col * SLOT, BORDER + row * SLOT));
        }

        // 玩家主物品栏（3 行 × 9 列）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, 9 + row * 9 + col,
                    BORDER + invOffset + col * SLOT, playerInvY + row * SLOT));
            }
        }

        // 快捷栏（1 行 × 9 列）
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col,
                BORDER + invOffset + col * SLOT, hotbarY));
        }
    }

    /**
     * 动态列布局算法。
     *
     * @param slots 容器总格数
     * @return int[2] = {cols, rows}
     */
    public static int[] calcLayout(int slots) {
        if (slots <= 27) {
            return new int[]{9, (int) Math.ceil((double) slots / 9)};
        }
        int bestCols = 9;
        double bestRatio = Double.MAX_VALUE;
        for (int cols = 9; cols <= 16; cols++) {
            int rows = (int) Math.ceil((double) slots / cols);
            double ratio = Math.max(cols, rows) / (double) Math.min(cols, rows);
            if (ratio < bestRatio) {
                bestRatio = ratio;
                bestCols = cols;
            }
        }
        return new int[]{bestCols, (int) Math.ceil((double) slots / bestCols)};
    }

    public Container getContainer() {
        return container;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getContainerSlotCount() {
        return containerSlots;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < containerSlots) {
                // 容器 → 玩家（反向：优先填充主物品栏，再填充快捷栏）
                if (!moveItemStackTo(stack, containerSlots, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 玩家 → 容器
                if (!moveItemStackTo(stack, 0, containerSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
