package net.minecraft.client.yiz.effect.rarity;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * 稀有度枚举
 * 定义五级稀有度及其显示颜色。
 */
public enum Rarity {
    COMMON("平凡", "effect.rarity.common", 0xFFAAAAAA),
    UNCOMMON("优秀", "effect.rarity.uncommon", 0xFF55FF55),
    RARE("精良", "effect.rarity.rare", 0xFF5555FF),
    EPIC("史诗", "effect.rarity.epic", 0xFFAA55FF),
    LEGENDARY("传说", "effect.rarity.legendary", 0xFFFFAA00);

    private final String chineseName;
    private final String translationKey;
    private final int displayColor;

    Rarity(String chineseName, String translationKey, int displayColor) {
        this.chineseName = chineseName;
        this.translationKey = translationKey;
        this.displayColor = displayColor;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public int getDisplayColor() {
        return displayColor;
    }

    public TextColor getTextColor() {
        return TextColor.fromRgb(displayColor);
    }

    public MutableComponent getDisplayName() {
        return Component.translatable(translationKey);
    }
}
