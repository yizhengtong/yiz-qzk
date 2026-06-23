package net.minecraft.client.yiz.item;

import net.minecraft.client.yiz.api.IGeneralItem;
import net.minecraft.world.item.Item;

/**
 * 通用分级物品桩类。
 * 所有阶段共享同一纹理资源，仅 itemId 和 level 不同。
 * 实现 {@link IGeneralItem} → 由 {@code CreativeTabAutoRegistry} 自动分入"物品"创造标签页。
 */
public class StagedItem extends Item implements IGeneralItem {
    private final int level;

    public StagedItem(Properties properties, int level) {
        super(properties);
        this.level = level;
    }

    public int getLevel() { return level; }
}
