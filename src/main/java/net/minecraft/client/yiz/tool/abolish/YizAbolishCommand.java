package net.minecraft.client.yiz.tool.abolish;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.client.yiz.core.AbolitionStateManager;
import net.minecraft.client.yiz.core.VTableReplace;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * /yiz abolish — 物品废除 + 背包废除 指令。
 *
 * <h3>子指令</h3>
 * <ul>
 *   <li>{@code /yiz abolish item <item_id>} — 彻底废除指定物品的所有功能</li>
 *   <li>{@code /yiz abolish item <item_id> rightclick} — 只废除右键功能</li>
 *   <li>{@code /yiz abolish defense [player]} — 废除指定玩家的背包防御（默认自己）</li>
 *   <li>{@code /yiz restore defense [player]} — 恢复指定玩家的背包防御</li>
 * </ul>
 */
public final class YizAbolishCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger("YizAbolishCmd");

    private YizAbolishCommand() {}

    public static void register() {
        // /yiz abolish ...
        LiteralArgumentBuilder<CommandSourceStack> yiz = Commands.literal("yiz");

        yiz.then(Commands.literal("abolish")
                .then(Commands.literal("item")
                        .then(Commands.argument("item",
                                        ResourceKeyArgument.key(Registries.ITEM))
                                .executes(YizAbolishCommand::abolishItemFull)
                                .then(Commands.literal("rightclick")
                                        .executes(YizAbolishCommand::abolishItemRightClick))
                                .then(Commands.literal("attack")
                                        .executes(YizAbolishCommand::abolishItemAttack))
                                .then(Commands.literal("tick")
                                        .executes(YizAbolishCommand::abolishItemTick))
                                .then(Commands.literal("equip")
                                        .executes(YizAbolishCommand::abolishItemEquip))
                                .then(Commands.literal("tooltip")
                                        .executes(YizAbolishCommand::abolishItemTooltip))
                        ))
                // ── 实体废除 ──
                .then(Commands.literal("entity")
                        .then(Commands.argument("entity",
                                        ResourceKeyArgument.key(Registries.ENTITY_TYPE))
                                .executes(YizAbolishCommand::abolishEntityFull)
                                .then(Commands.literal("behavior")
                                        .executes(YizAbolishCommand::abolishEntityBehavior))
                                .then(Commands.literal("aliveness")
                                        .executes(YizAbolishCommand::abolishEntityAliveness))
                        ))
                .then(Commands.literal("defense")
                        .executes(YizAbolishCommand::abolishDefenseSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(YizAbolishCommand::abolishDefensePlayer))
                ));

        // /yiz restore defense [player]
        yiz.then(Commands.literal("restore")
                .then(Commands.literal("defense")
                        .executes(YizAbolishCommand::restoreDefenseSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(YizAbolishCommand::restoreDefensePlayer))
                )
                .then(Commands.literal("entity")
                        .then(Commands.argument("entity",
                                        ResourceKeyArgument.key(Registries.ENTITY_TYPE))
                                .executes(YizAbolishCommand::restoreEntity))
                ));

        // /yiz vtable status
        // /yiz vtable diagnose <item>
        yiz.then(Commands.literal("vtable")
                .then(Commands.literal("status")
                        .executes(YizAbolishCommand::vtableStatus))
                .then(Commands.literal("diagnose")
                        .then(Commands.argument("item",
                                        ResourceKeyArgument.key(Registries.ITEM))
                                .executes(YizAbolishCommand::vtableDiagnose)))
        );

        SimpleCommandRegistry.register(yiz);
    }

    // ══════════════════════════════════════════════════════════
    //  物品废除
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static ResourceLocation getItemId(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceKey<Item> itemKey = ctx.getArgument("item", ResourceKey.class);
        return itemKey.location();
    }

    private static int abolishItemFull(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        if (itemId == null) return 0;

        // 三重联动：AbolitionStateManager(Mixin) + VTableReplace
        int count = ItemAbolitionHelper.abolishItemById(itemId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除物品 §e" + itemId + " §a(" + count + " 个方法 + Mixin 拦截)"), true);
        LOGGER.info("{} abolished item {} ({} methods + Mixin)",
                ctx.getSource().getDisplayName(), itemId, count);
        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Item> getItemClassFromId(ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item != null ? (Class<? extends Item>) item.getClass() : null;
    }

    private static int abolishItemRightClick(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        Class<? extends Item> itemClass = getItemClassFromId(itemId);
        if (itemClass == null) return 0;

        // Mixin 层：注册到状态管理器
        AbolitionStateManager.abolishItem(itemId);
        // VTable 层：选择性覆写
        int count = ItemAbolitionHelper.abolishRightClick(itemClass);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + itemId + " §a的右键功能 (" + count + "/2 + Mixin)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishItemAttack(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        Class<? extends Item> itemClass = getItemClassFromId(itemId);
        if (itemClass == null) return 0;

        AbolitionStateManager.abolishItem(itemId);
        int count = ItemAbolitionHelper.abolishAttackEffects(itemClass);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + itemId + " §a的攻击效果 (" + count + "/2 + Mixin)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishItemTick(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        Class<? extends Item> itemClass = getItemClassFromId(itemId);
        if (itemClass == null) return 0;

        AbolitionStateManager.abolishItem(itemId);
        boolean ok = ItemAbolitionHelper.abolishInventoryTick(itemClass);
        ctx.getSource().sendSuccess(() -> Component.literal(
                ok ? "§a已废除 §e" + itemId + " §a的背包 tick 效果 (Mixin)"
                   : "§a已通过 Mixin 废除 §e" + itemId + " §a的背包 tick"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishItemEquip(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        Class<? extends Item> itemClass = getItemClassFromId(itemId);
        if (itemClass == null) return 0;

        AbolitionStateManager.abolishItem(itemId);
        int count = ItemAbolitionHelper.abolishEquipEffects(itemClass);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + itemId + " §a的装备效果 (" + count + "/2 + Mixin)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishItemTooltip(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation itemId = getItemId(ctx);
        Class<? extends Item> itemClass = getItemClassFromId(itemId);
        if (itemClass == null) return 0;

        boolean ok = ItemAbolitionHelper.abolishHoverText(itemClass);
        ctx.getSource().sendSuccess(() -> Component.literal(
                ok ? "§a已废除 " + itemClass.getSimpleName() + " 的自定义 tooltip"
                   : "§c该物品未 override appendHoverText"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ══════════════════════════════════════════════════════════
    //  背包防御废除
    // ══════════════════════════════════════════════════════════

    private static int abolishDefenseSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        InventoryDefenseAbolisher.abolishPlayerDefense(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除你的背包防御 — 来自背包/装备的防御效果将不再生效"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishDefensePlayer(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        InventoryDefenseAbolisher.abolishPlayerDefense(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + target.getName().getString() + " §a的背包防御"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int restoreDefenseSelf(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("§c此指令只能由玩家执行"));
            return 0;
        }
        InventoryDefenseAbolisher.restorePlayerDefense(player);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已恢复你的背包防御"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int restoreDefensePlayer(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        InventoryDefenseAbolisher.restorePlayerDefense(target);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已恢复 §e" + target.getName().getString() + " §a的背包防御"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ══════════════════════════════════════════════════════════
    //  VTable 诊断
    // ══════════════════════════════════════════════════════════

    private static int vtableStatus(CommandContext<CommandSourceStack> ctx) {
        // 状态概览
        String available = VTableReplace.isAvailable() ? "§a可用" : "§c不可用";
        String donorsInit = VTableReplace.isDonorsInitialized() ? "§a已初始化" : "§c未初始化";
        String initErr = VTableReplace.getInitError();
        String donorCount = String.valueOf(VTableReplace.getDonorCacheSize());

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6===== VTableReplace 诊断 =====\n" +
                "  状态: " + available + "\n" +
                "  Donors: " + donorsInit + " (缓存 " + donorCount + " 个方法)\n" +
                "  初始化错误: " + (initErr != null ? "§c" + initErr : "§7无") + "\n" +
                "§7用 /yiz vtable diagnose <item> 检查具体物品"
        ), false);

        // 如果可用，打印详细偏移量
        if (VTableReplace.isAvailable()) {
            String details = VTableReplace.getProbeOffsetsSummary();
            ctx.getSource().sendSuccess(() -> Component.literal(details), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static int vtableDiagnose(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceKey<Item> itemKey = ctx.getArgument("item", ResourceKey.class);
        Item item = ctx.getSource().getServer().registryAccess()
                .registryOrThrow(Registries.ITEM).get(itemKey);
        if (item == null) {
            ctx.getSource().sendFailure(Component.literal("§c物品不存在: " + itemKey.location()));
            return 0;
        }

        // 使用 ItemAbolitionHelper 中定义的方法列表进行诊断
        String[][] methods = {
                {"use",              "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"},
                {"useOn",            "(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"},
                {"hurtEnemy",        "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z"},
                {"inventoryTick",    "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V"},
                {"releaseUsing",     "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"},
                {"appendHoverText",  "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/TooltipContext;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V"},
                {"onEquip",          "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/LivingEntity;)V"},
                {"onCraftedBy",      "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)V"},
                {"getUseDuration",   "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"},
        };

        String[] names = new String[methods.length];
        String[] descs = new String[methods.length];
        for (int i = 0; i < methods.length; i++) {
            names[i] = methods[i][0];
            descs[i] = methods[i][1];
        }

        if (VTableReplace.isAvailable()) {
            String result = VTableReplace.diagnoseItemMethods(item.getClass(), names, descs);
            ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§cVTableReplace 不可用，无法诊断 vtable。\n" +
                    "§7原因: " + (VTableReplace.getInitError() != null ?
                            VTableReplace.getInitError() : "未知")), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    // ══════════════════════════════════════════════════════════
    //  实体废除
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private static ResourceLocation getEntityId(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceKey<EntityType<?>> key = ctx.getArgument("entity", ResourceKey.class);
        return key.location();
    }

    private static int abolishEntityFull(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation entityId = getEntityId(ctx);
        // Mixin 层：注册到状态管理器（立即生效）
        int count = EntityAbolitionHelper.abolishEntityById(entityId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除实体 §e" + entityId + " §a(VTable " + count + " 方法 + Mixin 拦截)"), true);
        LOGGER.info("{} abolished entity {} ({} methods + Mixin)",
                ctx.getSource().getDisplayName(), entityId, count);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishEntityBehavior(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation entityId = getEntityId(ctx);
        EntityAbolitionStateManager.abolishEntity(entityId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + entityId + " §a的行为 (tick/AI/despawn)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int abolishEntityAliveness(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation entityId = getEntityId(ctx);
        EntityAbolitionStateManager.abolishEntity(entityId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已废除 §e" + entityId + " §a的存活判定 (isAlive/isDeadOrDying)"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int restoreEntity(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        ResourceLocation entityId = getEntityId(ctx);
        EntityAbolitionHelper.restoreEntityById(entityId);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已恢复实体 §e" + entityId), true);
        LOGGER.info("{} restored entity {}", ctx.getSource().getDisplayName(), entityId);
        return Command.SINGLE_SUCCESS;
    }
}
