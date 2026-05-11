package net.minecraft.client.yiz.effect;

import net.minecraft.client.yiz.effect.perception.PerceptionMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 效果上下文
 * 传递效果生效所需的所有信息。
 *
 * @param entity      效果持有者
 * @param target      目标实体（可能为 null）
 * @param level       所在世界
 * @param itemStack   效果来源物品
 * @param effect      当前效果
 * @param activeMode  激活的感知方式
 * @param position    位置
 * @param metadata    附加元数据
 */
public record EffectContext(
    LivingEntity entity,
    Entity target,
    Level level,
    ItemStack itemStack,
    AbstractEffect effect,
    PerceptionMode activeMode,
    Vec3 position,
    Map<String, Object> metadata
) {
    /**
     * 健康值修改元数据键名常量。
     */
    public static final class MetaKeys {
        /** 修改量来源效果 ID（String） */
        public static final String MOD_SOURCE_EFFECT = "health_mod_source_effect";
        /** 原始伤害值（Double） */
        public static final String MOD_RAW_DAMAGE = "health_mod_raw_damage";
        /** 原始伤害源（DamageSource） */
        public static final String MOD_DAMAGE_SOURCE = "health_mod_damage_source";
        /** 是否为强制执行（Boolean） */
        public static final String MOD_ENFORCED = "health_mod_enforced";
        /** BAN_HEALING 削减系数（Float，0.0~1.0） */
        public static final String MOD_BAN_HEALING_FACTOR = "health_mod_ban_healing_factor";
        /** 触发器的 tick 计数（Integer） */
        public static final String MOD_TRIGGER_TICK = "health_mod_trigger_tick";
        /** 本次修改的 Delta 偏移量（Float） */
        public static final String MOD_DELTA_AMOUNT = "health_mod_delta_amount";

        private MetaKeys() {}
    }
    public EffectContext {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
    }

    /**
     * 创建攻击上下文。
     */
    public static EffectContext createAttackContext(LivingEntity attacker, Entity target) {
        return new EffectContext(
            attacker,
            target,
            attacker.level(),
            attacker.getMainHandItem(),
            null,
            null,
            attacker.position(),
            new HashMap<>()
        );
    }

    /**
     * 创建效果上下文。
     */
    public static EffectContext create(
        LivingEntity entity,
        Entity target,
        AbstractEffect effect,
        PerceptionMode activeMode
    ) {
        return new EffectContext(
            entity,
            target,
            entity.level(),
            entity.getMainHandItem(),
            effect,
            activeMode,
            entity.position(),
            new HashMap<>()
        );
    }

    /**
     * 基础创建（不含效果）。
     */
    public static EffectContext create(LivingEntity entity, Entity target) {
        return createAttackContext(entity, target);
    }

    public EffectContext withEffect(AbstractEffect newEffect) {
        return new EffectContext(entity, target, level, itemStack, newEffect, activeMode, position, metadata);
    }

    public EffectContext withMetadata(String key, Object value) {
        Map<String, Object> newMeta = new HashMap<>(metadata);
        newMeta.put(key, value);
        return new EffectContext(entity, target, level, itemStack, effect, activeMode, position, newMeta);
    }

    /**
     * 批量设置健康值修改元数据。
     */
    public EffectContext withHealthModMetadata(
        String sourceEffectId, double rawDamage, boolean enforced
    ) {
        Map<String, Object> newMeta = new HashMap<>(metadata);
        newMeta.put(MetaKeys.MOD_SOURCE_EFFECT, sourceEffectId);
        newMeta.put(MetaKeys.MOD_RAW_DAMAGE, rawDamage);
        newMeta.put(MetaKeys.MOD_ENFORCED, enforced);
        return new EffectContext(entity, target, level, itemStack, effect, activeMode, position, newMeta);
    }
}
