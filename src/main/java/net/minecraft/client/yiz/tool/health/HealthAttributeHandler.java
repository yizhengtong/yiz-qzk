package net.minecraft.client.yiz.tool.health;

import net.minecraft.client.yiz.attribute.ModAttributes;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 属性健康值处理器
 * 自动将自定义属性映射到健康值修改事件中。
 *
 * <p>在 {@link HealthModificationEvent} 发布时自动添加以下修正器：</p>
 * <ul>
 *   <li>灵梦固定伤害 → 固定数值的 ADDITIVE 伤害修正器</li>
 *   <li>灵梦百分比伤害 → 目标最大生命值百分比的 ADDITIVE 伤害修正器</li>
 * </ul>
 */
public final class HealthAttributeHandler {

    private static final ResourceLocation FLAT_DAMAGE_ID =
        ResourceLocation.parse("yizmodqzk:reimu_flat_damage");
    private static final ResourceLocation PERCENT_DAMAGE_ID =
        ResourceLocation.parse("yizmodqzk:reimu_percent_damage");

    private static boolean registered = false;

    private HealthAttributeHandler() {}

    /**
     * 注册到 NeoForge 事件总线。
     * 可在 Mod 构造器中调用。
     */
    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.register(HealthAttributeHandler.class);
    }

    /**
     * 方法A：将攻击者的灵梦属性（固定伤害 + 百分比伤害）应用到目标实体。
     * <p>
     * 读取攻击者的 {@link ModAttributes#REIMU_FLAT_DAMAGE} 和
     * {@link ModAttributes#REIMU_PERCENT_DAMAGE} 属性值，
     * 通过 ASM Agent 的健康值修改流水线传递到目标实体上。
     * </p>
     * <p>
     * 该方法不直接修改生命值，而是创建 {@link EffectContext} 并委托
     * {@link HealthModificationManager#executeModification} 触发完整流程：
     * 发布 {@link HealthModificationEvent} → {@link #onHealthModification} 订阅者
     * 读取属性 → 添加修正器 → 数值聚合 → 实体应用。
     * </p>
     *
     * @param attacker 攻击者（属性持有者）
     * @param target   目标实体（伤害承受方）
     */
    public static void applyReimuAttributes(LivingEntity attacker, LivingEntity target) {
        double flatDmg = attacker.getAttributeValue(ModAttributes.REIMU_FLAT_DAMAGE);
        double pctDmg = attacker.getAttributeValue(ModAttributes.REIMU_PERCENT_DAMAGE);
        if (flatDmg <= 0 && pctDmg <= 0) return;

        EffectContext context = EffectContext.createAttackContext(attacker, target);
        HealthModificationManager.executeModification(target, context);
    }

    @SubscribeEvent
    public static void onHealthModification(HealthModificationEvent event) {
        EffectContext context = event.getContext();
        if (context == null) return;

        LivingEntity source = context.entity();
        if (source == null) return;

        // ---- 1. 灵梦固定伤害 ----
        // 此属性仅存在于玩家实体上，使用 getAttribute 避免非玩家实体抛出异常
        var flatAttr = source.getAttribute(ModAttributes.REIMU_FLAT_DAMAGE);
        if (flatAttr != null && flatAttr.getValue() > 0) {
            event.addModifier(new FlatDamageModifier(flatAttr.getValue()));
        }

        // ---- 2. 灵梦百分比伤害 ----
        var pctAttr = source.getAttribute(ModAttributes.REIMU_PERCENT_DAMAGE);
        if (pctAttr != null && pctAttr.getValue() > 0) {
            event.addModifier(new PercentDamageModifier(pctAttr.getValue()));
        }
    }

    // ==================== 内置修正器 ====================

    /**
     * 灵梦固定伤害修正器。
     * 每点数值直接作为固定伤害值。
     */
    private record FlatDamageModifier(double amount) implements HealthModifier {
        @Override
        public ResourceLocation getId() { return FLAT_DAMAGE_ID; }

        @Override
        public double getModificationAmount(LivingEntity entity, EffectContext context) {
            return -amount;
        }

        @Override
        public ModifierType getType() { return ModifierType.DAMAGE; }

        @Override
        public ModificationMode getMode() { return ModificationMode.ADDITIVE; }

        @Override
        public int getPriority() { return 100; }
    }

    /**
     * 灵梦百分比伤害修正器。
     * 每点数值为目标最大生命值的百分比伤害。
     * 例：10 → 取目标最大生命值的 10% 作为伤害
     */
    private record PercentDamageModifier(double percent) implements HealthModifier {
        @Override
        public ResourceLocation getId() { return PERCENT_DAMAGE_ID; }

        @Override
        public double getModificationAmount(LivingEntity entity, EffectContext context) {
            double damage = entity.getMaxHealth() * percent / 100.0;
            return -damage;
        }

        @Override
        public ModifierType getType() { return ModifierType.DAMAGE; }

        @Override
        public ModificationMode getMode() { return ModificationMode.ADDITIVE; }

        @Override
        public int getPriority() { return 90; }
    }
}
