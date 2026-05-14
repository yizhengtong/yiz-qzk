package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.yiz.menu.DynamicChestMenu;
import net.minecraft.client.yiz.menu.DynamicChestScreen;
import net.minecraft.client.yiz.menu.ModMenus;
import net.minecraft.client.yiz.ui.ItemInfoUI;
import net.minecraft.client.yiz.ui.PlayerTalentUI;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = tizMod.MODID, dist = Dist.CLIENT)
public class tizModClient {

    // 4 个演示容器的持久实例（由 ChestDataManager 自动存档）
    private static final SimpleContainer CHEST_75 = new SimpleContainer(75);
    private static final SimpleContainer CHEST_115 = new SimpleContainer(115);
    private static final SimpleContainer CHEST_130 = new SimpleContainer(130);
    private static final SimpleContainer CHEST_200 = new SimpleContainer(200);

    public tizModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Register client event handlers
        var modBus = container.getEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::onRegisterMenuScreens);

        // 注册持久容器到世界存档系统
        ChestDataManager.register("yizmodqzk:chest_75", CHEST_75);
        ChestDataManager.register("yizmodqzk:chest_115", CHEST_115);
        ChestDataManager.register("yizmodqzk:chest_130", CHEST_130);
        ChestDataManager.register("yizmodqzk:chest_200", CHEST_200);

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        tizMod.LOGGER.info("YizMod QZK Client initialized");
    }

    /**
     * Register key mappings so they appear in Controls settings and work properly.
     */
    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(UIConfig.getToggleItemUIKey());
        event.register(UIConfig.getToggleTalentUIKey());
        event.register(UIConfig.getChest75Key());
        event.register(UIConfig.getChest115Key());
        event.register(UIConfig.getChest130Key());
        event.register(UIConfig.getChest200Key());
    }

    /**
     * 绑定 4 个变列容器的 Screen。
     */
    private void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHEST_75.get(), DynamicChestScreen::new);
        event.register(ModMenus.CHEST_115.get(), DynamicChestScreen::new);
        event.register(ModMenus.CHEST_130.get(), DynamicChestScreen::new);
        event.register(ModMenus.CHEST_200.get(), DynamicChestScreen::new);
    }

    /**
     * Handle key input for UI toggles.
     * Uses edge-triggered detection (press only, not hold) to avoid repeated toggling.
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean ctrlHeld = Screen.hasControlDown();

        // CTRL + ALT: toggle item UI
        if (ctrlHeld && UIConfig.isItemUIKey(event.getKey(), event.getAction())) {
            UIConfig.toggleItemUI();
            return;
        }

        // CTRL + SHIFT: toggle talent UI
        if (ctrlHeld && UIConfig.isTalentUIKey(event.getKey(), event.getAction())) {
            UIConfig.toggleTalentUI();
            return;
        }

        // ── 演示容器快捷键（单键无修饰符） ──
        // 仅在无屏幕或当前屏幕为此容器时才触发，避免干扰文字输入
        if (mc.screen != null && !(mc.screen instanceof DynamicChestScreen)) return;

        int k = event.getKey();
        if (k == UIConfig.getChest75Key().getKey().getValue()) {
            if (mc.screen instanceof DynamicChestScreen s && s.hasSlotCount(75)) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                openChest(ModMenus.CHEST_75.get(), 75, CHEST_75);
            }
        } else if (k == UIConfig.getChest115Key().getKey().getValue()) {
            if (mc.screen instanceof DynamicChestScreen s && s.hasSlotCount(115)) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                openChest(ModMenus.CHEST_115.get(), 115, CHEST_115);
            }
        } else if (k == UIConfig.getChest130Key().getKey().getValue()) {
            if (mc.screen instanceof DynamicChestScreen s && s.hasSlotCount(130)) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                openChest(ModMenus.CHEST_130.get(), 130, CHEST_130);
            }
        } else if (k == UIConfig.getChest200Key().getKey().getValue()) {
            if (mc.screen instanceof DynamicChestScreen s && s.hasSlotCount(200)) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                openChest(ModMenus.CHEST_200.get(), 200, CHEST_200);
            }
        }
    }

    /**
     * 通过单机集成服务端打开容器，获得有效 containerId。
     */
    private static void openChest(MenuType<DynamicChestMenu> type, int slots, SimpleContainer container) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                sp.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new DynamicChestMenu(type, id, inv, slots, container),
                    Component.literal("Chest " + slots)
                ));
            }
        }
    }

    // 暂存悬停物品信息，用于在 ScreenEvent.Render.Post 中渲染（保证在最上层）
    private static ItemStack pendingItemStack = ItemStack.EMPTY;
    private static int pendingItemX = 0;
    private static int pendingItemY = 0;

    /**
     * Cancel vanilla tooltip and store hovered item info.
     * Actual rendering happens in onScreenRender (LOWEST) to stay on top of all GUI elements.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTooltipPre(RenderTooltipEvent.Pre event) {
        if (!UIConfig.isCustomItemUIEnabled()) return;
        if (event.getItemStack().isEmpty()) return;

        // 取消原版物品提示
        event.setCanceled(true);

        // 暂存物品信息，由 onScreenRender 渲染
        pendingItemStack = event.getItemStack();
        pendingItemX = event.getX();
        pendingItemY = event.getY();
    }

    /**
     * Render UI overlays on top of everything.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 渲染自定义物品信息（在最上层，不被GUI元素遮挡）
        if (UIConfig.isCustomItemUIEnabled() && !pendingItemStack.isEmpty()) {
            ItemInfoUI.renderItemInfo(event.getGuiGraphics(), pendingItemStack,
                pendingItemX, pendingItemY);
        }

        // 渲染天赋面板
        if (PlayerTalentUI.shouldShow(mc)) {
            PlayerTalentUI.renderTalentUI(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
        }

        // 清理悬停状态，下一帧如果没有 tooltip 事件就不再显示
        pendingItemStack = ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════
    //  容器数据持久化事件挂钩（通过 ChestDataManager 管理）
    // ══════════════════════════════════════════════════════════════════

    /**
     * 世界加载时：从 DimensionDataStorage 恢复所有已注册容器的数据。
     * 仅处理主世界 overworld 的服务端加载事件。
     */
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        serverLevel.getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(ChestSavedData::new, ChestSavedData::load), ChestSavedData.NAME);
    }

    /**
     * 世界保存时：将所有已注册容器的数据写入 DimensionDataStorage。
     * 仅处理主世界 overworld 的服务端保存事件。
     */
    @SubscribeEvent
    public void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        ChestSavedData data = serverLevel.getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(ChestSavedData::new, ChestSavedData::load), ChestSavedData.NAME);
        data.setDirty();
    }
}
