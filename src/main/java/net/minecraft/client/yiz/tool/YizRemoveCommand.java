package net.minecraft.client.yiz.tool;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * /yiz remove &lt;radius&gt; — 清除玩家周围指定半径内的所有非玩家实体。
 *
 * <p>用法：
 * <ul>
 *   <li>{@code /yiz remove 10} — 清除 10 格半径内的实体</li>
 *   <li>{@code /yiz remove 50} — 清除 50 格半径内的实体</li>
 *   <li>{@code /yiz remove 0}  — 清除 0 格（仅脚下，精确移除单个）</li>
 * </ul>
 *
 * <p>排除玩家自身和其他玩家。先快照实体列表再逐个 discard()，
 * 避免迭代中修改活集合导致漏删。</p>
 */
public final class YizRemoveCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("YizRemoveCmd");

    private YizRemoveCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("yiz")
                .then(Commands.literal("remove")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0, 256))
                                .executes(YizRemoveCommand::execute)
                        )
                );

        SimpleCommandRegistry.register(cmd);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double radius = DoubleArgumentType.getDouble(ctx, "radius");

        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(radius);

        // 先快照到独立列表，避免 discard() 修改底层活集合导致漏删
        List<Entity> snapshot = new ArrayList<>(
                level.getEntitiesOfClass(Entity.class, box, e -> !(e instanceof Player))
        );

        int count = 0;
        for (Entity entity : snapshot) {
            if (entity.isRemoved()) continue;
            if (EntityRemovalUtil.forceRemove(entity)) {
                count++;
            }
        }

        final int removed = count;
        source.sendSuccess(() -> Component.literal(
                "§a已清除 " + removed + " 个实体（半径 " + radius + " 格）"), true);
        LOGGER.info("{} removed {} entities within radius {}",
                source.getDisplayName(), removed, radius);

        return Command.SINGLE_SUCCESS;
    }
}
