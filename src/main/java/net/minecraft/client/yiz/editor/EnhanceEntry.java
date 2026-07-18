package net.minecraft.client.yiz.editor;

import net.minecraft.world.item.ItemStack;

/**
 * 强化条目 — 属性型或标签型。
 * <p>每个技能/被动物品最多提供 6 个条目，对应 GUI 中 6 个加强槽。</p>
 */
public sealed interface EnhanceEntry {

    String key();
    String displayName();

    /** A 类：数值属性（+/- 等级，每级 +10%） */
    record Attribute(EditableAttribute attr) implements EnhanceEntry {
        @Override public String key() { return attr.id(); }
        @Override public String displayName() { return attr.displayName(); }
        /** 计算加强后的数值：base × (1 + level × 10%) */
        public double compute(ItemStack stack, int level) {
            double base = attr.getter().apply(stack);
            return base * (1.0 + level * 0.10);
        }
    }

    /** B 类：机制标签（0=未激活, 1=激活）。点击切换，激活时消耗经验。 */
    record Tag(String key, String displayName, String description) implements EnhanceEntry {
        @Override public String displayName() { return displayName; }
    }
}
