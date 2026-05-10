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
}
