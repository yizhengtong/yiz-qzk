package net.minecraft.client.yiz.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 4 个变列容器菜单的 MenuType 注册。
 *
 * <p>每个 MenuType 使用数组引用捕获模式解决鸡生蛋问题：
 * 先分配 {@code MenuType[1]} 数组，lambda 捕获数组引用（始终有效），
 * 再将构建好的 MenuType 存入 {@code ref[0]}，{@code openMenu} 实际调用时 {@code ref[0]} 已就绪。
 */
public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, "yizmodqzk");

    public static final Supplier<MenuType<DynamicChestMenu>> CHEST_75 = MENUS.register("chest_75", () -> {
        MenuType<DynamicChestMenu>[] ref = new MenuType[1];
        MenuType<DynamicChestMenu> type = new MenuType<>(
            (id, inv) -> new DynamicChestMenu(ref[0], id, inv, 75, new SimpleContainer(75)),
            FeatureFlags.DEFAULT_FLAGS
        );
        ref[0] = type;
        return type;
    });

    public static final Supplier<MenuType<DynamicChestMenu>> CHEST_115 = MENUS.register("chest_115", () -> {
        MenuType<DynamicChestMenu>[] ref = new MenuType[1];
        MenuType<DynamicChestMenu> type = new MenuType<>(
            (id, inv) -> new DynamicChestMenu(ref[0], id, inv, 115, new SimpleContainer(115)),
            FeatureFlags.DEFAULT_FLAGS
        );
        ref[0] = type;
        return type;
    });

    public static final Supplier<MenuType<DynamicChestMenu>> CHEST_130 = MENUS.register("chest_130", () -> {
        MenuType<DynamicChestMenu>[] ref = new MenuType[1];
        MenuType<DynamicChestMenu> type = new MenuType<>(
            (id, inv) -> new DynamicChestMenu(ref[0], id, inv, 130, new SimpleContainer(130)),
            FeatureFlags.DEFAULT_FLAGS
        );
        ref[0] = type;
        return type;
    });

    public static final Supplier<MenuType<DynamicChestMenu>> CHEST_200 = MENUS.register("chest_200", () -> {
        MenuType<DynamicChestMenu>[] ref = new MenuType[1];
        MenuType<DynamicChestMenu> type = new MenuType<>(
            (id, inv) -> new DynamicChestMenu(ref[0], id, inv, 200, new SimpleContainer(200)),
            FeatureFlags.DEFAULT_FLAGS
        );
        ref[0] = type;
        return type;
    });
}
