package net.minecraft.client.yiz.ui;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 属性行 tooltip 数据组件 —— 携带 {@code attrId/name/value/color}。
 *
 * <p>由 {@code GatherComponents} 事件插入 tooltip，引擎据此构造
 * {@link AttributeLineClientComponent}（图标+文字一行，行高自适应图标，不溢出相邻行）。</p>
 */
public record AttributeLineTooltipComponent(
    String attrId, String name, String value, int color) implements TooltipComponent {}
