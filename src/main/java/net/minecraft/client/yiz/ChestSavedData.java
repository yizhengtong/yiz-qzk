package net.minecraft.client.yiz;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Map;

/**
 * 内部存档数据 — 将所有注册的容器持久化到 DimensionDataStorage。
 * 不对外暴露，通过 {@link ChestDataManager} 访问。
 */
public class ChestSavedData extends SavedData {
    public static final String NAME = "yizmod_chest_data";

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag containers = new CompoundTag();
        for (Map.Entry<String, Container> entry : ChestDataManager.getRegistry().entrySet()) {
            Container container = entry.getValue();
            CompoundTag ct = new CompoundTag();
            ct.putInt("Size", container.getContainerSize());
            ListTag items = new ListTag();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    CompoundTag slotTag = new CompoundTag();
                    slotTag.putByte("Slot", (byte) i);
                    items.add(stack.save(registries, slotTag));
                }
            }
            ct.put("Items", items);
            containers.put(entry.getKey(), ct);
        }
        tag.put("Containers", containers);
        return tag;
    }

    public static ChestSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Containers", CompoundTag.TAG_COMPOUND)) {
            CompoundTag containers = tag.getCompound("Containers");
            for (String key : containers.getAllKeys()) {
                Container container = ChestDataManager.getRegistry().get(key);
                if (container == null) continue;
                CompoundTag ct = containers.getCompound(key);
                ListTag items = ct.getList("Items", Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag slotTag = items.getCompound(i);
                    int slot = slotTag.getByte("Slot") & 0xFF;
                    if (slot >= 0 && slot < container.getContainerSize()) {
                        ItemStack.parse(registries, slotTag).ifPresent(stack -> container.setItem(slot, stack));
                    }
                }
            }
        }
        return new ChestSavedData();
    }
}
