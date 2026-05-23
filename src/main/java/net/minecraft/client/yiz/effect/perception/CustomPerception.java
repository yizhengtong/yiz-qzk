package net.minecraft.client.yiz.effect.perception;

import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiPredicate;

/**
 * 自定义感知接口
 * 第三方模组实现此接口来自定义感知逻辑。
 */
public interface CustomPerception extends PerceptionMode {

    @Override
    String getTypeName();

    @Override
    PerceptionType getPerceptionType();

    /**
     * 使用谓词快速创建自定义感知实例。
     */
    static CustomPerception of(String typeName, BiPredicate<LivingEntity, EffectContext> predicate) {
        return new CustomPerception() {
            @Override
            public boolean check(LivingEntity entity, EffectContext context) {
                return predicate.test(entity, context);
            }

            @Override
            public String getTypeName() {
                return typeName;
            }

            @Override
            public PerceptionType getPerceptionType() {
                return PerceptionType.CUSTOM;
            }
        };
    }
}
