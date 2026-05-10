package net.minecraft.client.yiz.test.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.client.yiz.test.effect.TestDamageAffix;
import net.minecraft.client.yiz.test.effect.TestFlameShadow;
import net.minecraft.client.yiz.test.talent.TestStrengthTalent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * 测试命令
 * 提供 /yizmodqzk test setup 来设置测试环境。
 */
public final class ModCommands {

    private ModCommands() {}

    /**
     * 注册所有命令。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yizmodqzk")
            .then(Commands.literal("test")
                .then(Commands.literal("setup")
                    .executes(ModCommands::runTestSetup)
                )
            )
        );
    }

    /**
     * 执行测试设置：
     * 1. 给予玩家附有测试词缀的钻石剑
     * 2. 解锁测试天赋
     * 3. 应用测试随影
     */
    private static int runTestSetup(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }

        // 1. 给予测试物品（带有词缀效果的钻石剑）
        giveTestSword(player);

        // 2. 解锁测试天赋
        unlockTestTalent(player);

        // 3. 确认
        player.sendSystemMessage(Component.literal(""));
        player.sendSystemMessage(Component.literal("§a========== YizMod QZK 测试设置完成 =========="));
        player.sendSystemMessage(Component.literal("§a✅ [词缀] 已获得试炼之剑（伤害增幅）"));
        player.sendSystemMessage(Component.literal("§a✅ [天赋] 已解锁力量天赋"));
        player.sendSystemMessage(Component.literal("§a✅ [随影] 火焰随影已就绪（打开容器时激活）"));
        player.sendSystemMessage(Component.literal("§a"));
        player.sendSystemMessage(Component.literal("§7操作提示："));
        player.sendSystemMessage(Component.literal("§7  Ctrl+Alt → 开关自定义物品 UI"));
        player.sendSystemMessage(Component.literal("§7  Ctrl+Shift → 开关天赋 UI"));
        player.sendSystemMessage(Component.literal("§7  用试炼之剑攻击生物 → 触发词缀效果"));
        player.sendSystemMessage(Component.literal("§7  打开箱子/容器 → 触发随影效果"));
        player.sendSystemMessage(Component.literal("§7  打开天赋 UI → 查看已解锁天赋"));
        player.sendSystemMessage(Component.literal("§a================================================"));
        player.sendSystemMessage(Component.literal(""));

        return 1;
    }

    /**
     * 给予玩家带有测试词缀效果的钻石剑。
     */
    private static void giveTestSword(Player player) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("§c§l试炼之剑 §7[词缀]"));

        // 应用效果到物品
        Optional<AbstractEffect> effect = ModRegistries.getEffect(TestDamageAffix.ID);
        if (effect.isPresent()) {
            net.minecraft.client.yiz.core.data.EffectNBTHandler.addEffectToItem(sword, effect.get());
            player.getInventory().add(sword);
        } else {
            player.sendSystemMessage(Component.literal("§c错误：测试词缀效果未注册"));
        }
    }

    /**
     * 为玩家解锁测试天赋。
     */
    private static void unlockTestTalent(Player player) {
        // 解锁力量天赋
        Optional<AbstractEffect> talent = ModRegistries.getEffect(TestStrengthTalent.ID);
        talent.ifPresentOrElse(
            effect -> UnlockManager.unlock(player, effect.getId()),
            () -> player.sendSystemMessage(Component.literal("§c错误：测试天赋未注册"))
        );

        // 同时解锁词缀和随影用于 UI 展示
        ModRegistries.getEffect(TestDamageAffix.ID).ifPresent(
            effect -> UnlockManager.unlock(player, effect.getId())
        );
        ModRegistries.getEffect(TestFlameShadow.ID).ifPresent(
            effect -> UnlockManager.unlock(player, effect.getId())
        );
    }
}
