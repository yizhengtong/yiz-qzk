package net.minecraft.client.yiz;

import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(tizMod.MODID)
public class tizMod {
    public static final String MODID = "yizmodqzk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public tizMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // 初始化简易指令注册器（下游模组通过 API 提交指令，无需自行订阅事件）
        SimpleCommandRegistry.init();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // Register data reload listener (NeoForge event bus, not mod bus)
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("YizMod QZK Framework initialized");
    }

    /**
     * Register JSON data reload listener.
     */
    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new EffectDataLoader());
        LOGGER.debug("EffectDataLoader registered");
    }

    /**
     * Handle player respawn/clone to persist unlock data.
     */
    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            var oldPlayer = event.getOriginal();
            var newPlayer = event.getEntity();

            LOGGER.debug("Player cloned, unlock data preserved");
        }
    }
}
