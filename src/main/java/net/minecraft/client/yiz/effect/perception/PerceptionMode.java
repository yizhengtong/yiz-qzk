package net.minecraft.client.yiz.effect.perception;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * 感知方式接口
 * 定义效果如何被"感知"（即效果与世界的绑定方式）。
 */
public interface PerceptionMode {

    /**
     * 检查当前实体是否满足此感知条件。
     */
    boolean check(LivingEntity entity, EffectContext context);

    /**
     * 获取感知类型名称（词缀/天赋/随影/自定义）。
     */
    String getTypeName();

    /**
     * 获取感知方式标识。
     */
    PerceptionType getPerceptionType();

    enum PerceptionType {
        ITEM,
        ENTITY,
        CONTAINER,
        CUSTOM
    }
}
