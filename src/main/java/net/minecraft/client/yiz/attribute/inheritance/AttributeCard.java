package net.minecraft.client.yiz.attribute.inheritance;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 属性化饰品卡 —— {@code Holder<Attribute>} 版的饰品定义，供下游模组注册泰拉饰品的数据。
 *
 * <p>本类是下游 {@code TerrariaCards.Card} 在库侧的等价物，用于驱动
 * {@link AttributeInheritanceGraph} 的继承解析（合并 / suppress 替换 / 环检测 / 菱形幂等）。</p>
 *
 * <h3>与下游 EffectTag 体系的对应关系</h3>
 * <ul>
 *   <li>{@code values} —— 数值属性，迁自下游 {@code EffectTag} 的数值类枚举
 *       （damage_reduction / armor / move_speed / jump_* 等），改用库 {@code Holder<Attribute>}。</li>
 *   <li>{@code suppressedValues} —— 撕掉父级继承来的同名数值属性，对应下游
 *       {@code suppressedEffects} 中的数值项。</li>
 *   <li>{@code flags} —— FLAG / 能力 / 触发器（如 FALL_IMMUNE / HOVER / DASH_ATTACK），
 *       原生 Attribute 表达不了，保留下游语义用字符串名。跳型标记
 *       （JUMP_CLOUD / JUMP_BLIZZARD / JUMP_SANDSTORM，无运行时消费方，仅驱动合成链）也放这里。</li>
 *   <li>{@code suppressedFlags} —— 撕掉父级继承来的 flag，对应下游
 *       {@code suppressedEffects} 中的 flag 项（如沙暴瓶 suppress JUMP_CLOUD）。</li>
 *   <li>{@code parents} —— 合成父级饰品 id 列表，驱动自动继承。</li>
 * </ul>
 *
 * <p><b>不可变 record</b>，线程安全。下游在模组构造器中通过
 * {@link AttributeCardRegistry#register(AttributeCard)} 注册。</p>
 *
 * @param id                饰品内部 id（如 53 = 云朵瓶）
 * @param values            数值属性（{@code Holder<Attribute> → Float}）
 * @param suppressedValues  要撕掉的父级继承数值属性
 * @param flags             FLAG / 能力 / 触发器名集合
 * @param suppressedFlags   要撕掉的父级继承 flag
 * @param parents           合成父级饰品 id 列表
 */
public record AttributeCard(
    int id,
    Map<Holder<Attribute>, Float> values,
    Set<Holder<Attribute>> suppressedValues,
    Set<String> flags,
    Set<String> suppressedFlags,
    List<Integer> parents
) {

    /** 空卡（非饰品材料用）：所有集合为空、无父级。 */
    public static AttributeCard empty(int id) {
        return new AttributeCard(id, Map.of(), Set.of(), Set.of(), Set.of(), List.of());
    }
}
