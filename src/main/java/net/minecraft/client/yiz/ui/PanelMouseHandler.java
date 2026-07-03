package net.minecraft.client.yiz.ui;

import net.minecraft.client.yiz.api.ISkillWeapon;
import net.minecraft.world.item.ItemStack;

/**
 * 饰品槽物品判定（历史遗留类的精简残留）。
 *
 * <p><b>历史</b>：本类曾是纯客户端饰品面板的鼠标交互逻辑（{@code handleSlotClick}
 * 等），在客户端本地复刻原版 Slot 交互，不经过服务端验证。该旧系统已整体移除
 * （详见 {@link InventoryPanel} 的历史说明），存取改由 yizxianmod 的真 menu slot
 * + 服务器权威协议处理。</p>
 *
 * <p><b>现状</b>：仅保留 {@link #isSkillItem}，供 {@code YizModQZKAPI.isSkillItem}
 * 公开 API 使用。其余交互逻辑已删除。</p>
 */
public final class PanelMouseHandler {

    private PanelMouseHandler() {}

    /**
     * 判定物品是否可放入饰品槽位。
     * 只有实现了 {@link ISkillWeapon} 的物品才可放入。
     */
    public static boolean isSkillItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof ISkillWeapon;
    }
}
