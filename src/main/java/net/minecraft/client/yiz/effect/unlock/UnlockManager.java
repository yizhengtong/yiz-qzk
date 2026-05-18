package net.minecraft.client.yiz.effect.unlock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * 解锁打勾管理器
 * 记录实体的效果解锁状态，提供打勾接口（unlock/lock）。
 */
public final class UnlockManager {

    private static final Map<UUID, Set<ResourceLocation>> unlockedEffects = new HashMap<>();

    private UnlockManager() {}

    /**
     * 为实体解锁效果。
     */
    public static void unlock(LivingEntity entity, ResourceLocation effectId) {
        unlockedEffects.computeIfAbsent(entity.getUUID(), k -> new HashSet<>()).add(effectId);
    }

    /**
     * 按 UUID 解锁。
     */
    public static void unlock(UUID uuid, ResourceLocation effectId) {
        unlockedEffects.computeIfAbsent(uuid, k -> new HashSet<>()).add(effectId);
    }

    /**
     * 检查实体是否已解锁特定效果。
     */
    public static boolean isUnlocked(LivingEntity entity, ResourceLocation effectId) {
        Set<ResourceLocation> effects = unlockedEffects.get(entity.getUUID());
        return effects != null && effects.contains(effectId);
    }

    /**
     * 锁定效果（取消解锁）。
     */
    public static void lock(LivingEntity entity, ResourceLocation effectId) {
        Set<ResourceLocation> effects = unlockedEffects.get(entity.getUUID());
        if (effects != null) {
            effects.remove(effectId);
        }
    }

    /**
     * 获取实体的所有已解锁效果。
     */
    public static Set<ResourceLocation> getUnlockedEffects(UUID uuid) {
        return unlockedEffects.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * 获取实体的所有已解锁效果。
     */
    public static Set<ResourceLocation> getUnlockedEffects(LivingEntity entity) {
        return getUnlockedEffects(entity.getUUID());
    }

    /**
     * 检查实体是否有任何已解锁效果。
     */
    public static boolean hasUnlockedEffects(LivingEntity entity) {
        Set<ResourceLocation> effects = unlockedEffects.get(entity.getUUID());
        return effects != null && !effects.isEmpty();
    }

    // ==================== NBT 持久化 ====================

    private static final String NBT_KEY = "yizmodqzk:unlocked_effects";

    /**
     * 保存到 NBT。
     */
    public static void saveToNBT(CompoundTag nbt) {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<UUID, Set<ResourceLocation>> entry : unlockedEffects.entrySet()) {
            ListTag list = new ListTag();
            for (ResourceLocation id : entry.getValue()) {
                list.add(StringTag.valueOf(id.toString()));
            }
            root.put(entry.getKey().toString(), list);
        }
        nbt.put(NBT_KEY, root);
    }

    /**
     * 从 NBT 加载。
     */
    public static void loadFromNBT(CompoundTag nbt) {
        unlockedEffects.clear();
        if (!nbt.contains(NBT_KEY)) return;

        CompoundTag root = nbt.getCompound(NBT_KEY);
        for (String uuidStr : root.getAllKeys()) {
            UUID uuid = UUID.fromString(uuidStr);
            ListTag list = root.getList(uuidStr, Tag.TAG_STRING);
            Set<ResourceLocation> effects = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                effects.add(ResourceLocation.parse(list.getString(i)));
            }
            unlockedEffects.put(uuid, effects);
        }
    }

    /**
     * 清除指定玩家的解锁数据。
     * 用于客户端接收同步包时替换该玩家状态。
     */
    public static void clearPlayer(UUID uuid) {
        unlockedEffects.remove(uuid);
    }

    /**
     * 清除所有数据（用于测试）。
     */
    public static void clearAll() {
        unlockedEffects.clear();
    }
}
