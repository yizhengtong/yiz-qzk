package net.minecraft.client.yiz.core.data;

import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 物品 NBT 效果处理器
 * 在 ItemStack 的 DataComponents 中读写效果数据。
 * 使用 Minecraft 1.21.1 组件系统 API。
 */
public final class EffectNBTHandler {

    private static final String EFFECTS_KEY = "yizmodqzk:effects";
    private static final String KEY_ID = "id";
    private static final String KEY_LEVEL = "level";

    private static final String INTERNAL_KEY = "yizmodqzk.data";

    private EffectNBTHandler() {}

    /**
     * 为物品添加效果。
     */
    public static void addEffectToItem(ItemStack stack, AbstractEffect effect) {
        // 读取或创建内部数据
        CompoundTag data = getOrCreateInternalData(stack);
        ListTag effectsList = data.getList(EFFECTS_KEY, Tag.TAG_COMPOUND);

        // 检查是否已存在（去重）
        String effectId = effect.getId().toString();
        for (int i = 0; i < effectsList.size(); i++) {
            if (effectsList.getCompound(i).getString(KEY_ID).equals(effectId)) {
                return; // 已存在
            }
        }

        // 添加新效果
        CompoundTag effectTag = new CompoundTag();
        effectTag.putString(KEY_ID, effectId);
        effectTag.putInt(KEY_LEVEL, effect.getLevel());
        effectsList.add(effectTag);

        data.put(EFFECTS_KEY, effectsList);
        saveInternalData(stack, data);
    }

    /**
     * 从物品移除效果。
     */
    public static void removeEffectFromItem(ItemStack stack, ResourceLocation effectId) {
        CompoundTag data = getInternalData(stack);
        if (data == null) return;

        ListTag effectsList = data.getList(EFFECTS_KEY, Tag.TAG_COMPOUND);
        ListTag newList = new ListTag();

        String targetId = effectId.toString();
        for (int i = 0; i < effectsList.size(); i++) {
            if (!effectsList.getCompound(i).getString(KEY_ID).equals(targetId)) {
                newList.add(effectsList.getCompound(i));
            }
        }

        if (newList.isEmpty()) {
            data.remove(EFFECTS_KEY);
        } else {
            data.put(EFFECTS_KEY, newList);
        }
        saveInternalData(stack, data);
    }

    /**
     * 获取物品的所有效果实例。
     */
    public static List<AbstractEffect> getItemEffects(ItemStack stack) {
        List<AbstractEffect> effects = new ArrayList<>();

        CompoundTag data = getInternalData(stack);
        if (data == null || !data.contains(EFFECTS_KEY)) return effects;

        ListTag effectsList = data.getList(EFFECTS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < effectsList.size(); i++) {
            CompoundTag tag = effectsList.getCompound(i);
            String idStr = tag.getString(KEY_ID);
            try {
                ResourceLocation id = ResourceLocation.parse(idStr);
                Optional<AbstractEffect> effect = ModRegistries.getEffect(id);
                effect.ifPresent(effects::add);
            } catch (Exception e) {
                // 跳过无法解析的效果
            }
        }

        return effects;
    }

    /**
     * 检查物品是否有任何效果。
     */
    public static boolean hasEffects(ItemStack stack) {
        CompoundTag data = getInternalData(stack);
        if (data == null) return false;
        return data.contains(EFFECTS_KEY) && !data.getList(EFFECTS_KEY, Tag.TAG_COMPOUND).isEmpty();
    }

    // ==================== 内部数据读写 ====================

    private static CompoundTag getInternalData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        return customData.copyTag();
    }

    private static CompoundTag getOrCreateInternalData(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            return customData.copyTag();
        }
        return new CompoundTag();
    }

    private static void saveInternalData(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
