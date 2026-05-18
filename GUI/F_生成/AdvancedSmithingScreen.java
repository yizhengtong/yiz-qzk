/**
 * 高级锻造台 GUI — 局内积木拼凑
 * 放入槽 + 材料槽A/B + 输出槽，菱形排列
 */
public class AdvancedSmithingScreen
        extends AbstractContainerScreen<AdvancedSmithingMenu> {

    private static final ResourceLocation TEX_BG       = tex("fill_white");
    private static final ResourceLocation TEX_CORNER   = tex("border_corner_tl");
    private static final ResourceLocation TEX_EDGE_T   = tex("border_edge_top");
    private static final ResourceLocation TEX_EDGE_B   = tex("border_edge_bottom");
    private static final ResourceLocation TEX_EDGE_L   = tex("border_edge_left");
    private static final ResourceLocation TEX_EDGE_R   = tex("border_edge_right");
    private static final ResourceLocation TEX_SLOT     = tex("slot_default");
    private static final ResourceLocation TEX_SPLIT    = tex("split_bar");
    private static final ResourceLocation TEX_PLAYER   = tex("player_slots_9x4");

    private static final int SLOT   = 18;
    private static final int BORDER = 5;
    private static final int COLS   = 9;
    private static final int CONTENT_W = COLS * SLOT;     // 162
    private static final int WINDOW_W  = BORDER * 2 + CONTENT_W; // 172

    private static final int CONTAINER_ROWS = 4;
    private static final int CONTAINER_H    = CONTAINER_ROWS * SLOT; // 72
    private static final int SPLIT_H = 14;
    private static final int PLAYER_H = 76;
    private static final int WINDOW_H = BORDER * 2 + CONTAINER_H + SPLIT_H + PLAYER_H; // 172

    // --- 槽位数据坐标（Menu 坐标，不含 -1 偏移） ---
    private static final int INPUT_X  = 72, INPUT_Y  = 27;
    private static final int MAT_A_X  = 72, MAT_A_Y  = 9;
    private static final int MAT_B_X  = 72, MAT_B_Y  = 45;
    private static final int OUTPUT_X = 108, OUTPUT_Y = 27;
    private static final int PLAYER_Y = BORDER + CONTAINER_H + SPLIT_H; // 91

    public AdvancedSmithingScreen(AdvancedSmithingMenu menu,
                                  Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = WINDOW_W;
        this.imageHeight = WINDOW_H;
    }

    @Override
    protected void renderBg(GuiGraphics g, float delta, int mx, int my) {
        int x = leftPos, y = topPos;

        // 1. 背景
        g.blit(TEX_BG, x + BORDER, y + BORDER, 0, 0,
               CONTENT_W, CONTAINER_H + SPLIT_H + PLAYER_H,
               CONTENT_W, CONTAINER_H + SPLIT_H + PLAYER_H);

        // 2. 边框 — 四角 + 四边
        g.blit(TEX_CORNER, x, y, 0, 0, 5, 5, 5, 5);              // 左上 → TL
        g.blit(TEX_CORNER, x + WINDOW_W - 5, y + WINDOW_H - 5,
               0, 0, 5, 5, 5, 5);
        g.blit(TEX_BG,     x, y, 0, 0, CONTENT_W, 5);             // 顶边
        g.blit(TEX_BG,     x, y + WINDOW_H - 5,
               0, 0, CONTENT_W, 5);

        // 3. 槽位背景（-1px 偏移规则）
        drawSlot(g, x, y, INPUT_X,  INPUT_Y);
        drawSlot(g, x, y, MAT_A_X,  MAT_A_Y);
        drawSlot(g, x, y, MAT_B_X,  MAT_B_Y);
        drawSlot(g, x, y, OUTPUT_X, OUTPUT_Y);

        // 4. 分隔条
        int splitY = y + BORDER + CONTAINER_H;
        for (int i = 0; i < CONTENT_W; i++) {
            g.blit(TEX_SPLIT, x + BORDER + i, splitY, 0, 0, 1, SPLIT_H, 1, SPLIT_H);
        }

        // 5. 玩家背包
        g.blit(TEX_PLAYER, x + BORDER - 1, y + PLAYER_Y - 1,
               0, 0, 162, 76, 162, 76);
    }

    private void drawSlot(GuiGraphics g, int x, int y, int sx, int sy) {
        g.blit(TEX_SLOT, x + BORDER + sx - 1, y + BORDER + sy - 1,
               0, 0, 18, 18, 18, 18);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            "yizmodqzk", "textures/gui/container/" + name + ".png"
        );
    }
}
