package net.minecraft.client.yiz.tool;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * /yiz setHealth &lt;选择器&gt; &lt;数值&gt; &lt;类型&gt; — 修改实体生命值。
 *
 * <h3>选择器</h3>
 * <ul>
 *   <li>{@code @s} — 执行者自身</li>
 *   <li>{@code @j} — 距离执行者最近的实体（排除自身）</li>
 *   <li>{@code @e} — 除执行者外的全部实体</li>
 * </ul>
 *
 * <h3>类型</h3>
 * <ul>
 *   <li>{@code 1} — 当前生命值减少 &lt;数值&gt;（造成伤害语义）</li>
 *   <li>{@code 2} — 设置为最大生命值的 &lt;数值&gt;%</li>
 * </ul>
 *
 * <p>注：本指令绕过减伤/格挡（直接写 setHealth），用于测试与精确控制。</p>
 */
public final class YizSetHealthCommand {

    private YizSetHealthCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> yiz = Commands.literal("yiz");
        // /yiz setHealth <@s|@j|@e> <数值> <类型1|2>  —— 选择器用字面量，无需引号
        var setHealth = Commands.literal("setHealth");
        for (String sel : new String[]{"@s", "@j", "@e"}) {
            setHealth.then(Commands.literal(sel)
                .then(Commands.argument("value", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f))
                    .then(Commands.argument("type", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 2))
                        .executes(ctx -> run(ctx, sel))
                    )
                )
            );
        }
        yiz.then(setHealth);
        SimpleCommandRegistry.register(yiz);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String target) {
        CommandSourceStack source = ctx.getSource();
        float value = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "value");
        int type = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "type");

        if (!(source.getEntity() instanceof ServerPlayer self)) {
            source.sendFailure(Component.literal("此指令只能由玩家执行"));
            return 0;
        }

        // 解析目标实体
        java.util.List<LivingEntity> targets;
        switch (target) {
            case "@s" -> targets = java.util.List.of(self);
            case "@j" -> {
                LivingEntity nearest = findNearest(self);
                if (nearest == null) {
                    source.sendFailure(Component.literal("附近没有其他实体"));
                    return 0;
                }
                targets = java.util.List.of(nearest);
            }
            case "@e" -> {
                targets = new java.util.ArrayList<>();
                for (Entity e : self.serverLevel().getAllEntities()) {
                    if (e instanceof LivingEntity le && e != self && e.isAlive()) {
                        targets.add(le);
                    }
                }
                if (targets.isEmpty()) {
                    source.sendFailure(Component.literal("没有可作用的实体"));
                    return 0;
                }
            }
            default -> {
                source.sendFailure(Component.literal("未知选择器: " + target + "（支持 @s / @j / @e）"));
                return 0;
            }
        }

        int affected = 0;
        for (LivingEntity le : targets) {
            if (apply(le, value, type)) affected++;
        }

        final int n = affected;
        source.sendSuccess(
            () -> Component.literal("§asetHealth 作用 " + n + " 个实体 (目标=" + target
                + " 值=" + value + " 类型=" + type + ")"),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    /** 类型1：当前生命值减少 value；类型2：直接设为最大生命值的 value%。 */
    private static boolean apply(LivingEntity le, float value, int type) {
        if (type == 1) {
            float cur = le.getHealth();
            le.setHealth(cur - value);
        } else {
            float max = le.getMaxHealth();
            le.setHealth(max * (value / 100f));
        }
        return true;
    }

    /** 找距离 self 最近的、存活的 LivingEntity（排除自身）。 */
    private static LivingEntity findNearest(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        LivingEntity nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity e : level.getAllEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e == self || !le.isAlive()) continue;
            double sq = le.distanceToSqr(self);
            if (sq < bestSq) {
                bestSq = sq;
                nearest = le;
            }
        }
        return nearest;
    }
}
