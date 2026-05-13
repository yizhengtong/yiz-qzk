package net.minecraft.client.yiz.tool.health;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 直接数据访问保底方案
 * <p>
 * 通过反射直接访问 {@link SynchedEntityData} 内部的 {@code DataItem[]} 数组，
 * 绕过所有实体方法覆盖、事件系统、以及 {@code getHealth()} 重写。
 * </p>
 * <p>
 * 不受以下因素影响：
 * <ul>
 *   <li>实体重写 {@code getHealth()} 返回自定义值（如 EntityTitan）</li>
 *   <li>自定义伤害管道中的类型黑名单、伤害阈值</li>
 *   <li>无敌帧计时器</li>
 *   <li>事件系统未触发</li>
 * </ul>
 * <strong>工作原理</strong>：Minecraft 所有实体的同步数据（包括血量）都存储在
 * {@link SynchedEntityData#defineId} 注册的 {@link EntityDataAccessor} 中，
 * 最终存放在 {@code SynchedEntityData.DataItem[]} 数组。我们直接修改这个数组中的
 * Float 类型数据项，无论实体如何重写方法，下一帧读取时都会看到修改后的值。
 * </p>
 * <p>
 * 此方案作为最后保底，与主 delta 系统和通道扫描器配合使用。
 * </p>
 */
public final class DirectHealthFallback {

    private static final Field ITEMS_BY_ID;
    private static final Field IS_DIRTY;
    private static final Method ON_SYNCED_DATA_UPDATED;
    private static final boolean AVAILABLE;

    static {
        Field itemsField = null;
        Field dirtyField = null;
        Method syncMethod = null;

        try {
            // 查找 itemsById 字段：先按名称，再按类型回退
            try {
                itemsField = SynchedEntityData.class.getDeclaredField("itemsById");
            } catch (NoSuchFieldException e) {
                for (Field f : SynchedEntityData.class.getDeclaredFields()) {
                    if (f.getType().isArray()) {
                        itemsField = f;
                        break;
                    }
                }
            }

            if (itemsField != null) {
                itemsField.setAccessible(true);

                // 查找 isDirty 字段
                try {
                    dirtyField = SynchedEntityData.class.getDeclaredField("isDirty");
                } catch (NoSuchFieldException e) {
                    for (Field f : SynchedEntityData.class.getDeclaredFields()) {
                        if (f.getType() == boolean.class) {
                            dirtyField = f;
                            break;
                        }
                    }
                }
                if (dirtyField != null) dirtyField.setAccessible(true);

                // onSyncedDataUpdated 方法
                try {
                    syncMethod = LivingEntity.class.getMethod("onSyncedDataUpdated", EntityDataAccessor.class);
                } catch (NoSuchMethodException e) {
                    for (Method m : LivingEntity.class.getMethods()) {
                        if (m.getParameterCount() == 1
                            && EntityDataAccessor.class.isAssignableFrom(m.getParameterTypes()[0])
                            && m.getReturnType() == void.class) {
                            syncMethod = m;
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        ITEMS_BY_ID = itemsField;
        IS_DIRTY = dirtyField;
        ON_SYNCED_DATA_UPDATED = syncMethod;
        AVAILABLE = itemsField != null;
    }

    private DirectHealthFallback() {}

    /**
     * 对所有 Float DataItem 直接施加伤害。
     * <p>
     * 绕过所有方法覆盖和事件系统，是最终保底方案。
     * 仅在 amount < 0 时生效。
     * </p>
     *
     * @param entity 目标实体
     * @param amount 伤害值（负数），如 -50
     */
    public static void damageAll(LivingEntity entity, float amount) {
        if (!AVAILABLE) return;
        if (amount >= 0) return;
        applyToAllFloatItems(entity, amount, true);
    }

    /**
     * 对所有 Float DataItem 直接施加治疗。
     * <p>
     * 与 {@link #damageAll} 对称，仅处理 amount > 0。
     * </p>
     *
     * @param entity 目标实体
     * @param amount 治疗值（正数），如 50
     */
    public static void healAll(LivingEntity entity, float amount) {
        if (!AVAILABLE) return;
        if (amount <= 0) return;
        applyToAllFloatItems(entity, amount, false);
    }

    // ==================== 公用遍历（供 HealBanHandler.enforceTick 使用） ====================

    /**
     * 遍历实体的所有 Float DataItem。
     * <p>
     * 使用与 {@link #applyToAllFloatItems} 相同的方式访问 {@code itemsById} 数组，
     * 确保不会遗漏任何 Float 数据通道（包括泰坦类模组通过反射等方式注册的）。
     * </p>
     *
     * @param entity   目标实体
     * @param callback 对每个 Float DataItem 的回调
     */
    public static void forEachFloatItem(LivingEntity entity, FloatItemCallback callback) {
        if (!AVAILABLE) return;
        try {
            SynchedEntityData data = entity.getEntityData();
            SynchedEntityData.DataItem<?>[] items = (SynchedEntityData.DataItem<?>[]) ITEMS_BY_ID.get(data);
            if (items == null) return;
            for (SynchedEntityData.DataItem<?> item : items) {
                if (item == null) continue;
                EntityDataAccessor<?> accessor = item.getAccessor();
                if (accessor == null || accessor.serializer() != EntityDataSerializers.FLOAT) continue;
                @SuppressWarnings("unchecked")
                SynchedEntityData.DataItem<Float> floatItem = (SynchedEntityData.DataItem<Float>) item;
                float value = (Float) item.getValue();
                @SuppressWarnings("unchecked")
                EntityDataAccessor<Float> floatAccessor = (EntityDataAccessor<Float>) accessor;
                callback.accept(floatAccessor, value, floatItem);
            }
        } catch (Exception ignored) {}
    }

    @FunctionalInterface
    public interface FloatItemCallback {
        void accept(EntityDataAccessor<Float> accessor, float value, SynchedEntityData.DataItem<Float> item);
    }

    // ==================== 内部实现 ====================

    /**
     * 对所有 Float DataItem 应用 delta 修改。
     *
     * @param entity   目标实体
     * @param amount   变化值（正=治疗，负=伤害）
     * @param clamp    true 表示对结果做 max(0, ...) 裁剪（伤害用）
     */
    private static void applyToAllFloatItems(LivingEntity entity, float amount, boolean clamp) {
        try {
            SynchedEntityData data = entity.getEntityData();
            SynchedEntityData.DataItem<?>[] items = (SynchedEntityData.DataItem<?>[]) ITEMS_BY_ID.get(data);

            boolean changed = false;

            for (SynchedEntityData.DataItem<?> item : items) {
                EntityDataAccessor<?> accessor = item.getAccessor();
                if (accessor == null) continue;
                if (accessor.serializer() != EntityDataSerializers.FLOAT) continue;

                float current = (Float) item.getValue();
                float newValue = clamp ? Math.max(0, current + amount) : current + amount;

                @SuppressWarnings("unchecked")
                SynchedEntityData.DataItem<Float> floatItem = (SynchedEntityData.DataItem<Float>) item;
                floatItem.setValue(newValue);
                item.setDirty(true);
                changed = true;
            }

            if (changed) {
                if (ON_SYNCED_DATA_UPDATED != null) {
                    for (SynchedEntityData.DataItem<?> item : items) {
                        EntityDataAccessor<?> accessor = item.getAccessor();
                        if (accessor.serializer() == EntityDataSerializers.FLOAT) {
                            try {
                                ON_SYNCED_DATA_UPDATED.invoke(entity, accessor);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (IS_DIRTY != null) {
                    IS_DIRTY.set(data, true);
                }
            }
        } catch (Exception ignored) {
            // 保底方案不出声，不影响主流程
        }
    }
}
