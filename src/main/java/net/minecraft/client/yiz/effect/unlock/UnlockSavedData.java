package net.minecraft.client.yiz.effect.unlock;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 将 {@link UnlockManager} 的解锁数据持久化到世界存档。
 * 通过 {@link net.minecraft.world.level.storage.DimensionDataStorage} 读写。
 */
public class UnlockSavedData extends SavedData {

    private static final String DATA_NAME = "yizmodqzk_unlocks";

    public UnlockSavedData() {}

    public UnlockSavedData(CompoundTag tag, HolderLookup.Provider registries) {
        UnlockManager.loadFromNBT(tag);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        UnlockManager.saveToNBT(tag);
        return tag;
    }

    public static SavedData.Factory<UnlockSavedData> factory() {
        return new SavedData.Factory<>(UnlockSavedData::new, UnlockSavedData::new);
    }

    public static String dataName() {
        return DATA_NAME;
    }
}
