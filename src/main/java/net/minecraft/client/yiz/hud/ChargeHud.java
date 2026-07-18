package net.minecraft.client.yiz.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 充能类 HUD（通用框架版）。
 *
 * <p>遍历 {@link ChargeHudRegistry} 中所有激活的充能条目，每个条目渲染一行：
 * 源图标（左）+ 该条目 max 数量的充能格框架。多行纵向排列，互不干扰。</p>
 *
 * <p>典型条目：天雷引（满6给强化普攻+冷却缩减）、PassiveChargeTracker（满6给技能充能+1）。</p>
 *
 * <p>逻辑高度预留 MAX_ROWS 行的空间（编辑器固定尺寸），实际按激活条目数渲染。</p>
 */
public class ChargeHud extends HudElement {

    private static final ResourceLocation TEX_LEFT   = tex("charge_frame_left");
    private static final ResourceLocation TEX_RIGHT  = tex("charge_frame_right");
    private static final ResourceLocation TEX_TOP    = tex("charge_frame_top");
    private static final ResourceLocation TEX_BOTTOM = tex("charge_frame_bottom");
    private static final ResourceLocation TEX_BODY   = tex("charge_frame_body");
    private static final ResourceLocation TEX_EMPTY  = tex("charge_empty");
    private static final ResourceLocation TEX_FULL   = tex("charge_full");

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("yizmodqzk", "textures/hud/" + name + ".png");
    }

    // 框架
    private static final int FR_L = 3, FR_R = 3, FR_T = 2, FR_B = 3;
    private static final int BODY_W = 19, BODY_H = 19;
    // 充能格
    private static final int PIP_SZ = 18;
    private static final int PIP_OFF = (BODY_W - PIP_SZ) / 2; // 0
    // 源图标
    private static final int ICON_SZ = 20;
    private static final int ICON_GAP = 6;
    // 每行宽度由该条目的 max 动态决定（自由拼凑：max=3 画 3 格，max=6 画 6 格）。
    // 编辑器固定逻辑尺寸取一个上限（图标 + 上限格数），不随运行时 max 抖动。
    private static final int EDIT_MAX_SLOTS = 8;
    private static final int FRAME_INNER_H = BODY_H;
    private static final int FRAME_H = FR_T + FRAME_INNER_H + FR_B;
    private static final int ROW_H = Math.max(ICON_SZ, FRAME_H);
    private static final int ROW_GAP = 4;
    // 预留行数（编辑器固定尺寸用）
    private static final int MAX_ROWS = 3;
    private static final int TOTAL_W = ICON_SZ + ICON_GAP + FR_L + EDIT_MAX_SLOTS * BODY_W + FR_R;
    private static final int TOTAL_H = MAX_ROWS * ROW_H + (MAX_ROWS - 1) * ROW_GAP;

    public ChargeHud() {
        super("charge_bar", 200, 300, 1.0f);
    }

    // 充能 HUD 条目由各效果自行向 ChargeHudRegistry 注册（如天雷引在内容项目注册）。
    // PassiveChargeTracker 的旧满6逻辑已废弃，不再注册 HUD 条目。

    @Override public int getLogicalWidth()  { return TOTAL_W; }
    @Override public int getLogicalHeight() { return TOTAL_H; }

    @Override
    public void render(GuiGraphics g, boolean editMode) {
        Minecraft mc = Minecraft.getInstance();
        var displays = ChargeHudRegistry.collect(mc.player, editMode);
        if (!editMode && displays.isEmpty()) return;

        // editMode 没有条目时画一个占位行
        if (editMode && displays.isEmpty()) {
            drawRow(g, 0, ItemStack.EMPTY, 0, 6, false);
            return;
        }

        int row = 0;
        for (ChargeHudEntry.Display d : displays) {
            if (row >= MAX_ROWS) break; // 超过预留行数截断
            int y = row * (ROW_H + ROW_GAP);
            int max = d.max() > 0 ? d.max() : 6;
            drawRow(g, y, d.icon(), d.count(), max, d.highlight());
            row++;
        }
    }

    /** 渲染单行：图标 + max 数量的充能格框架，count 格填充。行宽度随 max 自由拼凑。 */
    private void drawRow(GuiGraphics g, int y, ItemStack icon, int count, int max, boolean highlight) {
        int slots = Math.max(1, max);
        int frameW = FR_L + slots * BODY_W + FR_R;
        int frameX = ICON_SZ + ICON_GAP;
        int frameY = y + (ROW_H - FRAME_H) / 2;

        // ── 源图标（行垂直居中）──
        if (!icon.isEmpty()) {
            int iconCy = y + ROW_H / 2;
            float scale = ICON_SZ / 16f;
            g.pose().pushPose();
            g.pose().translate(ICON_SZ / 2f, iconCy, 0);
            g.pose().scale(scale, scale, 1f);
            g.renderItem(icon, -8, -8);
            g.pose().popPose();
        }

        // ── 框架边框（按实际格数裁剪宽度）──
        int bodyX = frameX + FR_L;
        int bodyY = frameY + FR_T;
        int innerW = slots * BODY_W;
        g.blit(TEX_TOP, bodyX, frameY, innerW, FR_T, 0, 0, 1, 2, 1, 2);
        g.blit(TEX_BOTTOM, bodyX, frameY + FR_T + FRAME_INNER_H, innerW, FR_B, 0, 0, 1, 3, 1, 3);
        g.blit(TEX_LEFT, frameX, bodyY, FR_L, FRAME_INNER_H, 0, FR_T, FR_L, 24 - FR_T - FR_B, 3, 24);
        g.blit(TEX_RIGHT, frameX + FR_L + innerW, bodyY, FR_R, FRAME_INNER_H, 0, FR_T, FR_R, 24 - FR_T - FR_B, 3, 24);
        for (int i = 0; i < slots; i++) {
            g.blit(TEX_BODY, bodyX + i * BODY_W, bodyY, BODY_W, BODY_H, 0, 0, BODY_W, BODY_H, BODY_W, BODY_H);
        }

        // ── 充能格（count 格填充）──
        int fill = highlight ? slots : Math.min(count, slots);
        for (int i = 0; i < slots; i++) {
            int px = bodyX + i * BODY_W + PIP_OFF;
            int py = bodyY + PIP_OFF;
            boolean filled = i < fill;
            g.blit(filled ? TEX_FULL : TEX_EMPTY, px, py, 0, 0, PIP_SZ, PIP_SZ, PIP_SZ, PIP_SZ);
        }
    }
}
