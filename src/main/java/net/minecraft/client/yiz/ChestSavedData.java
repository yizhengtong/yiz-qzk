package net.minecraft.client.yiz;

import net.minecraft.client.yiz.impl.WorldContainerDataStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 内部存档数据 — 已被 {@link WorldContainerDataStorage} 取代。
 *
 * <p>此类保留仅为避免已有存档数据丢失，作为迁移桥梁。
 * 新的保存/加载逻辑统一由 WorldContainerDataStorage 处理。</p>
 *
 * @deprecated 直接使用 {@link WorldContainerDataStorage} 替代。
 */
@Deprecated
public class ChestSavedData extends SavedData {

    public static final String NAME = "yizmod_chest_data";

    public ChestSavedData() {}

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return tag;
    }

    public static ChestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new ChestSavedData();
    }
}
