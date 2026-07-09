package net.minecraft.client.yiz.editor;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 属性编辑台方块的注册中心。
 *
 * <p>库模组的<b>第一个实体方块</b>，提供可视化 GUI 编辑物品的自定义属性。</p>
 * <p>注册命名空间 {@code yizmodqzk}，注册名 {@code attribute_editor}。</p>
 */
public final class AttributeEditorRegistries {

    private AttributeEditorRegistries() {}

    /** 方块 DeferredRegister。 */
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, "yizmodqzk");

    /** 物品 DeferredRegister（用于注册 BlockItem）。 */
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, "yizmodqzk");

    /** 创造标签页 DeferredRegister（库自己的专属标签页）。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "yizmodqzk");

    /** BlockEntity 类型 DeferredRegister（阶段 B）。 */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "yizmodqzk");

    /** Menu 类型 DeferredRegister（阶段 B）。 */
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, "yizmodqzk");

    // ── 方块 ──────────────────────────────────────────────────

    /** 属性编辑台方块：石质、金属声、不可燃，贴图含透明（cutout）需 noOcclusion。 */
    public static final DeferredHolder<Block, AttributeEditorBlock> ATTRIBUTE_EDITOR_BLOCK =
        BLOCKS.register("attribute_editor",
            () -> new AttributeEditorBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5f)
                .sound(SoundType.METAL)
                .noOcclusion()
                .requiresCorrectToolForDrops()));

    /** 对应的 BlockItem（让方块能进入创造栏/可 give）。 */
    public static final DeferredHolder<Item, BlockItem> ATTRIBUTE_EDITOR_ITEM =
        ITEMS.register("attribute_editor",
            () -> new BlockItem(ATTRIBUTE_EDITOR_BLOCK.get(), new Item.Properties()));

    // ── 创造标签页 ────────────────────────────────────────────

    /** 工作方块标签页（标题走语言文件 itemGroup.yizmodqzk.workbench，图标=属性编辑台）。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WORKBENCH_TAB =
        CREATIVE_TABS.register("workbench",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.yizmodqzk.workbench"))
                .icon(() -> new ItemStack(ATTRIBUTE_EDITOR_ITEM.get()))
                .displayItems((params, output) -> output.accept(ATTRIBUTE_EDITOR_ITEM.get()))
                .build());

    // ── BlockEntity 类型（阶段 B） ────────────────────────────

    /** 属性编辑台 BlockEntity 类型。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AttributeEditorBlockEntity>>
        ATTRIBUTE_EDITOR_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("attribute_editor",
                () -> new BlockEntityType<>(
                    AttributeEditorBlockEntity::new,
                    java.util.Set.of(ATTRIBUTE_EDITOR_BLOCK.get()),
                    null));

    // ── Menu 类型（阶段 B） ───────────────────────────────────

    /** 属性编辑台 Menu 类型（客户端构造走 createClientMenu）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<AttributeEditorMenu>>
        ATTRIBUTE_EDITOR_MENU =
            MENUS.register("attribute_editor",
                () -> new MenuType<>(AttributeEditorMenu::createClientMenu, FeatureFlags.DEFAULT_FLAGS));

    // ── 注册入口 ──────────────────────────────────────────────

    /** 在 modEventBus 上注册全部 DeferredRegister。由 tizMod 构造器调用。 */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
    }
}
