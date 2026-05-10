package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.yiz.ui.ItemInfoUI;
import net.minecraft.client.yiz.ui.PlayerTalentUI;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.client.yiz.test.TestSetup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = tizMod.MODID, dist = Dist.CLIENT)
public class tizModClient {

    public tizModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Register client event handlers
        var modBus = container.getEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        tizMod.LOGGER.info("YizMod QZK Client initialized");

        // 初始化测试内容（注册测试效果 + 事件钩子）
        TestSetup.init();
    }

    /**
     * Register key mappings so they appear in Controls settings and work properly.
     */
    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(UIConfig.getToggleItemUIKey());
        event.register(UIConfig.getToggleTalentUIKey());
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
            mc.player.displayClientMessage(Component.literal(
                UIConfig.isCustomItemUIEnabled()
                    ? "§a自定义物品UI已开启"
                    : "§c自定义物品UI已关闭"
            ), true);
        }

        // CTRL + SHIFT: toggle talent UI
        if (ctrlHeld && UIConfig.isTalentUIKey(event.getKey(), event.getAction())) {
            UIConfig.toggleTalentUI();
            mc.player.displayClientMessage(Component.literal(
                UIConfig.isPlayerTalentUIEnabled()
                    ? "§a天赋信息UI已开启"
                    : "§c天赋信息UI已关闭"
            ), true);
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
}
