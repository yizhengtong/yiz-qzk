package net.minecraft.client.yiz.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.yiz.editor.EditableAttribute;
import net.minecraft.client.yiz.editor.EnhanceEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 可强化物品接口。
 *
 * <p>主动技能和被动物品均可实现此接口，返回可强化属性列表与可用标签。
 * GUI 通过 {@link #getEnhanceEntries} 获取全部强化条目（最多 6 条）填充加强槽。</p>
 *
 * <h3>设计分层</h3>
 * <ul>
 *   <li>{@link #getEnhanceableAttributes} — A 类：数值属性（自动检测，90% 无需覆写）</li>
 *   <li>{@link #getProvidedTags} — 被动物品对外提供的触发标签池</li>
 *   <li>{@link #getEnhanceEntries} — 汇总：属性 + 标签 → GUI 6 格加强槽</li>
 * </ul>
 */
public interface IEnhanceable {

    /** 无需覆写：自动检测物品上所有非零的可编辑属性 */
    default List<EnhanceEntry.Attribute> getEnhanceableAttributes(ItemStack stack) {
        List<EnhanceEntry.Attribute> list = new ArrayList<>();
        for (var attr : EditableAttribute.getAll()) {
            if (Math.abs(attr.getter().apply(stack)) > 0.001) {
                list.add(new EnhanceEntry.Attribute(attr));
            }
        }
        return list;
    }

    /**
     * 此物品对外提供的触发标签。
     * 被动物品覆写此方法，声明自己可为技能提供的标签。
     */
    default List<String> getProvidedTags(ItemStack stack) { return List.of(); }

    /**
     * 获取此物品可用作强化的全部条目（属性 + 标签）。
     * <p>优先级：{@link SkillEnhanceConfig} 预定义映射 > 自动检测属性 + 被动标签。</p>
     */
    default List<EnhanceEntry> getEnhanceEntries(ItemStack stack, Player player) {
        // 1. 优先查 SkillEnhanceConfig（用户配置的技能→强化物品映射）
        var configEntries = net.minecraft.client.yiz.editor.SkillEnhanceConfig.getEnhancementsFor(stack);
        if (!configEntries.isEmpty()) return configEntries;

        // 2. 回退：自动检测属性 + 被动标签
        List<EnhanceEntry> entries = new ArrayList<>();
        entries.addAll(getEnhanceableAttributes(stack));
        if (player != null) {
            var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
            if (data != null) {
                for (int i = 0; i < 3; i++) {
                    ItemStack passive = data.passiveLoad().getItem(i);
                    if (!passive.isEmpty() && passive.getItem() instanceof IEnhanceable pe) {
                        for (String tagKey : pe.getProvidedTags(passive)) {
                            String name = net.minecraft.client.yiz.editor.EnhanceTagRegistry.displayName(tagKey);
                            String desc = net.minecraft.client.yiz.editor.EnhanceTagRegistry.description(tagKey);
                            entries.add(new EnhanceEntry.Tag(tagKey, name, desc));
                        }
                    }
                }
            }
        }
        if (entries.size() > 6) entries = entries.subList(0, 6);
        return entries;
    }
}
