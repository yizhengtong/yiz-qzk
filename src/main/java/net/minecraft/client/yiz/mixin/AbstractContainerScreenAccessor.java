package net.minecraft.client.yiz.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 AbstractContainerScreen 的 protected 字段，供准星右键 handler 清零 quick-craft 状态用。
 *
 * <p>vanilla 在 mouseClicked 成功点击后设 isQuickCrafting=true（开始拖拽选格）。
 * 下一次 mouseClicked 若 isQuickCrafting 仍为 true，会进入 quick-craft 结束分支——
 * 把物品分回 quickCraftSlots（上一槽位），而非正常放到新槽位 → "能拿起不能放下"。</p>
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("isQuickCrafting")
    void setQuickCrafting(boolean value);

    @Accessor("isQuickCrafting")
    boolean getQuickCrafting();

    @Accessor("skipNextRelease")
    void setSkipNextRelease(boolean value);

    @Accessor("leftPos")
    int getLeftPos();

    @Accessor("topPos")
    int getTopPos();

    @Accessor("imageWidth")
    int getImageWidth();

    @Accessor("imageHeight")
    int getImageHeight();
}
