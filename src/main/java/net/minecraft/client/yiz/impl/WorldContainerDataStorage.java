package net.minecraft.client.yiz.impl;

import net.minecraft.client.yiz.api.ContainerDataStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link DimensionDataStorage} 的容器持久化实现。
 *
 * <p>数据格式（按 namespace 隔离存储）：</p>
 * <pre>{@code
 * {
 *   "DataVersion": 1,
 *   "mod_a": {
 *     "tombstone_data": { "Size": 27, "Items": [...] },
 *     "backpack":       { "Size": 54, "Items": [...] }
 *   },
 *   "mod_b": {
 *     "my_container":   { "Size": 18, "Items": [...] }
 *   }
 * }
 * }</pre>
 */
public class WorldContainerDataStorage extends SavedData implements ContainerDataStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger("yizmodqzk:ContainerStorage");
    private static final String NAME = "yizmod_chest_data";
    static final int CURRENT_VERSION = 1;

    public static int currentVersion() {
        return CURRENT_VERSION;
    }

    /** 按 namespace → key → Container 组织 */
    private final Map<String, Map<String, Container>> registry = new ConcurrentHashMap<>();

    public WorldContainerDataStorage() {}

    // ══════════════════════════════════════════════════════════════════
    //  ContainerDataStorage 接口实现
    // ══════════════════════════════════════════════════════════════════

    @Override
    public AutoCloseable register(String namespace, String key, Container container) {
        registry.computeIfAbsent(namespace, k -> new ConcurrentHashMap<>())
                .put(key, container);
        setDirty();
        LOGGER.info("容器已注册: {}:{}", namespace, key);
        return () -> unregister(ResourceLocation.fromNamespaceAndPath(namespace, key));
    }

    @Override
    public void unregister(ResourceLocation id) {
        Map<String, Container> ns = registry.get(id.getNamespace());
        if (ns != null) {
            ns.remove(id.getPath());
            if (ns.isEmpty()) registry.remove(id.getNamespace());
            setDirty();
            LOGGER.info("容器已注销: {}:{}", id.getNamespace(), id.getPath());
        }
    }

    @Override
    public boolean has(String namespace, String key) {
        Map<String, Container> ns = registry.get(namespace);
        return ns != null && ns.containsKey(key);
    }

    @Override
    public void saveAll() {
        setDirty();
    }

    @Override
    public int getDataVersion() {
        return CURRENT_VERSION;
    }

    // ── 包内可见：供 tizModClient 挂钩使用 ──

    static SavedData.Factory<WorldContainerDataStorage> factory() {
        return new SavedData.Factory<>(WorldContainerDataStorage::new, WorldContainerDataStorage::load);
    }

    static String storageName() {
        return NAME;
    }

    // ══════════════════════════════════════════════════════════════════
    //  序列化 / 反序列化
    // ══════════════════════════════════════════════════════════════════

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("DataVersion", CURRENT_VERSION);

        CompoundTag root = new CompoundTag();
        for (Map.Entry<String, Map<String, Container>> nsEntry : registry.entrySet()) {
            String namespace = nsEntry.getKey();
            CompoundTag nsTag = new CompoundTag();

            for (Map.Entry<String, Container> entry : nsEntry.getValue().entrySet()) {
                Container container = entry.getValue();
                nsTag.put(entry.getKey(), serializeContainer(container, registries));
            }

            root.put(namespace, nsTag);
        }
        tag.put("Containers", root);
        return tag;
    }

    private static CompoundTag serializeContainer(Container container, HolderLookup.Provider registries) {
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
        return ct;
    }

    public static WorldContainerDataStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldContainerDataStorage data = new WorldContainerDataStorage();

        int fileVersion = tag.getInt("DataVersion");

        if (tag.contains("Containers", CompoundTag.TAG_COMPOUND)) {
            CompoundTag root = tag.getCompound("Containers");
            for (String namespace : root.getAllKeys()) {
                CompoundTag nsTag = root.getCompound(namespace);
                Map<String, Container> nsMap = new ConcurrentHashMap<>();

                for (String key : nsTag.getAllKeys()) {
                    CompoundTag containerTag = nsTag.getCompound(key);
                    int size = containerTag.getInt("Size");
                    SimpleContainerStub container = new SimpleContainerStub(size);
                    deserializeContainer(containerTag, container, registries);
                    nsMap.put(key, container);
                }

                if (!nsMap.isEmpty()) {
                    data.registry.put(namespace, nsMap);
                }
            }
        }

        if (fileVersion < CURRENT_VERSION) {
            LOGGER.info("容器数据版本迁移: {} → {}", fileVersion, CURRENT_VERSION);
            // 后续版本迁移逻辑在此处追加 if 分支
        }

        return data;
    }

    private static void deserializeContainer(CompoundTag tag, Container container, HolderLookup.Provider registries) {
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag slotTag = items.getCompound(i);
            int slot = slotTag.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < container.getContainerSize()) {
                ItemStack.parse(registries, slotTag).ifPresent(stack -> container.setItem(slot, stack));
            }
        }
    }

    /**
     * 用于反序列化的占位容器实现。
     * 当反序列化时容器实例尚未创建，先用它暂存数据；
     * 后续调用方通过 register() 注册真实容器时，由调用方决定是否覆盖。
     *
     * <p>实际使用中，需要加载的容器应在 onLevelLoad 之前就通过 register 注册好。
     * 此占位容器用于在注册和反序列化顺序不确定时的兜底保护。</p>
     */
    private record SimpleContainerStub(int size, Map<Integer, ItemStack> items) implements Container {

        SimpleContainerStub(int size) {
            this(size, new HashMap<>());
        }

        @Override public int getContainerSize() { return size; }
        @Override public boolean isEmpty() { return items.isEmpty(); }

        @Override
        public ItemStack getItem(int slot) {
            return items.getOrDefault(slot, ItemStack.EMPTY);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (stack.isEmpty()) items.remove(slot);
            else items.put(slot, stack);
        }

        @Override public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;
            ItemStack split = stack.split(amount);
            if (stack.isEmpty()) items.remove(slot); else items.put(slot, stack);
            return split;
        }

        @Override public ItemStack removeItemNoUpdate(int slot) {
            return items.remove(slot);
        }

        @Override public void setChanged() {}
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        @Override public void clearContent() { items.clear(); }
    }
}
