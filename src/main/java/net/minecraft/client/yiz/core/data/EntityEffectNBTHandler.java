package net.minecraft.client.yiz.core.data;

import net.minecraft.client.yiz.effect.unlock.UnlockManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * 实体 NBT 效果处理器
 * 保存/加载实体已解锁的天赋数据。
 */
public final class EntityEffectNBTHandler {

    private static final String TALENTS_KEY = "yizmodqzk:talents";
    private static final String UNLOCKED_KEY = "unlocked";

    private EntityEffectNBTHandler() {}

    /**
     * 保存实体已解锁的天赋到 NBT。
     */
    public static void saveEntityTalents(LivingEntity entity, CompoundTag nbt) {
        CompoundTag talentsTag = new CompoundTag();
        ListTag unlockedList = new ListTag();

        Set<ResourceLocation> unlockedTalents = UnlockManager.getUnlockedEffects(entity);
        for (ResourceLocation id : unlockedTalents) {
            unlockedList.add(StringTag.valueOf(id.toString()));
        }

        talentsTag.put(UNLOCKED_KEY, unlockedList);
        nbt.put(TALENTS_KEY, talentsTag);
    }

    /**
     * 从 NBT 加载实体天赋数据。
     */
    public static void loadEntityTalents(LivingEntity entity, CompoundTag nbt) {
        if (!nbt.contains(TALENTS_KEY)) return;

        CompoundTag talentsTag = nbt.getCompound(TALENTS_KEY);
        if (!talentsTag.contains(UNLOCKED_KEY)) return;

        ListTag unlockedList = talentsTag.getList(UNLOCKED_KEY, Tag.TAG_STRING);
        for (int i = 0; i < unlockedList.size(); i++) {
            try {
                ResourceLocation id = ResourceLocation.parse(unlockedList.getString(i));
                UnlockManager.unlock(entity, id);
            } catch (Exception e) {
                // 跳过无效 ID
            }
        }
    }

    /**
     * 检查实体 NBT 中是否有天赋数据。
     */
    public static boolean hasTalentData(LivingEntity entity, CompoundTag nbt) {
        return nbt.contains(TALENTS_KEY);
    }

    /**
     * 清除实体 NBT 中的天赋数据。
     */
    public static void clearTalentData(CompoundTag nbt) {
        nbt.remove(TALENTS_KEY);
    }
}
