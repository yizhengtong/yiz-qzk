package net.minecraft.client.yiz.handler;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 特殊装备 Handler 注册表（模块 D 基础设施）。
 *
 * <p>Handler 是无状态函数，接收编译好的上下文执行逻辑。
 * 当前提供占位注册入口，Phase 2 实现具体钩子分发。</p>
 */
public final class HandlerRegistry {

    private HandlerRegistry() {}

    // ── 占位注册表（Phase 2 接入） ──

    /** Tick Handler：每玩家 tick 调用 */
    public static final List<BiConsumer<Player, List<SpecialGearContext>>> TICK_HANDLERS
        = new CopyOnWriteArrayList<>();

    /** Attack Handler：每次玩家攻击时调用 */
    public static final List<TriConsumer<Player, LivingEntity, List<SpecialGearContext>>> ATTACK_HANDLERS
        = new CopyOnWriteArrayList<>();

    // ── 辅助 ──

    @FunctionalInterface
    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
