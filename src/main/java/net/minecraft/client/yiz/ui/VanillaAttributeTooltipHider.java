package net.minecraft.client.yiz.ui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;

/**
 * 屏蔽本模组自定义属性在原版 tooltip 中的属性行。
 *
 * <p>本模组所有自定义属性（{@code yizmodqzk:*}）已由 {@link ItemAttributeDisplay}
 * 在 tooltip 顶部面板统一格式化显示，若再由 NeoForge 的 {@code AttributeUtil.addAttributeTooltips}
 * 渲染一遍原版属性行，会造成"顶部面板 + 下方原版区"双重显示。</p>
 *
 * <p>本类监听 {@link GatherSkippedAttributeTooltipsEvent}，将物品上所有命名空间为
 * {@code yizmodqzk} 的属性的 modifier 标记为 skipped，让 NeoForge 跳过它们的原版属性行。
 * 原版属性（攻击力、护甲等）不受影响，仍在原版区正常显示。</p>
 *
 * <p>注意：被 skip 的只是 tooltip 显示，属性的实际效果完全保留（注册为原版 Attribute
 * 一等公民，{@code getAttributeValue} 照常工作）。</p>
 */
public final class VanillaAttributeTooltipHider {

    private VanillaAttributeTooltipHider() {}

    /** 本模组命名空间，匹配此命名空间的属性一律不在原版 tooltip 显示。 */
    private static final String HIDE_NAMESPACE = "yizmodqzk";

    @SubscribeEvent
    public static void onGatherSkipped(GatherSkippedAttributeTooltipsEvent event) {
        ItemStack stack = event.getStack();
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            // 属性 Holder 的 key 命名空间是 yizmodqzk → skip 其 modifier id
            var keyOpt = entry.attribute().unwrapKey();
            if (keyOpt.isEmpty()) continue;
            ResourceLocation attrKey = keyOpt.get().location();
            if (HIDE_NAMESPACE.equals(attrKey.getNamespace())) {
                event.skipId(entry.modifier().id());
            }
        }
    }
}
