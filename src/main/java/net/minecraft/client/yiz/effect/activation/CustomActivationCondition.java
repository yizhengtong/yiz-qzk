package net.minecraft.client.yiz.effect.activation;

/**
 * 自定义生效条件接口
 * 第三方模组实现此接口来自定义生效判断逻辑。
 */
public interface CustomActivationCondition extends ActivationCondition {

    @Override
    String getConditionName();
}
