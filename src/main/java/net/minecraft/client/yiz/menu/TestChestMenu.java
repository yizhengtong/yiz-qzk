package net.minecraft.client.yiz.menu;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 测试容器 — 27格（3×9）箱子槽位 + 玩家背包36格 + 快捷栏9格
 *
 * 槽位坐标与 TestChestScreen 的纹理布局严格对应：
 *
 *   窗口总尺寸 172×154
 *   边框 5px，内容区从 (5,5) 开始
 *
 *   箱子槽位：  y=5      起，3行 × 9列
 *   分隔带：    14px（视觉分隔，无槽位）
 *   玩家背包：  y=73     起，3行 × 9列
 *   快捷栏：    y=131    起，1行 × 9列
 *   底边框：    5px
 */
public class TestChestMenu extends AbstractContainerMenu {

    private static final int COLS = 9;
    private static final int SLOT = 18;
    private static final int BORDER = 5;

    // 箱子槽区
    private static final int CHEST_ROWS = 3;
    private static final int CHEST_SLOTS = CHEST_ROWS * COLS; // 27
    private static final int CHEST_Y = BORDER;

    // 玩家背包区域
    private static final int PLAYER_INV_Y = 73; // BORDER + CHEST_ROWS*SLOT + SPLIT(14)
    private static final int PLAYER_ROWS = 3;

    // 快捷栏
    private static final int HOTBAR_Y = 131; // PLAYER_INV_Y + PLAYER_ROWS*SLOT + gap

    private final Container chest;

    /** 客户端构造 */
    public TestChestMenu(int containerId, Inventory playerInv) {
        this(containerId, playerInv, new SimpleContainer(CHEST_SLOTS));
    }

    /** 服务端构造 */
    public TestChestMenu(int containerId, Inventory playerInv, Container chestInv) {
        super(ModMenus.TEST_CHEST.get(), containerId);
        this.chest = chestInv;
        chestInv.startOpen(playerInv.player);

        // ── 箱子槽位 (27格, 3×9) ──
        for (int row = 0; row < CHEST_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(chestInv, col + row * COLS,
                    BORDER + col * SLOT, CHEST_Y + row * SLOT));
            }
        }

        // ── 玩家物品栏 (27格, 跳过盔甲栏) ──
        for (int row = 0; row < PLAYER_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                addSlot(new Slot(playerInv, col + row * COLS + COLS,
                    BORDER + col * SLOT, PLAYER_INV_Y + row * SLOT));
            }
        }

        // ── 快捷栏 (9格) ──
        for (int col = 0; col < COLS; col++) {
            addSlot(new Slot(playerInv, col,
                BORDER + col * SLOT, HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();

            int chestEnd = CHEST_SLOTS;
            int invEnd = chestEnd + COLS * PLAYER_ROWS;
            int total = invEnd + COLS;

            if (slotIndex < chestEnd) {
                // 箱子 → 玩家背包
                if (!moveItemStackTo(slotStack, chestEnd, total, true))
                    return ItemStack.EMPTY;
            } else {
                // 玩家背包/快捷栏 → 箱子
                if (!moveItemStackTo(slotStack, 0, chestEnd, false))
                    return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();

            if (slotStack.getCount() == stack.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, slotStack);
        }
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return chest.stillValid(player);
    }
}
