package net.minecraft.client.yiz.editor;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 技能配置 GUI 的注册中心。
 *
 * <p>提供技能配置界面所需的 {@link MenuType}。命名空间 {@code yizmodqzk}。</p>
 */
public final class SkillConfigRegistries {

    private SkillConfigRegistries() {}

    /** Menu 类型 DeferredRegister。 */
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, "yizmodqzk");

    /** 技能配置 Menu 类型（客户端构造走 createClientMenu）。 */
    public static final DeferredHolder<MenuType<?>, MenuType<SkillConfigMenu>>
        SKILL_CONFIG_MENU =
            MENUS.register("skill_config",
                () -> new MenuType<>(SkillConfigMenu::createClientMenu, FeatureFlags.DEFAULT_FLAGS));

    /** 在 modEventBus 上注册 Menu 类型。由 tizMod 构造器调用。 */
    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
