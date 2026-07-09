package net.minecraft.client.yiz.attribute.inheritance;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 属性化继承解析引擎 —— {@code Holder<Attribute>} 版的 {@code dfs / dfsValues}。
 *
 * <p>从 {@link AttributeCardRegistry} 读取饰品定义，递归解析某饰品 id 的完整数值属性集与
 * FLAG 集，支持合成链继承、suppress 替换、环检测、菱形幂等。算法忠实移植自下游
 * {@code AccessoryFlags.dfs / dfsValues}，把 {@code EffectTag} 替换为
 * {@code Holder<Attribute>}（数值）/ {@code String}（flag）。</p>
 *
 * <h3>核心规则（与下游完全一致）</h3>
 * <ol>
 *   <li><b>返回值风格</b>（非共享累加器）：suppress 只作用于本子树累加结果，避免多线合并时
 *       误伤兄弟子树。例如气球束 = 云气球 + 暴雪气球 + 沙气球，沙暴瓶的
 *       {@code suppress JUMP_CLOUD} 不应撕掉云气球贡献的云跳。</li>
 *   <li><b>visited 双重职责</b>：环检测（重复访问返回空）+ 幂等（菱形继承下同一父级只算一次）。</li>
 *   <li><b>数值合并</b>：父级贡献按 {@code Float::sum} 累加；本节点 {@code values} 用
 *       {@code put} <b>覆盖</b>父级同名属性（不是 sum）。</li>
 *   <li><b>FLAG 合并</b>：纯集合 union；本节点 {@code flags} 覆盖语义由 suppress 控制
 *       （suppress 显式 remove，未 suppress 的保留）。</li>
 * </ol>
 *
 * <p>第一切片实时递归、不缓存（饰品少、链浅，性能足够；与下游 {@code AccessoryFlags} 一致）。</p>
 */
public final class AttributeInheritanceGraph {

    private AttributeInheritanceGraph() {}

    // ── 数值属性解析 ─────────────────────────────────────────────

    /**
     * 解析某饰品 id 的完整<b>数值属性集</b>（递归父级 + suppress 替换 + 合并 + 环检测）。
     *
     * @param id 饰品 id
     * @return {@code Holder<Attribute> → Float} 的完整合并结果；id 未注册返回空 map
     */
    public static Map<Holder<Attribute>, Float> resolveValues(int id) {
        return dfsValues(id, new HashSet<>());
    }

    private static Map<Holder<Attribute>, Float> dfsValues(int id, Set<Integer> visited) {
        if (!visited.add(id)) return new HashMap<>();               // 环检测 / 幂等
        AttributeCard card = AttributeCardRegistry.byId(id);
        if (card == null) return new HashMap<>();                    // 非饰品材料
        Map<Holder<Attribute>, Float> acc = new HashMap<>();
        // 1. 合并各父级子树（数值按 sum 累加）
        for (int pid : card.parents()) {
            for (var e : dfsValues(pid, visited).entrySet()) {
                acc.merge(e.getKey(), e.getValue(), Float::sum);
            }
        }
        // 2. suppress：撕掉本节点要替换的父级数值属性
        for (Holder<Attribute> a : card.suppressedValues()) {
            acc.remove(a);
        }
        // 3. 本节点 values 覆盖父级同名属性（put，非 sum）
        for (var e : card.values().entrySet()) {
            acc.put(e.getKey(), e.getValue());
        }
        return acc;
    }

    // ── FLAG 解析 ────────────────────────────────────────────────

    /**
     * 解析某饰品 id 的完整<b> FLAG / 能力 / 触发器集</b>（递归父级 + suppress 替换 + 合并 + 环检测）。
     *
     * @param id 饰品 id
     * @return 完整合并后的 flag 名集合；id 未注册返回空集
     */
    public static Set<String> resolveFlags(int id) {
        return dfsFlags(id, new HashSet<>());
    }

    private static Set<String> dfsFlags(int id, Set<Integer> visited) {
        if (!visited.add(id)) return new LinkedHashSet<>();          // 环检测 / 幂等
        AttributeCard card = AttributeCardRegistry.byId(id);
        if (card == null) return new LinkedHashSet<>();              // 非饰品材料
        Set<String> acc = new LinkedHashSet<>();
        // 1. 合并各父级子树（集合并）
        for (int pid : card.parents()) {
            acc.addAll(dfsFlags(pid, visited));
        }
        // 2. suppress：撕掉本节点要替换的父级 flag
        for (String f : card.suppressedFlags()) {
            acc.remove(f);
        }
        // 3. 本节点 flags 合入
        acc.addAll(card.flags());
        return acc;
    }

    // ── 便捷查询 ─────────────────────────────────────────────────

    /** 玩家是否拥有某 flag（等价下游 {@code AccessoryFlags.has}，但这里只看单饰品 id 的解析结果）。 */
    public static boolean hasFlag(int id, String flag) {
        return resolveFlags(id).contains(flag);
    }

    /** 取某饰品某数值属性的值，不存在返回 0。 */
    public static float getValue(int id, Holder<Attribute> attribute) {
        return resolveValues(id).getOrDefault(attribute, 0f);
    }
}
