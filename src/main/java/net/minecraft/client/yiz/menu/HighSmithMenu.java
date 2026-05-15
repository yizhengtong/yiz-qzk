package net.minecraft.client.yiz.menu;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 高阶锻造台容器菜单。
 *
 * <p>3 个材料槽 + 1 个结果预览槽。
 * 材料 1（主手武器）吸收材料 2、3 的附魔和属性。</p>
 */
public class HighSmithMenu extends AbstractContainerMenu {

    /** 静态持久容器引用（临时绕过 ChestDataManager，测试通过后迁回） */
    private static Container sharedContainer = new SimpleContainer(4);

    private final Container dataContainer;
    private final ContainerLevelAccess access;

    // ─────────────────────── 客户端 / 服务端共用构造函数 ───────────────────────

    public HighSmithMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, sharedContainer);
    }

    // ─────────────────────── 显式容器构造函数 ───────────────────────

    public HighSmithMenu(int containerId, Inventory playerInventory, Container container) {
        this(containerId, playerInventory, container, ContainerLevelAccess.NULL);
    }

    public HighSmithMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(ModMenus.HIGH_SMITH.get(), containerId);
        this.dataContainer = container;
        this.access = access;

        tizMod.LOGGER.info("HighSmithMenu 创建，containerId={}, container={}", containerId, container);

        // ══════ 4 个锻造槽位（y=45，与 Screen 的 SLOT_ROW_Y 对齐） ══════

        // Slot 0 - Base item (列 0)
        addSlot(new Slot(container, 0, 5 + 0 * 18, 45) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(container);
            }
        });
        // Slot 1 - Material 1 (列 2)
        addSlot(new Slot(container, 1, 5 + 2 * 18, 45) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(container);
            }
        });
        // Slot 2 - Material 2 (列 4)
        addSlot(new Slot(container, 2, 5 + 4 * 18, 45) {
            @Override
            public void setChanged() {
                super.setChanged();
                slotsChanged(container);
            }
        });
        // Slot 3 - Result output only (列 7)
        addSlot(new Slot(container, 3, 5 + 7 * 18, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public void onTake(Player player, ItemStack stack) {
                // 消耗材料
                container.setItem(0, ItemStack.EMPTY);
                container.setItem(1, ItemStack.EMPTY);
                container.setItem(2, ItemStack.EMPTY);
                recalculateResult();
                super.onTake(player, stack);
            }
        });

        // ══════ 玩家背包 36 格（y=92，与 Screen 的 PLAYER_Y 对齐） ══════

        int playerInvY = 92; // = SPLIT_Y(78) + 14

        // 物品栏 27 格（3×9）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        5 + col * 18, playerInvY + row * 18));
            }
        }
        // 快捷栏 9 格（p_slots_9x4 纹理中第 4 行起始于顶部 58px 处）
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col,
                    5 + col * 18, playerInvY + 58));
        }

    }

    // ════════════════════════════════════════════════════════════════
    //  核心逻辑：附魔 + 属性吸收
    // ════════════════════════════════════════════════════════════════

    @Override
    public void slotsChanged(Container container) {
        recalculateResult();
        super.slotsChanged(container);
    }

    private void recalculateResult() {
        ItemStack base = dataContainer.getItem(0);
        ItemStack mat1 = dataContainer.getItem(1);
        ItemStack mat2 = dataContainer.getItem(2);

        if (base.isEmpty()) {
            dataContainer.setItem(3, ItemStack.EMPTY);
            return;
        }

        ItemStack result = base.copy();

        // 吸收 mat1 的附魔
        absorbEnchantments(result, mat1);
        // 吸收 mat2 的附魔
        absorbEnchantments(result, mat2);

        // 吸收属性（攻击力、攻击速度）
        absorbAttributeModifier(result, mat1);
        absorbAttributeModifier(result, mat2);

        dataContainer.setItem(3, result);
    }

    /**
     * 将 source 的附魔合并到 target（重复附魔取最高级）。
     */
    private void absorbEnchantments(ItemStack target, ItemStack source) {
        if (source.isEmpty()) return;

        ItemEnchantments srcEnch = source.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (srcEnch.isEmpty()) return;

        ItemEnchantments tgtEnch = target.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

        var mutable = new ItemEnchantments.Mutable(tgtEnch);
        for (var entry : srcEnch.entrySet()) {
            Holder<Enchantment> ench = entry.getKey();
            int srcLevel = entry.getIntValue();
            int tgtLevel = mutable.getLevel(ench);
            if (srcLevel > tgtLevel) {
                mutable.set(ench, srcLevel);
            }
        }
        target.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }

    /**
     * 将 source 的攻击力、攻击速度属性修正合并到 target。
     */
    private void absorbAttributeModifier(ItemStack target, ItemStack source) {
        if (source.isEmpty()) return;

        var srcModifiers = source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        var srcEntries = srcModifiers.modifiers();
        if (srcEntries.isEmpty()) return;

        var tgtModifiers = target.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();

        // 保留 target 原有的属性修正
        for (var entry : tgtModifiers.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        // 从 source 吸收攻击力、攻击速度、护甲、护甲韧性
        for (var entry : srcEntries) {
            var attr = entry.attribute();
            if (attr == Attributes.ATTACK_DAMAGE
                    || attr == Attributes.ATTACK_SPEED
                    || attr == Attributes.ARMOR
                    || attr == Attributes.ARMOR_TOUGHNESS) {
                builder.add(entry.attribute(), entry.modifier(), entry.slot());
            }
        }
        target.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    // ════════════════════════════════════════════════════════════════
    //  标准接口
    // ════════════════════════════════════════════════════════════════

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);

        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();

            // 结果槽（3）→ 玩家背包
            if (slotIndex == 3) {
                if (!moveItemStackTo(slotStack, 4, 40, true)) return ItemStack.EMPTY;
                slot.onTake(player, slotStack);
            }
            // 材料槽（0-2）→ 玩家背包
            else if (slotIndex < 3) {
                if (!moveItemStackTo(slotStack, 4, 40, false)) return ItemStack.EMPTY;
            }
            // 玩家背包 → 材料槽
            else {
                if (!moveItemStackTo(slotStack, 0, 3, false)) return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
