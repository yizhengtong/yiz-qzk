package net.minecraft.client.yiz.effect.parent;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 五大父类枚举
 * 作为效果的分类标签，仅作推荐用途。
 */
public enum ParentType {
    ECHO("残响", "effect.parent.echo", "推荐用于攻击类效果"),
    INSCRIPTION("铭刻", "effect.parent.inscription", "推荐用于回复类效果"),
    MANIFESTATION("显化", "effect.parent.manifestation", "推荐用于机制类效果"),
    ORIGIN("本形", "effect.parent.origin", "推荐用于防御类效果"),
    ASCENSION("升灵", "effect.parent.ascension", "推荐用于被动类效果");

    private final String chineseName;
    private final String translationKey;
    private final String recommendedUse;

    ParentType(String chineseName, String translationKey, String recommendedUse) {
        this.chineseName = chineseName;
        this.translationKey = translationKey;
        this.recommendedUse = recommendedUse;
    }

    public String getChineseName() {
        return chineseName;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getRecommendedUse() {
        return recommendedUse;
    }

    public MutableComponent getDisplayName() {
        return Component.translatable(translationKey);
    }
}
