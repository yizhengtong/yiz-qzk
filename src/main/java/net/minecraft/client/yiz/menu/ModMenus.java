package net.minecraft.client.yiz.menu;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(BuiltInRegistries.MENU, tizMod.MODID);

    public static final Supplier<MenuType<TestChestMenu>> TEST_CHEST =
        MENUS.register("test_chest", () -> new MenuType<>(TestChestMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
