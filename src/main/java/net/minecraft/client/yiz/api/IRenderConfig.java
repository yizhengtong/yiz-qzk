package net.minecraft.client.yiz.api;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

/**
 * Item 自渲染配置接口 — 武器基类实现此接口，渲染端统一读取。
 *
 * <p>替代渲染端硬编码 {@code instanceof} 检查。渲染系统只需判断
 * {@code stack.getItem() instanceof IRenderConfig} 即可读取所有渲染参数。</p>
 *
 * <h3>实现建议</h3>
 * <p>武器基类从 {@code WeaponProfile.tier(level)} 读取对应品质的光效色和光效类型，
 * 无需子类重复实现。</p>
 */
public interface IRenderConfig {

    /**
     * 描边光效色。
     * @return null 表示使用动画色板（传说品质）
     */
    @Nullable
    Vector4f getGlowColor(ItemStack stack);

    /**
     * 光效类型。
     * @return 0 = 静态色（使用 getGlowColor），5 = 动画色板
     */
    default int getGlowType(ItemStack stack) { return 0; }

    /**
     * 品质颜色（tooltip 品质文字颜色，如 §f 白色）。

     * @return RGB 颜色值，默认 0xFFFFFF
     */
    default int getQualityColor(ItemStack stack) { return 0xFFFFFF; }

    /**
     * 渲染用等级。
     * @return 1-based 等级，默认 1
     */
    default int getRenderLevel(ItemStack stack) { return 1; }
}
