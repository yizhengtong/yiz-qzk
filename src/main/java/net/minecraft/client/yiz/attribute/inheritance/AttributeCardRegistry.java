package net.minecraft.client.yiz.attribute.inheritance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 属性化饰品卡注册表 —— 库侧全局表，下游模组在构造器中注册泰拉饰品定义。
 *
 * <p>下游 {@code TerrariaCards.CARDS} 的库侧等价物。线程安全（{@link ConcurrentHashMap}），
 * 支持多模组同时注册。注册后由 {@link AttributeInheritanceGraph} 读取做继承解析。</p>
 *
 * <p><b>注册时机</b>：下游在模组构造器（{@code @Mod} 主类的构造函数）中调用
 * {@link #register(AttributeCard)}，早于任何 {@code tick} / 属性查询。</p>
 */
public final class AttributeCardRegistry {

    /** id → 卡。id 全局唯一（下游约定 acc_&lt;id&gt;，与 terrariaId 对齐）。 */
    private static final Map<Integer, AttributeCard> CARDS = new ConcurrentHashMap<>();

    private AttributeCardRegistry() {}

    /** 注册一张饰品卡。重复注册同一 id 会覆盖。 */
    public static void register(AttributeCard card) {
        CARDS.put(card.id(), card);
    }

    /** 批量注册。 */
    public static void registerAll(AttributeCard... cards) {
        for (AttributeCard c : cards) register(c);
    }

    /** 按 id 查卡，不存在返回 null（父级中的非饰品材料）。 */
    public static AttributeCard byId(int id) {
        return CARDS.get(id);
    }

    /** 是否注册了某 id。 */
    public static boolean exists(int id) {
        return CARDS.containsKey(id);
    }

    /** 已注册的全部卡（只读视图）。 */
    public static Map<Integer, AttributeCard> all() {
        return java.util.Collections.unmodifiableMap(CARDS);
    }
}
