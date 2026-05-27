package net.minecraft.client.yiz.tool;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.yiz.core.PlayerClassSwapper;

/**
 * /yiz th — 切换当前玩家的保护态。
 *
 * <p>执行者必须为玩家实体。每次执行在保护/非保护之间切换。</p>
 */
public final class YizProtectCommand {

    private YizProtectCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> yiz = Commands.literal("yiz");
        yiz.then(Commands.literal("th").executes(YizProtectCommand::toggleProtection));

        SimpleCommandRegistry.register(yiz);
    }

    private static int toggleProtection(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("此指令只能由玩家执行"));
            return 0;
        }

        boolean currentlyProtected = PlayerClassSwapper.isProtected(player);

        if (currentlyProtected) {
            PlayerClassSwapper.disableProtection(player);
            source.sendSuccess(() -> Component.literal("§c保护态已关闭"), true);
        } else {
            PlayerClassSwapper.enableProtection(player);
            source.sendSuccess(() -> Component.literal("§a保护态已开启 — 免疫一切伤害"), true);
        }

        return Command.SINGLE_SUCCESS;
    }
}
