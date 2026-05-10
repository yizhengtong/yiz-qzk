package net.minecraft.client.yiz;

import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.tizMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(tizMod.MODID)
public class tizMod {
    public static final String MODID = "yizmodqzk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public tizMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(this::commonSetup);

        // Register data reload listener
        modEventBus.addListener(this::onAddReloadListener);

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
            // Copy unlock data from old player to new player
            var oldPlayer = event.getOriginal();
            var newPlayer = event.getEntity();

            // UnlockManager data is preserved in memory,
            // so no explicit copy needed here for in-session respawns.
            // For cross-session, data is loaded via EntityEffectNBTHandler.
            LOGGER.debug("Player cloned, unlock data preserved");
        }
    }
}
