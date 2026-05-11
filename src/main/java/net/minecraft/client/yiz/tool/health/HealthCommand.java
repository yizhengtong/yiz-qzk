package net.minecraft.client.yiz.tool.health;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * 健康值系统调试指令
 *
 * <ul>
 *   <li>/yiz agent — 检测 ASM Agent 是否加载成功</li>
 *   <li>/yiz lkgd &lt;value&gt; — 为手中物品添加灵梦固定伤害</li>
 *   <li>/yiz lkbfb &lt;value&gt; — 为手中物品添加灵梦百分比伤害</li>
 *   <li>/yiz jl &lt;value&gt; — 为手中物品添加眷恋</li>
 * </ul>
 */
public final class HealthCommand {

    private static final ResourceLocation MODIFIER_FLAT = ResourceLocation.parse("yizmodqzk:cmd_reimu_flat");
    private static final ResourceLocation MODIFIER_PERCENT = ResourceLocation.parse("yizmodqzk:cmd_reimu_percent");
    private static final ResourceLocation MODIFIER_ATTACHMENT = ResourceLocation.parse("yizmodqzk:cmd_attachment");

    private HealthCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("yiz")
            // /yiz agent — 检测 agent 状态
            .then(Commands.literal("agent")
                .executes(HealthCommand::checkAgent)
            )
            // /yiz lkgd <value> — 灵梦固定伤害
            .then(Commands.literal("lkgd")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx -> addAttribute(ctx,
                        ModAttributes.REIMU_FLAT_DAMAGE,
                        IntegerArgumentType.getInteger(ctx, "value"),
                        MODIFIER_FLAT,
                        "灵梦固定伤害"))
                )
            )
            // /yiz lkbfb <value> — 灵梦百分比伤害
            .then(Commands.literal("lkbfb")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx -> addAttribute(ctx,
                        ModAttributes.REIMU_PERCENT_DAMAGE,
                        IntegerArgumentType.getInteger(ctx, "value"),
                        MODIFIER_PERCENT,
                        "灵梦百分比伤害"))
                )
            )
            // /yiz jl <value> — 眷恋
            .then(Commands.literal("jl")
                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                    .executes(ctx -> addAttribute(ctx,
                        ModAttributes.ATTACHMENT,
                        IntegerArgumentType.getInteger(ctx, "value"),
                        MODIFIER_ATTACHMENT,
                        "眷恋"))
                )
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

    private static int addAttribute(
        CommandContext<CommandSourceStack> ctx,
        Holder<Attribute> attribute,
        double value,
        ResourceLocation modifierId,
        String displayName
    ) throws CommandSyntaxException {
        var source = ctx.getSource();
        var player = source.getPlayerOrException();
        ItemStack item = player.getMainHandItem();

        if (item.isEmpty()) {
            source.sendFailure(Component.literal("§c请手持物品后使用"));
            return 0;
        }

        // 构建修正器
        var modifier = new AttributeModifier(modifierId, value, AttributeModifier.Operation.ADD_VALUE);

        // 获取当前属性修正器列表，移除同 ID 旧条目后添加新条目
        ItemAttributeModifiers current = item.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        List<ItemAttributeModifiers.Entry> filtered = current.modifiers().stream()
            .filter(e -> !e.modifier().id().equals(modifierId))
            .toList();

        ItemAttributeModifiers cleaned = new ItemAttributeModifiers(filtered, current.showInTooltip());
        ItemAttributeModifiers updated = cleaned.withModifierAdded(attribute, modifier, EquipmentSlotGroup.MAINHAND);
        item.set(DataComponents.ATTRIBUTE_MODIFIERS, updated);

        source.sendSuccess(() ->
            Component.literal("§a✔ 已添加 " + displayName + " §a: §e" + (int) value + "§a 到 "
                + item.getHoverName().getString()), true);
        return 1;
    }
}
