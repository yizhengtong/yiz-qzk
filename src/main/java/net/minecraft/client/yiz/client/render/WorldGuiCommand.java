package net.minecraft.client.yiz.client.render;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;

/**
 * /yiz gui — 开关世界光屏系统（默认关闭）。
 */
public final class WorldGuiCommand {

    private WorldGuiCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> yiz = Commands.literal("yiz");
        yiz.then(Commands.literal("gui").executes(WorldGuiCommand::toggle));
        SimpleCommandRegistry.register(yiz);
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx) {
        boolean now = !WorldGuiPanelManager.isEnabled();
        WorldGuiPanelManager.setEnabled(now);
        ctx.getSource().sendSuccess(
                () -> Component.literal("§a世界光屏系统已" + (now ? "开启" : "关闭")), false);
        return 1;
    }
}
