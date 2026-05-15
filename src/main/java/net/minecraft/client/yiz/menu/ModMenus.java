package net.minecraft.client.yiz.menu;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, tizMod.MODID);

    public static final Supplier<MenuType<HighSmithMenu>> HIGH_SMITH =
            MENUS.register("high_smith", () -> new MenuType<>(
                    HighSmithMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
