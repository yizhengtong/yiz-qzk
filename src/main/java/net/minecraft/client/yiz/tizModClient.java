package net.minecraft.client.yiz;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.yiz.api.ShaderManager;
import net.minecraft.client.yiz.api.ShaderProtectionRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.yiz.handler.SkillKeyMappings;
import net.minecraft.client.yiz.hud.BlockHud;
import net.minecraft.client.yiz.hud.BuffHud;
import net.minecraft.client.yiz.hud.ChargeHud;
import net.minecraft.client.yiz.hud.HudEditorScreen;
import net.minecraft.client.yiz.hud.HudManager;
import net.minecraft.client.yiz.hud.HudPositionConfig;
import net.minecraft.client.yiz.hud.ManaHud;
import net.minecraft.client.yiz.hud.ShieldHud;
import net.minecraft.client.yiz.hud.SkillHud;
import net.minecraft.client.yiz.hud.SkillInfoHud;
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
        modBus.addListener(net.minecraft.client.yiz.lightning.render.LightningShaders::onRegisterShaders);
        modBus.addListener(this::onAtlasStitched);
        modBus.addListener(this::onRegisterTooltipComponents);

        // 注册属性编辑台 Screen（阶段 B）
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, event -> {
            event.register(
                net.minecraft.client.yiz.editor.AttributeEditorRegistries.ATTRIBUTE_EDITOR_MENU.get(),
                net.minecraft.client.yiz.editor.AttributeEditorScreen::new);
            event.register(
                net.minecraft.client.yiz.editor.SkillConfigRegistries.SKILL_CONFIG_MENU.get(),
                net.minecraft.client.yiz.editor.SkillConfigScreen::new);
        });

        // ═══ HUD 系统（管理能力由 yizmodqzk 提供） ═══
        HudPositionConfig.load();
        HudManager.register(new SkillHud());
        HudManager.register(new ChargeHud());
        HudManager.register(new ShieldHud());
        HudManager.register(new BlockHud());
        HudManager.register(new ManaHud());
        HudManager.register(new SkillInfoHud());
        HudManager.register(new BuffHud());
        NeoForge.EVENT_BUS.addListener(HudManager::onRenderGui);
        NeoForge.EVENT_BUS.addListener(this::onSkillKeyTick);

        // Register Forge event bus handlers
        NeoForge.EVENT_BUS.register(this);

        // 重生界面：添加"快速重生（30秒无敌）"按钮
        NeoForge.EVENT_BUS.addListener(this::onDeathScreenInit);

        // 自定义属性 tooltip：蓝色格式置顶显示
        NeoForge.EVENT_BUS.addListener(this::onItemTooltip);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        tizMod.LOGGER.info("YizMod QZK Client initialized");

        // 屏蔽本模组自定义属性在原版 tooltip 的属性行（顶部面板已统一显示，避免重复）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                net.minecraft.client.yiz.ui.VanillaAttributeTooltipHider.class);

        // 世界空间容器 GUI：箱子面板离屏渲染到 FBO + 世界四边形 + 准星命中
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                net.minecraft.client.yiz.client.render.WorldGuiPanelManager.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                net.minecraft.client.yiz.client.render.WorldGuiInputHandler.class);
        // 世界光屏准星右键操作（mc.screen==null 时，准星对准留存光屏右键操作槽位）
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(
                net.minecraft.client.yiz.client.render.WorldPanelInteractionHandler.class);

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
        event.register(UIConfig.getToggleAbolishPanelKey());
        SkillKeyMappings.registerAll(event);
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

        // [里程碑1 调试] F8：在视线前方 spawn 一条闪电电弧，验证渲染管线。后续接正式技能后移除。
        if (event.getKey() == GLFW.GLFW_KEY_F8) {
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(0f);
            net.minecraft.world.phys.Vec3 to = eye.add(mc.player.getLookAngle().scale(8.0));
            net.minecraft.client.yiz.lightning.LightningFX.spawnArc(eye, to);
        }

        // [火花] F9：一键开关火花系统（总开关），actionbar 回显
        if (event.getKey() == GLFW.GLFW_KEY_F9) {
            boolean now = net.minecraft.client.yiz.lightning.config.SparkConfig.toggleEnabled();
            mc.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§b[闪电] 火花系统：" + (now ? "§a开" : "§c关")),
                    true);
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
        net.minecraft.client.yiz.lightning.render.LightningRenderer.onRenderLevelStage(event);
    }

    // ══════════════════════════════════════════════════════════════════
    //  Phase 2.3：FIXED 面板交互（视角冻结 + 鼠标转发）
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.yiz.client.render.PanelInteractionManager.onClientTick();
        net.minecraft.client.yiz.lightning.render.LightningRenderer.tick();
        net.minecraft.client.yiz.api.ShockedEntityAPI.tick();
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
        var lines = event.getToolTip();

        // 先清理所有之前追加的自定义行
        cleanTooltip(lines, attrs, event.getItemStack());

        // 统一从 index=1 开始顺次插入：已觉醒 → 装备时
        int insertAt = 1;
        insertAt = insertAwakenedEffects(event.getItemStack(), lines, insertAt);
        if (!attrs.isEmpty()) {
            lines.add(insertAt++, net.minecraft.network.chat.Component.literal("在装备时：")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
            for (var attr : attrs) {
                // 属性行用纯文字 Component（图标由 GatherComponents 替换为 AttributeLineTooltipComponent 渲染）
                var line = net.minecraft.network.chat.Component.literal("  " + attr.name() + "：");
                line.append(net.minecraft.network.chat.Component.literal(attr.value())
                    .withStyle(net.minecraft.ChatFormatting.BLUE));
                lines.add(insertAt++, line);
            }
            lines.add(insertAt, net.minecraft.network.chat.Component.empty());
        }
    }

    /** 注册属性图标 tooltip 组件工厂（mod bus）。 */
    private void onRegisterTooltipComponents(
            net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(
            net.minecraft.client.yiz.ui.AttributeLineTooltipComponent.class,
            net.minecraft.client.yiz.ui.AttributeLineClientComponent::new);
    }

    /**
     * 把属性行 Component 替换为 AttributeLineTooltipComponent（图标+文字一行，行高自适应不溢出）。
     * GatherComponents 在 tooltip 渲染前触发，tooltipElements 是 Either&lt;FormattedText, TooltipComponent&gt; 列表。
     */
    @SubscribeEvent
    public void onGatherTooltip(net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event) {
        net.minecraft.world.item.ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        var elements = event.getTooltipElements();

        // 属性行替换
        var attrs = net.minecraft.client.yiz.ui.ItemAttributeDisplay.getAvailableAttributes(stack);
        if (attrs.isEmpty()) return;
        for (int i = 0; i < elements.size(); i++) {
            var either = elements.get(i);
            if (either.left().isEmpty()) continue;
            if (!(either.left().get() instanceof net.minecraft.network.chat.Component c)) continue;
            String txt = c.getString().trim();
            for (var attr : attrs) {
                if (txt.startsWith(attr.name() + "：") || txt.startsWith(attr.name() + ":")) {
                    elements.set(i, com.mojang.datafixers.util.Either.right(
                        new net.minecraft.client.yiz.ui.AttributeLineTooltipComponent(
                            attr.attrId(), attr.name(), attr.value(), attr.color())));
                    break;
                }
            }
        }
    }

    /** 清理之前追加的所有自定义行。 */
    private static void cleanTooltip(
            java.util.List<net.minecraft.network.chat.Component> lines,
            java.util.List<net.minecraft.client.yiz.ui.ItemAttributeDisplay.AttributeInfo> attrs,
            net.minecraft.world.item.ItemStack stack) {
        var entries = net.minecraft.client.yiz.editor.SkillEnhanceConfig.getEnhancementsFor(stack);
        var it = lines.iterator();
        while (it.hasNext()) {
            String txt = it.next().getString().trim();
            if (txt.isEmpty()) continue;
            if (txt.equals("在装备时：") || txt.equals("已觉醒效果：")) { it.remove(); continue; }
            // 属性行（纯文字"属性名："开头，图标改由 GatherComponents 组件渲染）
            if (!attrs.isEmpty()) {
                var attrNames = attrs.stream().map(a -> a.name()).collect(java.util.stream.Collectors.toSet());
                if (attrNames.stream().anyMatch(n -> txt.startsWith(n + "：") || txt.startsWith(n + ":"))) {
                    it.remove(); continue;
                }
            }
            // 觉醒名称行
            for (var e : entries) {
                if (txt.equals(e.displayName())) { it.remove(); break; }
            }
        }
    }

    /** 插入已觉醒效果行，返回下一个可用的 insertAt。 */
    private static int insertAwakenedEffects(net.minecraft.world.item.ItemStack stack,
                                             java.util.List<net.minecraft.network.chat.Component> lines,
                                             int insertAt) {
        if (!(stack.getItem() instanceof net.minecraft.client.yiz.api.ISkillItem)
            && !(stack.getItem() instanceof net.minecraft.client.yiz.api.IPassiveItem)) return insertAt;

        var entries = net.minecraft.client.yiz.editor.SkillEnhanceConfig.getEnhancementsFor(stack);
        if (entries.isEmpty()) return insertAt;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return insertAt;
        int[] levels = net.minecraft.client.yiz.editor.SkillConfigStorage.getEnhanceLevels(stack);

        boolean hasActive = false;
        for (int i = 0; i < Math.min(entries.size(), levels.length); i++)
            if (levels[i] > 0) { hasActive = true; break; }
        if (!hasActive) return insertAt;

        lines.add(insertAt++, net.minecraft.network.chat.Component.literal("已觉醒效果：")
            .withStyle(net.minecraft.ChatFormatting.BLUE));
        for (int i = 0; i < Math.min(entries.size(), levels.length); i++) {
            if (levels[i] > 0) {
                lines.add(insertAt++, net.minecraft.network.chat.Component.literal(entries.get(i).displayName())
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        return insertAt;
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

    // ══════════════════════════════════════════════════════════════
    //  [闪电特效] /yzarc 调试命令：生成持续 20 秒的固定电弧，便于绕着观察立体感
    // ══════════════════════════════════════════════════════════════

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("yzarc")
                        .executes(this::spawnTestArc)
                        .then(net.minecraft.commands.Commands.literal("clear").executes(this::clearArcs))
                        .then(net.minecraft.commands.Commands.literal("surface").executes(this::spawnSurfaceTest))
                        .then(net.minecraft.commands.Commands.literal("ball").executes(this::spawnBallTest))
                        .then(net.minecraft.commands.Commands.literal("shoot").executes(this::spawnShootTest))
                        .then(net.minecraft.commands.Commands.literal("sparktest").executes(this::sparkTest))
                        .then(net.minecraft.commands.Commands.literal("spark")
                                .then(net.minecraft.commands.Commands.literal("on").executes(this::sparkOn))
                                .then(net.minecraft.commands.Commands.literal("off").executes(this::sparkOff))
                                .then(net.minecraft.commands.Commands.literal("endpoint").executes(this::sparkToggleEndpoint))
                                .then(net.minecraft.commands.Commands.literal("hit").executes(this::sparkToggleHit))
                                .then(net.minecraft.commands.Commands.literal("surface").executes(this::sparkToggleSurface))
                                .executes(this::sparkStatus))
                        .then(net.minecraft.commands.Commands.literal("skill")
                                .then(net.minecraft.commands.Commands.literal("a").executes(this::skillA))
                                .then(net.minecraft.commands.Commands.literal("b").executes(this::skillB))
                                .then(net.minecraft.commands.Commands.literal("c").executes(this::skillC)))
        );
    }

    private int spawnTestArc(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        net.minecraft.world.entity.Entity ent = src.getEntity();
        if (ent == null) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 eye = ent.getEyePosition(1f);
        net.minecraft.world.phys.Vec3 to = eye.add(ent.getViewVector(1f).scale(8.0));
        net.minecraft.client.yiz.lightning.LightningFX.spawnArc(eye, to, 20f, 0.055f,
                net.minecraft.client.yiz.lightning.LightningFX.DEFAULT_R,
                net.minecraft.client.yiz.lightning.LightningFX.DEFAULT_G,
                net.minecraft.client.yiz.lightning.LightningFX.DEFAULT_B);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 已在视线前方生成 20 秒电弧，可绕着观察（/yzarc clear 清除）"), false);
        return 1;
    }

    private int clearArcs(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.client.yiz.lightning.render.LightningRenderer.clear();
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 已清除所有电弧"), false);
        return 1;
    }

    private int spawnSurfaceTest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        net.minecraft.world.entity.Entity ent = src.getEntity();
        if (!(ent instanceof net.minecraft.world.entity.player.Player player)) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        // 找准星前方最近的可拾取实体；找不到则对自己施加（便于观察）
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1f);
        net.minecraft.world.phys.Vec3 view = player.getViewVector(1f);
        net.minecraft.world.phys.AABB search = player.getBoundingBox().expandTowards(view.scale(16)).inflate(1.0);
        net.minecraft.world.entity.Entity target = player.level()
                .getEntities(player, search, e -> e.isAlive() && e.isPickable())
                .stream()
                .min(java.util.Comparator.comparingDouble(e -> e.getEyePosition().distanceToSqr(eye)))
                .orElse(null);
        if (target == null) target = player;
        net.minecraft.client.yiz.lightning.LightningFX.spawnSurfaceArc(target);
        net.minecraft.world.entity.Entity t = target;
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§b[闪电] 已对 " + t.getName().getString() + " 施加 20 秒表面缠绕电弧"), false);
        return 1;
    }

    private int spawnBallTest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        net.minecraft.world.entity.Entity ent = src.getEntity();
        if (!(ent instanceof net.minecraft.world.entity.player.Player player)) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        // 头顶上方 1 格 = FollowingEntity(玩家) + 偏移(0, 身高+1, 0)
        var headPos = net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier.offset(
                net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier.following(player),
                0, player.getBbHeight() + 1.0, 0);
        net.minecraft.client.yiz.lightning.LightningFX.spawnBall(headPos, 0.5f, 20f, true);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 已在头顶生成 20 秒球状闪电"), false);
        return 1;
    }

    private int spawnShootTest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        net.minecraft.world.entity.Entity ent = src.getEntity();
        if (!(ent instanceof net.minecraft.world.entity.player.Player player)) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        // 从眼睛朝视线方向发射飞行球，速度 20 格/s，寿命 2 秒（飞约 40 格）
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1f);
        net.minecraft.world.phys.Vec3 vel = player.getViewVector(1f).scale(8.0);
        var pos = net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier.flying(eye, vel);
        net.minecraft.client.yiz.lightning.LightningFX.spawnBall(pos, 1.0f, 2.0f, true);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 已发射球状闪电（8 格/秒，2 秒）"), false);
        return 1;
    }

    // ══════════════════════════════════════════════════════════════════
    //  [火花·阶段1] /yzarc sparktest：静止亮点 + 飞行拖尾，验证火花渲染管线
    // ══════════════════════════════════════════════════════════════════

    private int sparkTest(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        net.minecraft.world.entity.Entity ent = src.getEntity();
        if (!(ent instanceof net.minecraft.world.entity.player.Player player)) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition(1f);
        net.minecraft.world.phys.Vec3 fwd = player.getViewVector(1f);
        // 静止火花：视线前方 6 格，纯亮点无拖尾（velocity=0 退化对称亮点）
        net.minecraft.client.yiz.lightning.render.LightningRenderer.enqueueSpark(
                new net.minecraft.client.yiz.lightning.fx.SparkEffect(
                        net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier.fixed(eye.add(fwd.scale(6))),
                        net.minecraft.world.phys.Vec3.ZERO,
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(),
                        0.08f, 1.5f, 0.40f, 0.60f, 1.00f,
                        net.minecraft.client.yiz.lightning.fx.SparkEffect.Kind.HIT, 0f));
        // 拖尾火花：视线前方 4 格，沿视线方向飞 6 格/s，观察拖尾朝向运动方向
        net.minecraft.client.yiz.lightning.render.LightningRenderer.enqueueSpark(
                new net.minecraft.client.yiz.lightning.fx.SparkEffect(
                        net.minecraft.client.yiz.lightning.orchestrate.PositionSupplier.fixed(eye.add(fwd.scale(4))),
                        fwd.scale(6),
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(),
                        0.06f, 1.2f, 0.40f, 0.60f, 1.00f,
                        net.minecraft.client.yiz.lightning.fx.SparkEffect.Kind.ENDPOINT, 2f));
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 火花渲染测试：静止亮点 + 沿视线飞行拖尾（~1.5s）"), false);
        return 1;
    }

    // ══════════════════════════════════════════════════════════════════
    //  [火花·阶段3] /yzarc spark on|off|endpoint|hit|surface — 运行时开关
    // ══════════════════════════════════════════════════════════════════

    private int sparkOn(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.client.yiz.lightning.config.SparkConfig.setAll(true);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 火花系统：全开（端点/命中/表面）"), false);
        return 1;
    }

    private int sparkOff(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        net.minecraft.client.yiz.lightning.config.SparkConfig.setAll(false);
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 火花系统：全关"), false);
        return 1;
    }

    private int sparkToggleEndpoint(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        boolean now = net.minecraft.client.yiz.lightning.config.SparkConfig.toggleEndpoint();
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 端点火花：" + onOff(now)), false);
        return 1;
    }

    private int sparkToggleHit(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        boolean now = net.minecraft.client.yiz.lightning.config.SparkConfig.toggleHit();
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 命中火花：" + onOff(now)), false);
        return 1;
    }

    private int sparkToggleSurface(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        boolean now = net.minecraft.client.yiz.lightning.config.SparkConfig.toggleSurface();
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 表面火花：" + onOff(now)), false);
        return 1;
    }

    private int sparkStatus(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        boolean en = net.minecraft.client.yiz.lightning.config.SparkConfig.isEnabled();
        boolean ep = net.minecraft.client.yiz.lightning.config.SparkConfig.isEndpoint();
        boolean ht = net.minecraft.client.yiz.lightning.config.SparkConfig.isHit();
        boolean sf = net.minecraft.client.yiz.lightning.config.SparkConfig.isSurface();
        ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "§b[闪电] 火花系统 " + onOff(en)
                        + " §7|§r 端点 " + onOff(ep)
                        + " §7|§r 命中 " + onOff(ht)
                        + " §7|§r 表面 " + onOff(sf)), false);
        return 1;
    }

    private static String onOff(boolean v) { return v ? "§a开" : "§c关"; }

    // ══════════════════════════════════════════════════════════════════
    //  [里程碑4] 预设技能 /yzarc skill a|b|c
    // ══════════════════════════════════════════════════════════════════

    private int runSkill(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                         java.util.function.Consumer<net.minecraft.world.entity.player.Player> fn, String name) {
        net.minecraft.commands.CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof net.minecraft.world.entity.player.Player player)) {
            src.sendFailure(net.minecraft.network.chat.Component.literal("需要由玩家执行"));
            return 0;
        }
        fn.accept(player);
        String n = name;
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal("§b[闪电] 释放 " + n), false);
        return 1;
    }

    private int skillA(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        return runSkill(ctx, net.minecraft.client.yiz.lightning.LightningFX::skillA, "技能A（头顶球·自动链式）");
    }

    private int skillB(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        return runSkill(ctx, net.minecraft.client.yiz.lightning.LightningFX::skillB, "技能B（定向·命中扩散）");
    }

    private int skillC(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx) {
        return runSkill(ctx, net.minecraft.client.yiz.lightning.LightningFX::skillC, "技能C（范围·群体链式）");
    }

    // ════════════════════════════════════════════════════════════════
    //  技能释放：R/G/C/V 按键监听
    // ════════════════════════════════════════════════════════════════

    private void onSkillKeyTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return; // 有 GUI 打开时不响应

        // V：切换下一个小技能槽（1→2→3→1）
        if (SkillKeyMappings.SWITCH.consumeClick()) {
            net.minecraft.client.yiz.hud.SkillHud.selectedSmall =
                (net.minecraft.client.yiz.hud.SkillHud.selectedSmall + 1) % 3;
        }
        // Y：释放大槽技能（slot 0）
        if (SkillKeyMappings.BIG.consumeClick()) {
            net.minecraft.client.yiz.handler.CastDirectionTracker.capture(mc.player);
            net.minecraft.client.yiz.network.C2SSkillCastPayload.send(0);
        }
        // R：释放当前选中的小槽技能（slot = selectedSmall + 1）
        if (SkillKeyMappings.SKILL.consumeClick()) {
            int slot = net.minecraft.client.yiz.hud.SkillHud.selectedSmall + 1;
            net.minecraft.client.yiz.handler.CastDirectionTracker.capture(mc.player);
            net.minecraft.client.yiz.network.C2SSkillCastPayload.send(slot);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HUD 编辑器：DEL+ALT 边沿触发打开
    // ════════════════════════════════════════════════════════════════

    private boolean delAltWasDown = false;

    private void onHudKeyTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long window = mc.getWindow().getWindow();
        boolean alt = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                   || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean del = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DELETE);
        boolean both = alt && del;
        if (both && !delAltWasDown && mc.screen == null) {
            mc.setScreen(new HudEditorScreen());
        }
        delAltWasDown = both;
    }
}
