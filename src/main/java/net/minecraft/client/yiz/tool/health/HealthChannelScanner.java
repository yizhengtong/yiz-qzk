package net.minecraft.client.yiz.tool.health;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体健康通道扫描器
 * <p>
 * 扫描实体类层级上的所有静态 {@link EntityDataAccessor}&lt;Float&gt; 字段，
 * 用于在伤害时覆盖所有可能的血量 DataParameter。
 * </p>
 */
public final class HealthChannelScanner {

    private static final Map<Class<?>, List<EntityDataAccessor<Float>>> CHANNEL_CACHE = new ConcurrentHashMap<>();

    /** LivingEntity.DATA_HEALTH_ID 的 accessor，运行时反射获取 */
    private static final EntityDataAccessor<Float> VANILLA_HEALTH_ACCESSOR = initVanillaHealthAccessor();

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Float> initVanillaHealthAccessor() {
        try {
            Field f = LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
            f.setAccessible(true);
            return (EntityDataAccessor<Float>) f.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private HealthChannelScanner() {}

    /**
     * 获取实体所有 Float 类型的 DataParameter（排除 vanilla DATA_HEALTH_ID）。
     * <p>
     * 结果按类缓存，避免重复反射扫描。
     */
    public static List<EntityDataAccessor<Float>> getFloatChannels(LivingEntity entity) {
        return CHANNEL_CACHE.computeIfAbsent(entity.getClass(), HealthChannelScanner::scanClassHierarchy);
    }

    /**
     * 获取实体所有 Float 类型的 DataParameter（包含 vanilla DATA_HEALTH_ID）。
     */
    public static List<EntityDataAccessor<Float>> getAllFloatChannels(LivingEntity entity) {
        List<EntityDataAccessor<Float>> all = new ArrayList<>();
        // DATA_HEALTH_ID 放第一个
        if (VANILLA_HEALTH_ACCESSOR != null) {
            all.add(VANILLA_HEALTH_ACCESSOR);
        }
        // 再加上其他通道
        all.addAll(getFloatChannels(entity));
        return all;
    }

    /**
     * 清除缓存（类重载时调用）
     */
    public static void clearCache() {
        CHANNEL_CACHE.clear();
    }

    // ==================== 内部扫描 ====================

    /**
     * 从 clazz 一直扫描到 LivingEntity，收集所有静态 EntityDataAccessor&lt;Float&gt; 字段。
     * 排除 VANILLA_HEALTH_ACCESSOR（已在 delta 系统中处理）。
     */
    private static List<EntityDataAccessor<Float>> scanClassHierarchy(Class<?> clazz) {
        List<EntityDataAccessor<Float>> result = new ArrayList<>();
        scanUpToLivingEntity(clazz, result);
        return List.copyOf(result); // 不可变缓存
    }

    private static void scanUpToLivingEntity(Class<?> clazz, List<EntityDataAccessor<Float>> result) {
        if (clazz == null || clazz == Object.class || clazz == LivingEntity.class) return;

        // 先扫描父类（上层优先）
        scanUpToLivingEntity(clazz.getSuperclass(), result);

        // 同时扫描该类实现的所有接口（模组可能在接口中定义 DataParameter）
        scanInterfaces(clazz, result);

        // 再扫描该类自身的静态字段
        scanDeclaredFloatAccessors(clazz, result);
    }

    /**
     * 递归扫描接口层级，查找定义在接口中的 EntityDataAccessor 静态字段。
     */
    private static void scanInterfaces(Class<?> clazz, List<EntityDataAccessor<Float>> result) {
        for (Class<?> iface : clazz.getInterfaces()) {
            // 避免重复扫描（多个父类可能实现同一个接口）
            if (iface == LivingEntity.class || iface == Object.class) continue;
            scanDeclaredFloatAccessors(iface, result);
            // 接口也可以继承其他接口
            scanInterfaces(iface, result);
        }
    }

    /**
     * 扫描单个类/接口上的静态 EntityDataAccessor&lt;Float&gt; 字段
     */
    private static void scanDeclaredFloatAccessors(Class<?> clazz, List<EntityDataAccessor<Float>> result) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!EntityDataAccessor.class.isAssignableFrom(field.getType())) continue;

            try {
                field.setAccessible(true);
                EntityDataAccessor<?> accessor = (EntityDataAccessor<?>) field.get(null);
                if (accessor == null) continue;
                if (accessor.serializer() != EntityDataSerializers.FLOAT) continue;

                @SuppressWarnings("unchecked")
                EntityDataAccessor<Float> floatAccessor = (EntityDataAccessor<Float>) accessor;

                // 排除 vanilla DATA_HEALTH_ID（由 delta 系统处理）
                if (VANILLA_HEALTH_ACCESSOR != null && accessor.id() == VANILLA_HEALTH_ACCESSOR.id()) continue;

                result.add(floatAccessor);
            } catch (Exception ignored) {
                // 无法访问的字段跳过
            }
        }
    }
}
