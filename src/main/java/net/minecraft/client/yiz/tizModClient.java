package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.yiz.ui.ItemInfoUI;
import net.minecraft.client.yiz.ui.PlayerTalentUI;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.client.yiz.test.TestSetup;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
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

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        tizMod.LOGGER.info("YizMod QZK Client initialized");

        // 初始化测试内容（注册测试效果 + 事件钩子）
        TestSetup.init();
    }

    /**
     * Handle key input for UI toggles.
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // CTRL + ALT: toggle item UI
        if (UIConfig.checkItemUIToggle()) {
            UIConfig.toggleItemUI();
            mc.player.displayClientMessage(Component.literal(
                UIConfig.isCustomItemUIEnabled()
                    ? "§a自定义物品UI已开启"
                    : "§c自定义物品UI已关闭"
            ), true);
        }

        // CTRL + SHIFT: toggle talent UI
        if (UIConfig.checkTalentUIToggle()) {
            UIConfig.toggleTalentUI();
            mc.player.displayClientMessage(Component.literal(
                UIConfig.isPlayerTalentUIEnabled()
                    ? "§a天赋信息UI已开启"
                    : "§c天赋信息UI已关闭"
            ), true);
        }
    }

    /**
     * Render custom item info overlay.
     */
    @SubscribeEvent
    public void onRenderOverlay(RenderGuiEvent.Post event) {
        if (!UIConfig.isCustomItemUIEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // Don't render in GUIs

        // Get hovered item from screen if available
        // For now, this is a placeholder - full implementation needs screen event integration
    }

    /**
     * Render talent UI on inventory screen.
     */
    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        Minecraft mc = Minecraft.getInstance();

        if (PlayerTalentUI.shouldShow(mc)) {
            PlayerTalentUI.renderTalentUI(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
        }
    }
}
