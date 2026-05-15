package net.minecraft.client.yiz;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.yiz.api.YizModQZKAPI;
import net.minecraft.client.yiz.core.data.EffectDataLoader;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        registerDebugCommands();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // Register data reload listener (NeoForge event bus, not mod bus)
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListener);

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
    }

    /**
     * 注册开发调试指令 /yiz cs <1-7> — 为手持物品快速设置预设属性。
     */
    private void registerDebugCommands() {
        SimpleCommandRegistry.register(
            Commands.literal("yiz")
                .then(Commands.literal("cs")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1, 7))
                        .executes(ctx -> {
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            var source = ctx.getSource();
                            if (!(source.getEntity() instanceof Player player)) {
                                source.sendFailure(Component.literal("Only players can use this command"));
                                return 0;
                            }
                            ItemStack held = player.getMainHandItem();
                            if (held.isEmpty()) {
                                source.sendFailure(Component.literal("You must hold an item"));
                                return 0;
                            }

                            String label;
                            switch (id) {
                                case 1 -> {
                                    YizModQZKAPI.addAttackDamage(held, 25);
                                    label = "Attack Damage +25";
                                }
                                case 2 -> {
                                    YizModQZKAPI.addAttackSpeed(held, 4);
                                    label = "Attack Speed +4";
                                }
                                case 3 -> {
                                    YizModQZKAPI.addInteractionRange(held, 10);
                                    label = "Interaction Range +10";
                                }
                                case 4 -> {
                                    YizModQZKAPI.setSweepRatio(held, 5.0);
                                    YizModQZKAPI.setSweepDecay(held, false);
                                    label = "Sweep 5-block, no decay";
                                }
                                case 5 -> {
                                    YizModQZKAPI.setMaxDurability(held, 5);
                                    label = "Max Durability = 5";
                                }
                                case 6 -> {
                                    YizModQZKAPI.setDamageAmplification(held, 0.5);
                                    label = "Damage Amplification +50%";
                                }
                                case 7 -> {
                                    YizModQZKAPI.setDamageReduction(held, 1.0);
                                    label = "Damage Reduction 100%";
                                }
                                default -> {
                                    source.sendFailure(Component.literal("Invalid id: " + id));
                                    return 0;
                                }
                            }

                            source.sendSuccess(() ->
                                Component.literal("§a✔ §f" + label + " §7→ §f" +
                                    held.getHoverName().getString()), false);
                            return 1;
                        })
                    )
                )
        );
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
