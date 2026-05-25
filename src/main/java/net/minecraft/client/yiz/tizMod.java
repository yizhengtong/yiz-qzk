package net.minecraft.client.yiz;

import net.minecraft.client.yiz.api.AttributeBalanceRegistry;
import net.minecraft.client.yiz.api.DaoPalaceAPI;
import net.minecraft.client.yiz.api.ProjectileReflectionSystem;
import net.minecraft.client.yiz.api.RealmProgressionAPI;
import net.minecraft.client.yiz.core.asm.AsmBootstrapper;
import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.core.registry.CreativeTabAutoRegistry;
import net.minecraft.client.yiz.core.registry.ModAttachments;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.effect.unlock.UnlockSavedData;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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
        // 注册 PlayerDataAPI 自动同步
        NetworkHandler.registerPlayerDataSync();

        // 注册境界跨度数据 + 同步
        RealmProgressionAPI.initDataKey();
        NetworkHandler.registerRealmSync();

        // 注册道宫数据 + 同步
        DaoPalaceAPI.initDataKey();
        NetworkHandler.registerDaoPalaceSync();

        // 初始化简易指令注册器（下游模组通过 API 提交指令，无需自行订阅事件）
        SimpleCommandRegistry.init();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // 注册玩家数据附件
        ModAttachments.register(modEventBus);

        // 初始化创造标签页自动注册（扫描实现 ITalentItem/ISkillItem/IGeneralItem/IWeaponItem 的物品）
        CreativeTabAutoRegistry.init(modEventBus);

        // Register data reload listener (NeoForge event bus, not mod bus)
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(this::onLevelSave);

        // 延迟加载 ASM Agent（此时 Mixin 已完成，不会与 geckolib 等模组冲突）
        try {
            AsmBootstrapper.start();
            LOGGER.info("ASM Agent bootstrapped from mod constructor");
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap ASM Agent from mod constructor", e);
        }
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
            NetworkHandler.syncPlayerRealm(serverPlayer);
            NetworkHandler.syncPlayerDaoPalaces(serverPlayer);
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
                NetworkHandler.syncPlayerRealm(serverPlayer);
                NetworkHandler.syncPlayerDaoPalaces(serverPlayer);
                LOGGER.debug("Player cloned, unlock data resynced");
            }
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        ProjectileReflectionSystem.tick(event.getEntity());
        AttributeBalanceRegistry.enforceFloors(event.getEntity());
    }

    // ==================== 解锁数据持久化 ====================

    private static UnlockSavedData unlockSavedData;

    private void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;

        unlockSavedData = serverLevel.getDataStorage().computeIfAbsent(
            UnlockSavedData.factory(), UnlockSavedData.dataName()
        );
        // 每次解锁新效果时标记存档需要保存
        UnlockManager.setDirtyCallback(unlockSavedData::setDirty);
        LOGGER.info("Unlock data loaded from world save");
    }

    private void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (serverLevel.dimension() != Level.OVERWORLD) return;
        if (unlockSavedData != null) {
            unlockSavedData.setDirty();
        }
    }
}
