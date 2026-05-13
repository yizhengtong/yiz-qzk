package net.minecraft.client.yiz.tool.health;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 健康值系统调试指令
 *
 * <ul>
 *   <li>/yiz agent — 检测 ASM Agent 是否加载成功</li>
 *   <li>/yiz e &lt;value&gt; — 设置 ASM 实体伤害值（攻击非玩家实体生效）</li>
 * </ul>
 */
public final class HealthCommand {

    private HealthCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("yiz")
            // /yiz agent — 检测 agent 状态
            .then(Commands.literal("agent")
                .executes(HealthCommand::checkAgent)
            )
            // /yiz e <value> — 最简单的 ASM Agent 伤害测试
            .then(Commands.literal("e")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx -> {
                        int value = IntegerArgumentType.getInteger(ctx, "value");
                        SimpleEntityDamageHandler.setDamageValue(value);
                        ctx.getSource().sendSuccess(() ->
                            Component.literal("§a✔ 已设置 ASM 实体伤害值: §e" + value + " §a（攻击非玩家实体生效）"), false);
                        return 1;
                    })
                )
            )
            // /yiz kill e — 击杀所有非玩家实体
            .then(Commands.literal("kill")
                .then(Commands.literal("e")
                    .executes(HealthCommand::killAllNonPlayer)
                )
                .then(Commands.literal("s")
                    .executes(HealthCommand::killSelf)
                )
            )
        );
    }

    // ==================== 指令执行器 ====================

    private static int checkAgent(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        if (net.minecraft.client.yiz.core.asm.AsmBootstrapper.isAgentLoaded()) {
            source.sendSuccess(() ->
                Component.literal("§a✔ ASM Agent 已加载，字节码改写层正常运行"), false);
        } else {
            source.sendSuccess(() ->
                Component.literal("§e⚠ ASM Agent 未加载，使用 Mixin 回退模式（部分实体不受支持）"), false);
        }
        return 1;
    }

    private static int killAllNonPlayer(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var level = source.getLevel();

        java.util.List<LivingEntity> targets = new java.util.ArrayList<>();
        for (var entity : level.getEntities().getAll()) {
            if (entity instanceof LivingEntity le && !(le instanceof Player)) {
                targets.add(le);
            }
        }

        for (var e : targets) {
            e.kill();
        }

        source.sendSuccess(() ->
            Component.literal("§c☠ 已击杀 §e" + targets.size() + " §c个非玩家实体"), false);
        return targets.size();
    }

    private static int killSelf(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = ctx.getSource().getPlayerOrException();
        player.kill();
        ctx.getSource().sendSuccess(() ->
            Component.literal("§c☠ 已自杀"), false);
        return 1;
    }
}
