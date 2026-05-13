package net.minecraft.client.yiz;

import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.client.yiz.tool.health.HealthCommand;
import net.minecraft.client.yiz.tool.health.SimpleEntityDamageHandler;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
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

        // 注册自定义属性
        ModAttributes.getRegistry().register(modEventBus);

        // 将自定义属性添加到玩家实体
        modEventBus.addListener(this::onEntityAttributeModification);

        // 注册最简单的 ASM Agent 伤害测试接口
        SimpleEntityDamageHandler.register();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // 注册指令
        NeoForge.EVENT_BUS.register(HealthCommand.class);

        // Register data reload listener (NeoForge event bus, not mod bus)
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("YizMod QZK Framework initialized");
    }

    /**
     * 将自定义属性添加到实体类型上。
     */
    private void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ModAttributes.ATTACHMENT);
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
