package net.minecraft.client.yiz.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 武器特殊行为标准接口 — 注册时注入，运行时由武器基类统一分发。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * StagedWeaponRegistration.create(...)
 *     .withAbility(new DashAbility())
 *     .register(...);
 * }</pre>
 *
 * <h3>Host 接口</h3>
 * <p>武器基类实现 {@link Host} 以接收注入的 Ability 列表。
 * 基类的 use/hit 等钩子中遍历 abilities 分发调用。</p>
 */
public interface IWeaponAbility {

    /** 武器命中实体时触发（近战攻击）。 */
    default void onEntityHit(Player attacker, LivingEntity target, ItemStack stack) {}

    /** 武器主手右键时触发。 */
    default void onWeaponUse(Level level, Player player, InteractionHand hand, ItemStack stack) {}

    /** 召唤武器攻击时触发。 */
    default void onSummonAttack(Player attacker, LivingEntity target, ItemStack stack) {}

    // ═══════════════════════════════════════════════════════════
    //  Host — 武器基类实现此接口以接收 Ability 注入
    // ═══════════════════════════════════════════════════════════

    /** 由注册系统调用的注入接口。武器基类实现此接口。 */
    interface Host {
        void yizweapon$setAbilities(List<IWeaponAbility> abilities);
    }
}
