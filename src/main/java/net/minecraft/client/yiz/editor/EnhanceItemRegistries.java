package net.minecraft.client.yiz.editor;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 技能强化物品 + 创造标签页注册中心。
 */
public final class EnhanceItemRegistries {

    private EnhanceItemRegistries() {}

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, "yizmodqzk");

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "yizmodqzk");

    // ── 强化物品 ──────────────────────────────────────────────

    public static final DeferredHolder<Item, Item> BENLEIXI =
        ITEMS.register("benleixi", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> GANDIAN =
        ITEMS.register("gandian", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LEISHEN =
        ITEMS.register("leishen", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LEIXIAOSHAN =
        ITEMS.register("leixiaoshan", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> LEIZHENQIANLI =
        ITEMS.register("leizhenqianli", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MINGYINZHAOJIA =
        ITEMS.register("pozhenjinshen", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> PILI =
        ITEMS.register("pili", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> WUYINGJI =
        ITEMS.register("wuyingji", () -> new Item(new Item.Properties()));

    // 将 8 个强化物品注册到 SkillEnhanceConfig（供技能槽位映射查询）
    static {
        SkillEnhanceConfig.registerItem("benleixi", BENLEIXI::get);
        SkillEnhanceConfig.registerItem("gandian", GANDIAN::get);
        SkillEnhanceConfig.registerItem("leishen", LEISHEN::get);
        SkillEnhanceConfig.registerItem("leixiaoshan", LEIXIAOSHAN::get);
        SkillEnhanceConfig.registerItem("leizhenqianli", LEIZHENQIANLI::get);
        SkillEnhanceConfig.registerItem("pozhenjinshen", MINGYINZHAOJIA::get);
        SkillEnhanceConfig.registerItem("pili", PILI::get);
        SkillEnhanceConfig.registerItem("wuyingji", WUYINGJI::get);
    }

    // ── 创造标签页 ────────────────────────────────────────────

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ENHANCE_TAB =
        CREATIVE_TABS.register("skill_enhance",
            () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.yizmodqzk.skill_enhance"))
                .icon(() -> new ItemStack(BENLEIXI.get()))
                .displayItems((params, output) -> {
                    output.accept(BENLEIXI.get());
                    output.accept(GANDIAN.get());
                    output.accept(LEISHEN.get());
                    output.accept(LEIXIAOSHAN.get());
                    output.accept(LEIZHENQIANLI.get());
                    output.accept(MINGYINZHAOJIA.get());
                    output.accept(PILI.get());
                    output.accept(WUYINGJI.get());
                })
                .build());

    // ── 注册入口 ──────────────────────────────────────────────

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
