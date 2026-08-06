package net.minecraft.client.yiz.api;

import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 连击攻击助手 — 将连击额外攻击路由到 Player.attack() 管道。
 *
 * <p>使用与 {@link CounterAttackRegistry} 相同的模式：
 * 临时 ATTACK_DAMAGE 缩放修饰符 + attackStrengthTicker 反射重置，
 * 然后调用 {@link Player#attack}，让完整攻击管道（暴击、附魔、横扫等）生效。</p>
 *
 * <p>ThreadLocal COMBO_ATTACKING 作为递归守卫，替代原先基于 NBT 的
 * {@code yiz:combo_hitting} 标志，防止连击派生的 hurt() 重复触发连击。</p>
 */
public final class ComboAttackHelper {

    private static final ResourceLocation COMBO_MODIFIER_ID =
            ResourceLocation.parse("yizmodqzk:combo_attack");

    /**
     * 连击递归守卫。
     * 当连击循环正在执行时设为 true，防止 {@code onHurtReturn}
     * 中的连击检测对派生 hurt() 再次触发。
     */
    private static final ThreadLocal<Boolean> COMBO_ATTACKING =
            ThreadLocal.withInitial(() -> false);

    private ComboAttackHelper() {}

    /** 检查当前线程是否正在执行连击攻击。 */
    public static boolean isComboAttacking() {
        return COMBO_ATTACKING.get();
    }

    /**
     * 执行连击攻击序列。
     * 由 {@code LivingEntityMixin.yizmodqzk$onHurtReturn} 在检测到连击属性时调用。
     *
     * @param player 攻击方玩家（必须是 ServerPlayer，已在调用方判空）
     * @param target 要连击的目标实体
     */
    public static void executeCombo(ServerPlayer player, LivingEntity target) {
        if (player.level().isClientSide()) return;
        if (target.isRemoved() || !target.isAlive()) return;

        // 读取连击属性 — x = 1 + (y - 1), y 为属性值
        // y=0(未配) → 1次, y=1 → 1次, y=2 → 2次, y=3 → 3次
        double cValue = player.getAttributeValue(YizAttributes.COMBO_VALUE);
        if (cValue <= 0) cValue = 100.0;
        double rawCount = player.getAttributeValue(YizAttributes.COMBO_COUNT);
        int count = rawCount > 0 ? Math.max(1, (int) rawCount) : 1;

        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;

        // ADD_MULTIPLIED_TOTAL: final = base × (1 + amount)
        // 需要 final = base × comboValue/100 → amount = comboValue/100 - 1.0
        double modifierAmount = cValue / 100.0 - 1.0;

        AttributeModifier mod = new AttributeModifier(
                COMBO_MODIFIER_ID, modifierAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        // 设置递归守卫
        COMBO_ATTACKING.set(true);
        // 防御性先移除旧修饰符：连续触发 hurt 时，
        // 上一次 executeCombo 的修饰符可能未及时移除 → addTransientModifier 会抛
        // "Modifier is already applied on this attribute!"（AttributeInstance.addModifier:79）。
        attack.removeModifier(COMBO_MODIFIER_ID);
        attack.addTransientModifier(mod);

        // 反射拿到 attackStrengthTicker，循环中复用
        java.lang.reflect.Field tickerField = null;
        try {
            tickerField = Player.class.getDeclaredField("attackStrengthTicker");
            tickerField.setAccessible(true);
        } catch (Exception ignored) {}

        try {
            for (int i = 0; i < count; i++) {
                if (target.isRemoved() || !target.isAlive()) break;

                target.invulnerableTime = 0;

                if (tickerField != null) {
                    try {
                        tickerField.setInt(player, (int) player.getCurrentItemAttackStrengthDelay());
                    } catch (Exception ignored) {}
                }

                player.attack(target);
            }
        } finally {
            attack.removeModifier(COMBO_MODIFIER_ID);
            COMBO_ATTACKING.remove();
        }
    }
}
