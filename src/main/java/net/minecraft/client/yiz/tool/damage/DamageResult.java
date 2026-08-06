package net.minecraft.client.yiz.tool.damage;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 伤害结果数据模型
 * 封装伤害计算的结果，支持链式修改。
 *
 * @param finalDamage  最终伤害值
 * @param baseDamage   基础伤害值
 * @param source       伤害来源
 * @param tags         伤害标签集合
 * @param knockback    击退强度（默认0）
 * @param stunDuration 硬直时长（秒，默认0）
 * @param metadata     附加元数据
 */
public record DamageResult(
    double finalDamage,
    double baseDamage,
    ResourceLocation source,
    Set<DamageTag> tags,
    double knockback,
    double stunDuration,
    Map<String, Object> metadata
) {
    /**
     * 简化构造函数（无标签、无击退、无硬直）。
     */
    public DamageResult(double finalDamage, double baseDamage, ResourceLocation source) {
        this(finalDamage, baseDamage, source, new HashSet<>(), 0.0, 0.0, new HashMap<>());
    }

    /**
     * 简化构造函数（带标签）。
     */
    public DamageResult(double finalDamage, double baseDamage, ResourceLocation source, Set<DamageTag> tags) {
        this(finalDamage, baseDamage, source, tags, 0.0, 0.0, new HashMap<>());
    }

    /**
     * 紧凑构造器：防御性拷贝可变集合，防止外部修改。
     */
    public DamageResult {
        tags = Set.copyOf(tags);
        metadata = Map.copyOf(metadata);
    }

    /**
     * 检查是否包含特定标签。
     */
    public boolean hasTag(DamageTag tag) {
        return tags.contains(tag);
    }

    /**
     * 检查是否包含任意指定标签。
     */
    public boolean hasAnyTag(DamageTag... checkTags) {
        for (DamageTag tag : checkTags) {
            if (tags.contains(tag)) return true;
        }
        return false;
    }

    /**
     * 链式方法：添加标签。
     */
    public DamageResult withTag(DamageTag tag) {
        Set<DamageTag> newTags = new HashSet<>(tags);
        newTags.add(tag);
        return new DamageResult(finalDamage, baseDamage, source, newTags, knockback, stunDuration, metadata);
    }

    /**
     * 链式方法：设置标签集合。
     */
    public DamageResult withTags(Set<DamageTag> newTags) {
        return new DamageResult(finalDamage, baseDamage, source, newTags, knockback, stunDuration, metadata);
    }

    /**
     * 链式方法：设置击退。
     */
    public DamageResult withKnockback(double knockback) {
        return new DamageResult(finalDamage, baseDamage, source, tags, knockback, stunDuration, metadata);
    }

    /**
     * 链式方法：设置硬直。
     */
    public DamageResult withStun(double durationSeconds) {
        return new DamageResult(finalDamage, baseDamage, source, tags, knockback, durationSeconds, metadata);
    }

    /**
     * 链式方法：伤害倍率修正。
     */
    public DamageResult multiply(double multiplier) {
        return new DamageResult(
            finalDamage * multiplier, baseDamage, source, tags, knockback, stunDuration, metadata
        );
    }

    /**
     * 链式方法：伤害加法修正。
     */
    public DamageResult add(double amount) {
        return new DamageResult(
            finalDamage + amount, baseDamage, source, tags, knockback, stunDuration, metadata
        );
    }

    /**
     * 链式方法：添加元数据。
     */
    public DamageResult withMetadata(String key, Object value) {
        Map<String, Object> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return new DamageResult(finalDamage, baseDamage, source, tags, knockback, stunDuration, newMetadata);
    }

    /**
     * 获取伤害类型标签。
     */
    public String getDamageType() {
        return (String) metadata.getOrDefault("damage_type", "generic");
    }

    /**
     * 伤害通知（用于发送伤害提示）。
     */
    public double getActualDamage() {
        return Math.max(0, finalDamage);
    }
}
