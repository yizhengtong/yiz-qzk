package net.minecraft.client.yiz.handler;

import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 特殊装备执行引擎（模块 D — 无状态调度）。
 *
 * <p>只接收 {@link SpecialGearRouter#compile} 编译好的上下文列表，
 * 按生命周期钩子遍历调度已注册的 Handler。</p>
 *
 * <p>用法：在玩家 Tick/Attack/Hurt 事件中调用对应的静态方法。</p>
 */
public final class SpecialMechanismEngine {

    private SpecialMechanismEngine() {}

    /** 每玩家 tick 调度 */
    public static void onPlayerTick(Player player, Container equipmentCont) {
        List<SpecialGearContext> ctxList = SpecialGearRouter.compile(player, equipmentCont);
        for (var handler : HandlerRegistry.TICK_HANDLERS) {
            handler.accept(player, ctxList);
        }
    }

    /** 玩家攻击命中时调度 */
    public static void onPlayerAttack(Player player, LivingEntity target, Container equipmentCont) {
        List<SpecialGearContext> ctxList = SpecialGearRouter.compile(player, equipmentCont);
        for (var handler : HandlerRegistry.ATTACK_HANDLERS) {
            handler.accept(player, target, ctxList);
        }
    }
}
