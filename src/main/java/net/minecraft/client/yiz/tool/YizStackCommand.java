package net.minecraft.client.yiz.tool;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.yiz.core.ItemStackSizeOverride;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * /yiz stack — 运行时修改物品最大堆叠数指令。
 *
 * <h3>子指令</h3>
 * <ul>
 *   <li>{@code /yiz stack set <item> <数量>} — 设置某物品最大堆叠数（1 ~ 99，自动夹紧）</li>
 *   <li>{@code /yiz stack reset <item>} — 移除某物品的覆盖（恢复原版行为）</li>
 *   <li>{@code /yiz stack clear} — 清空所有覆盖</li>
 *   <li>{@code /yiz stack list} — 列出所有覆盖</li>
 * </ul>
 *
 * <p>设置即时生效（通过 {@code ItemStackMaxSizeMixin} 拦截 {@code getMaxStackSize()}），
 * 且持久化到 {@code config/yizmodqzk-stacksize.json}，重启后自动恢复。</p>
 */
public final class YizStackCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("YizStackCmd");

    private YizStackCommand() {}

    public static void register() {
        LiteralArgumentBuilder<CommandSourceStack> yiz = Commands.literal("yiz");

        yiz.then(Commands.literal("stack")
                .then(Commands.literal("set")
                        .then(Commands.argument("item", ResourceKeyArgument.key(Registries.ITEM))
                                .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                        .executes(YizStackCommand::set))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("item", ResourceKeyArgument.key(Registries.ITEM))
                                .executes(YizStackCommand::reset)))
                .then(Commands.literal("clear")
                        .executes(YizStackCommand::clear))
                .then(Commands.literal("list")
                        .executes(YizStackCommand::list)));

        SimpleCommandRegistry.register(yiz);
    }

    // ══════════════════════════════════════════════════════════
    //  子指令实现
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static ResourceLocation getItemId(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceKey<Item> itemKey = ctx.getArgument("item", ResourceKey.class);
        return itemKey.location();
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        if (itemId == null) return 0;
        int requested = IntegerArgumentType.getInteger(ctx, "size");
        int applied = ItemStackSizeOverride.set(itemId, requested);

        ctx.getSource().sendSuccess(() -> Component.literal(
                (requested != applied)
                        ? "§e" + itemId + " §a最大堆叠数已设为 §e" + applied
                          + " §7(请求值 " + requested + " 超出范围，已夹紧到合法区间)"
                        : "§a已将 §e" + itemId + " §a最大堆叠数设为 §e" + applied
        ), true);
        LOGGER.info("{} set stack size of {} to {} (requested {})",
                ctx.getSource().getDisplayName(), itemId, applied, requested);
        return Command.SINGLE_SUCCESS;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        if (itemId == null) return 0;
        boolean removed = ItemStackSizeOverride.reset(itemId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                removed ? "§a已恢复 §e" + itemId + " §a的原始最大堆叠数"
                        : "§7" + itemId + " §7本就没有堆叠数覆盖"
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int n = ItemStackSizeOverride.clear();
        ctx.getSource().sendSuccess(() -> Component.literal(
                n > 0 ? "§a已清空 §e" + n + " §a条堆叠数覆盖"
                      : "§7当前没有任何堆叠数覆盖"
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        Map<ResourceLocation, Integer> all = ItemStackSizeOverride.snapshot();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7当前没有任何堆叠数覆盖（用 §e/yiz stack set <物品> <数量> §7添加）"), false);
            return Command.SINGLE_SUCCESS;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6===== 堆叠数覆盖 (" + all.size() + " 条) ====="), false);
        all.forEach((id, size) -> ctx.getSource().sendSuccess(
                () -> Component.literal("§e" + id + " §7→ §a" + size), false));
        return Command.SINGLE_SUCCESS;
    }
}
