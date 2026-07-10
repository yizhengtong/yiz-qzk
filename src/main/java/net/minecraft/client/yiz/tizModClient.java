package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.ShaderProtectionRegistry;
import net.minecraft.client.yiz.ui.UIConfig;
import net.minecraft.client.yiz.impl.WorldContainerDataStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
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

        // 注册属性编辑台 Screen（阶段 B）
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, event ->
            event.register(
                net.minecraft.client.yiz.editor.AttributeEditorRegistries.ATTRIBUTE_EDITOR_MENU.get(),
                net.minecraft.client.yiz.editor.AttributeEditorScreen::new));

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);

        // 重生界面：添加"快速重生（30秒无敌）"按钮
        NeoForge.EVENT_BUS.addListener(this::onDeathScreenInit);

        // 自定义属性 tooltip：蓝色格式置顶显示
        NeoForge.EVENT_BUS.addListener(this::onItemTooltip);
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
        event.register(UIConfig.getTogglePanelFixKey());
        event.register(UIConfig.getTogglePanelKeyboardKey());
        event.register(UIConfig.getToggleAbolishPanelKey());
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

        // CTRL + C: 切换面板固定/跟随
        if (ctrlHeld && UIConfig.isPanelFixKey(event.getKey(), event.getAction())) {
            net.minecraft.client.yiz.client.render.HandheldPanelRenderer.toggleFixCurrent();
        }

        // F7: 打开物品废除面板（单独键，不需要 CTRL，避免与复述功能 CTRL+B 冲突）
        if (UIConfig.isAbolishPanelKey(event.getKey(), event.getAction())) {
            mc.setScreen(new net.minecraft.client.yiz.ui.AbolishPanelScreen());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  锁定目标图标渲染
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        net.minecraft.client.yiz.client.render.EntityLockRenderer.onRenderLevelStage(event);
        net.minecraft.client.yiz.client.render.HandheldPanelRenderer.onRenderLevelStage(event);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Phase 2.3：FIXED 面板交互（视角冻结 + 鼠标转发）
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.yiz.client.render.PanelInteractionManager.onClientTick();
    }

    @SubscribeEvent
    public void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        boolean down = event.getAction() == GLFW.GLFW_PRESS;
        if (net.minecraft.client.yiz.client.render.PanelInteractionManager.onMouseButton(event.getButton(), down)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (net.minecraft.client.yiz.client.render.PanelInteractionManager.onMouseScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
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

    // ══════════════════════════════════════════════════════════════
    //  原版属性 tooltip 屏蔽 → 自定义蓝色格式替换
    // ══════════════════════════════════════════════════════════════

    /**
     * 自定义属性 tooltip：从 ATTRIBUTE_MODIFIERS 组件直接读取，
     * 追加蓝色格式化行（不受 showInTooltip / SkipAll 影响）。
     */
    @SubscribeEvent
    public void onItemTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        var attrs = net.minecraft.client.yiz.ui.ItemAttributeDisplay.getAvailableAttributes(event.getItemStack());
        if (attrs.isEmpty()) return;

        var lines = event.getToolTip();

        // 清理上次追加的自定义行（避免重复叠加）
        var attrNames = attrs.stream().map(a -> a.name()).collect(java.util.stream.Collectors.toSet());
        var it = lines.iterator();
        while (it.hasNext()) {
            String text = it.next().getString().trim();
            if (attrNames.stream().anyMatch(n -> text.startsWith(n + "：") || text.startsWith(n + ":"))) {
                it.remove();
            }
        }

        // 置顶蓝色属性块（插入在物品名称下方，防止长 tooltip 被挤出屏幕）
        int insertAt = 1; // 紧跟物品名称（index 0）
        lines.add(insertAt++, net.minecraft.network.chat.Component.literal("在装备时：")
            .withStyle(net.minecraft.ChatFormatting.GRAY));
        for (var attr : attrs) {
            var line = net.minecraft.network.chat.Component.literal("  " + attr.name() + "：");
            line.append(net.minecraft.network.chat.Component.literal(attr.value())
                .withStyle(net.minecraft.ChatFormatting.BLUE));
            lines.add(insertAt++, line);
        }
        // 属性块与后面内容之间加一个空行
        lines.add(insertAt, net.minecraft.network.chat.Component.empty());
    }

    // ══════════════════════════════════════════════════════════════
    //  死亡界面「快速重生（30秒无敌）」按钮
    // ══════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onDeathScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.DeathScreen)) return;

        var mc = Minecraft.getInstance();
        event.addListener(
            net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.literal("⚡ 快速重生（30秒无敌）"),
                btn -> {
                    if (mc.player != null) {
                        mc.player.respawn();  // 原版重生流程
                        net.minecraft.client.yiz.network.C2SFastRespawnPayload.send();  // 告诉服务端给 30s 保护
                    }
                }
            )
            .bounds(event.getScreen().width / 2 - 100, event.getScreen().height / 4 + 120, 200, 20)
            .build()
        );
    }
}
