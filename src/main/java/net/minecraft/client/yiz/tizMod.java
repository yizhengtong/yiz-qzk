package net.minecraft.client.yiz;

import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.server.level.ServerPlayer;
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

        // 注册网络同步处理器
        modEventBus.addListener(NetworkHandler::onRegisterPayloadHandlers);

        // 初始化简易指令注册器（下游模组通过 API 提交指令，无需自行订阅事件）
        SimpleCommandRegistry.init();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // Register data reload listener (NeoForge event bus, not mod bus)
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
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
     * Handle player login: 同步已解锁效果到客户端。
     */
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            NetworkHandler.syncPlayerUnlocks(serverPlayer);
            LOGGER.debug("Synced unlocks for player {} on login", serverPlayer.getGameProfile().getName());
        }
    }

    /**
     * Handle player respawn/clone: 重新同步解锁状态。
     * 死亡重生后玩家实体被替换，需通知客户端最新状态。
     */
    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                NetworkHandler.syncPlayerUnlocks(serverPlayer);
                LOGGER.debug("Player cloned, unlock data resynced");
            }
        }
    }
}
