package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.yiz.ui.ItemInfoUI;
import net.minecraft.client.yiz.ui.PlayerTalentUI;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.ShaderProtectionRegistry;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.client.yiz.impl.WorldContainerDataStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.lwjgl.glfw.GLFW;

@Mod(value = tizMod.MODID, dist = Dist.CLIENT)
public class tizModClient {

    public tizModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // Register client event handlers
        var modBus = container.getEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(ShaderProtectionRegistry::onRegisterShaders);
        modBus.addListener(ShaderManager::onRegisterShaders);
        modBus.addListener(this::onAtlasStitched);

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        tizMod.LOGGER.info("YizMod QZK Client initialized");

        // 注册着色器预设 (1=星芒, 2=图标贴图, 3=曲速穿越)
        ShaderManager.registerPreset("1", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic2", "rendertype_cosmic2_armor", true
        ));
        ShaderManager.registerPreset("2", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic3", "rendertype_cosmic3_armor", true
        ));
        ShaderManager.registerPreset("3", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic4", "rendertype_cosmic4_armor", true
        ));
        // z系列：70%透明黑底 + 星光铠甲（能看到皮肤）
        ShaderManager.registerPreset("z1", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic2", "rendertype_cosmic2_armor_z", true
        ));
        ShaderManager.registerPreset("z2", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic3", "rendertype_cosmic3_armor_z", true
        ));
        ShaderManager.registerPreset("z3", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic4", "rendertype_cosmic4_armor_z", true
        ));
        // z0: 纯取消盔甲渲染（调试用）
        ShaderManager.registerPreset("z0", new ShaderManager.ShaderDescriptor(
                tizMod.MODID, "rendertype_cosmic2", "rendertype_cosmic2_armor", true
        ));
    }

    /** 将 cosmic 图标注册到方块纹理图谱，供 cosmic3 着色器采样 */
    private void onAtlasStitched(TextureAtlasStitchedEvent event) {
        if (!event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) return;

        float[] uvs = new float[40];
        for (int i = 0; i < 10; i++) {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(tizMod.MODID, "cosmic/cosmic_" + i);
            TextureAtlasSprite sprite = event.getAtlas().getSprite(loc);
            uvs[i * 4]     = sprite.getU0();
            uvs[i * 4 + 1] = sprite.getV0();
            uvs[i * 4 + 2] = sprite.getU1();
            uvs[i * 4 + 3] = sprite.getV1();
        }
        ShaderManager.setCosmicUVs(uvs);
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
        }

        // CTRL + SHIFT: toggle talent UI
        if (ctrlHeld && UIConfig.isTalentUIKey(event.getKey(), event.getAction())) {
            UIConfig.toggleTalentUI();
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

        // 渲染锁定目标瞄准框
        net.minecraft.client.yiz.client.render.EntityLockRenderer.renderOverlay(
            event.getGuiGraphics().pose());

        // 清理悬停状态，下一帧如果没有 tooltip 事件就不再显示
        pendingItemStack = ItemStack.EMPTY;
    }

    // ══════════════════════════════════════════════════════════════════
    //  锁定目标图标渲染
    // ══════════════════════════════════════════════════════════════════

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

        WorldContainerDataStorage storage = serverLevel.getDataStorage()
            .computeIfAbsent(WorldContainerDataStorage.factory(), WorldContainerDataStorage.storageName());

        // 通知 ChestDataManager 切换活跃存储实例
        ChestDataManager.setActiveWorldStorage(storage);
    }

    /**
     * 世界保存时：将所有已注册容器的数据写入 DimensionDataStorage。
     * 仅处理主世界 overworld 的服务端保存事件。
     */
    @SubscribeEvent
    public void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        WorldContainerDataStorage storage = serverLevel.getDataStorage()
            .computeIfAbsent(WorldContainerDataStorage.factory(), WorldContainerDataStorage.storageName());
        storage.setDirty();
    }
}
