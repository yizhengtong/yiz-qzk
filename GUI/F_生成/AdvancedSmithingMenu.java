/**
 * 高级锻造台 Menu — 4 槽位菱形布局
 */
public class AdvancedSmithingMenu extends AbstractContainerMenu {

    private static final int SLOT   = 18;
    private static final int BORDER = 5;

    // 必须与 Screen 坐标完全一致
    private static final int INPUT_X  = 72, INPUT_Y  = 27;
    private static final int MAT_A_X  = 72, MAT_A_Y  = 9;
    private static final int MAT_B_X  = 72, MAT_B_Y  = 45;
    private static final int OUTPUT_X = 108, OUTPUT_Y = 27;
    private static final int PLAYER_Y = 91; // BORDER + CONTAINER_H + SPLIT_H

    public AdvancedSmithingMenu(int containerId, Inventory playerInv) {
        super(ModMenus.ADVANCED_SMITHING.get(), containerId);

        // 自定义槽位（数据坐标，不含 -1 偏移）
        addSlot(new Slot(inputContainer,  0, INPUT_X,  INPUT_Y));
        addSlot(new Slot(matAContainer,   0, MAT_A_X,  MAT_A_Y));
        addSlot(new Slot(matBContainer,   0, MAT_B_X,  MAT_B_Y));
        addSlot(new Slot(outputContainer, 0, OUTPUT_X, OUTPUT_Y));

        // 玩家背包（标准 9×4 排列）
        int invY = PLAYER_Y;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv,
                    col + row * 9 + 9,
                    BORDER + col * SLOT,
                    invY + row * SLOT
                ));
            }
        }

        // 快捷栏
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col,
                BORDER + col * SLOT,
                invY + 4 * SLOT + 4
            ));
        }
    }
}
