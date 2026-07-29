package net.minecraft.client.yiz;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 组合容器：把箱子（左）+ 工作台（右）合并为一个统一 Menu。
 * 服务端 / 客户端共用。
 */
public class CombinedContainerMenu extends AbstractContainerMenu {

    public static final MenuType<CombinedContainerMenu> TYPE =
            new MenuType<>(CombinedContainerMenu::createClientMenu, FeatureFlags.DEFAULT_FLAGS);

    private final Container chestContainer;
    private final CraftingContainer craftContainer;
    private final ResultContainer resultContainer;
    private final Level level;
    private final Player player;

    // ── 服务端构造 ──
    public CombinedContainerMenu(int containerId, Inventory playerInv, Container chestContainer, Level level) {
        super(TYPE, containerId);
        this.chestContainer = chestContainer;
        this.level = level;
        this.player = playerInv.player;
        this.craftContainer = new TransientCraftingContainer(this, 3, 3);
        this.resultContainer = new ResultContainer();

        chestContainer.startOpen(playerInv.player);
        buildSlots(playerInv);
    }

    // ── 客户端构造 ──
    public CombinedContainerMenu(int containerId, Inventory playerInv) {
        super(TYPE, containerId);
        this.chestContainer = new SimpleContainer(27);
        this.level = null;
        this.player = playerInv.player;
        this.craftContainer = new TransientCraftingContainer(this, 3, 3);
        this.resultContainer = new ResultContainer();
        buildSlots(playerInv);
    }

    private void buildSlots(Inventory playerInv) {
        int leftOffset = 0;       // 箱子区域起点
        int rightOffset = 176;    // 工作台区域起点（偏移 176px）

        // 箱子槽 (0-26)：9×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(chestContainer, row * 9 + col,
                        leftOffset + 8 + col * 18, 18 + row * 18));
            }
        }
        // 合成格 (27-35)：3×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.addSlot(new Slot(craftContainer, row * 3 + col,
                        rightOffset + 30 + col * 18, 17 + row * 18));
            }
        }
        // 合成结果 (36)
        this.addSlot(new ResultSlot(player, craftContainer, resultContainer, 36,
                rightOffset + 124, 35));
        // 玩家背包 (37-63)：9×3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, 9 + row * 9 + col,
                        8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏 (64-72)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    // ── 合成逻辑 ──
    @Override
    public void slotsChanged(Container container) {
        if (level != null && !level.isClientSide) {
            CraftingInput input = craftContainer.asCraftInput();
            var recipe = level.getServer()
                    .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            recipe.ifPresentOrElse(
                    r -> resultContainer.setRecipeUsed(r),
                    () -> resultContainer.setRecipeUsed(null));
        }
        super.slotsChanged(container);
    }

    // ── Shift+点击 ──
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index == 36) {
            // 合成结果 → 尝试放玩家背包，失败了放箱子
            if (!this.moveItemStackTo(stack, 37, 73, true)) {
                if (!this.moveItemStackTo(stack, 0, 27, false)) return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, result);
        } else if (index >= 0 && index < 27) {
            // 箱子区域 → 玩家背包
            if (!this.moveItemStackTo(stack, 37, 73, true)) return ItemStack.EMPTY;
        } else if (index >= 27 && index < 37) {
            // 合成格/结果 → 玩家背包
            if (!this.moveItemStackTo(stack, 37, 73, true)) return ItemStack.EMPTY;
        } else {
            // 玩家背包 → 优先箱子，否则合成格
            if (!this.moveItemStackTo(stack, 0, 27, false)) {
                if (!this.moveItemStackTo(stack, 27, 36, false)) return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == result.getCount()) return ItemStack.EMPTY;

        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return chestContainer.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        chestContainer.stopOpen(player);
        resultContainer.stopOpen(player);
    }

    // ── 客户端工厂 ──
    public static CombinedContainerMenu createClientMenu(int containerId, Inventory playerInv) {
        return new CombinedContainerMenu(containerId, playerInv);
    }
}
