package net.minecraft.client.yiz.core;

import net.minecraft.world.item.Item;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过 {@code sun.misc.Unsafe} 在运行时替换 {@link Item} 单例的 class 指针，
 * 使该物品的所有行为回退到 {@link Item} 基类——即"变成一个空物品"。
 *
 * <p>原理与 {@link PlayerClassSwapper} 相同——只是目标是 Item 单例对象。
 * Item 单例在 mod 加载阶段一次性创建，所有 ItemStack 持有的都是同一个单例引用，
 * 所以改一次 klass 指针，全场所有该物品的实例都生效。</p>
 *
 * <h3>与 VTableReplace 的区别</h3>
 * <ul>
 *   <li>VTableReplace：按方法粒度覆写 vtable 入口指针。只对该子类自己 override
 *       了的方法有效——继承的方法点不到</li>
 *   <li>ItemKlassSwapper：直接换 Item 单例的 klass。所有 virtual 方法都回退到
 *       Item 基类，{@code instanceof MyItem} 也变 false，从而绕过事件层里
 *       常见的 {@code stack.getItem() instanceof MyItem} 判断</li>
 * </ul>
 *
 * <h3>风险</h3>
 * <ul>
 *   <li>子类的字段虽然还在内存里但永远读不到——副作用是它们占的内存浪费</li>
 *   <li>如果调用方持有的是 {@code item == YourItems.SPECIFIC_ITEM} 这种引用相等
 *       的判断，klass 换了引用没换，依然为 true。不能绕过这类判断</li>
 *   <li>不可在 JVM 启用 {@code -XX:+UseCompressedClassPointers} 但 narrow klass
 *       resolution 失败时使用——会写错地址</li>
 * </ul>
 */
public final class ItemKlassSwapper {

    private static final Unsafe U;
    private static final long KLASS_OFFSET;
    private static final boolean KLASS_COMPRESSED;

    /** Item 单例对象 → 它原本的 klass 地址 / narrow */
    private static final ConcurrentHashMap<Item, Long> ORIGINAL_KLASS = new ConcurrentHashMap<>();

    /** Item 基类的 klass 地址（懒求） */
    private static volatile long itemBaseKlass = 0;

    static {
        U = getUnsafe();
        KLASS_OFFSET = determineKlassOffset();
        KLASS_COMPRESSED = isCompressedKlass();
    }

    private ItemKlassSwapper() {}

    // ══════════════════════════════════════════════════════════
    //  公开 API
    // ══════════════════════════════════════════════════════════

    /**
     * 将指定 Item 单例的 klass 指针换成 Item 基类，行为彻底回退到基类。
     *
     * <p>幂等：同一个 Item 多次调用只首次生效，后续调用直接返回 true。</p>
     *
     * @return true 表示成功；false 表示初始化失败或 klass 无法解析
     */
    public static boolean swapToBase(Item item) {
        if (item == null) return false;
        if (item.getClass() == Item.class) return true; // 已经是基类，无需操作
        if (ORIGINAL_KLASS.containsKey(item)) return true; // 已经换过了

        long baseKlass = getItemBaseKlass();
        if (baseKlass == 0) {
            System.err.println("[ItemKlassSwapper] Cannot resolve Item base klass");
            return false;
        }

        long originalKlass = getKlassRaw(item);
        if (originalKlass == 0) {
            System.err.println("[ItemKlassSwapper] Cannot read original klass for " +
                    item.getClass().getName());
            return false;
        }

        ORIGINAL_KLASS.put(item, originalKlass);
        putKlassRaw(item, baseKlass);

        System.out.println("[ItemKlassSwapper] Swapped " + item.getClass().getSimpleName() +
                " klass: 0x" + Long.toHexString(originalKlass) +
                " → Item base (0x" + Long.toHexString(baseKlass) + ")");
        return true;
    }

    /**
     * 把之前被 {@link #swapToBase} 修改过的 Item 还原成原始类。
     */
    public static boolean restore(Item item) {
        if (item == null) return false;
        Long original = ORIGINAL_KLASS.remove(item);
        if (original == null) return false;

        putKlassRaw(item, original);
        System.out.println("[ItemKlassSwapper] Restored " + item.getClass().getSimpleName() +
                " klass to 0x" + Long.toHexString(original));
        return true;
    }

    /**
     * 查询某个 Item 是否处于"已被换 klass"的状态。
     */
    public static boolean isSwapped(Item item) {
        return item != null && ORIGINAL_KLASS.containsKey(item);
    }

    /** 已被换 klass 的 Item 数量。 */
    public static int swappedCount() {
        return ORIGINAL_KLASS.size();
    }

    // ══════════════════════════════════════════════════════════
    //  Item 基类 klass 求值
    // ══════════════════════════════════════════════════════════

    private static long getItemBaseKlass() {
        long cached = itemBaseKlass;
        if (cached != 0) return cached;

        synchronized (ItemKlassSwapper.class) {
            if (itemBaseKlass != 0) return itemBaseKlass;
            try {
                // 用 allocateInstance 拿一个 Item 基类幻象实例 → 读它的 klass
                Object phantom = U.allocateInstance(Item.class);
                long klass = getKlassRaw(phantom);
                itemBaseKlass = klass;
                return klass;
            } catch (Throwable t) {
                System.err.println("[ItemKlassSwapper] Cannot allocate Item phantom: " +
                        t.getMessage());
                return 0;
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  klass 读写
    // ══════════════════════════════════════════════════════════

    /**
     * 读对象头中的 klass —— 压缩模式返回 32-bit narrow（zero-extended 到 long），
     * 非压缩模式返回完整 64-bit 地址。
     */
    private static long getKlassRaw(Object obj) {
        if (KLASS_COMPRESSED) {
            return U.getInt(obj, KLASS_OFFSET) & 0xFFFFFFFFL;
        }
        return U.getLong(obj, KLASS_OFFSET);
    }

    /** 写 klass —— 压缩模式只写低 32-bit。 */
    private static void putKlassRaw(Object obj, long klassValue) {
        if (KLASS_COMPRESSED) {
            U.putInt(obj, KLASS_OFFSET, (int) klassValue);
        } else {
            U.putLong(obj, KLASS_OFFSET, klassValue);
        }
    }

    private static boolean isCompressedKlass() {
        Object probe = new Object();
        long full = U.getLong(probe, KLASS_OFFSET);
        return (full & 0xFFFFFFFF00000000L) == 0 && (int) full != 0;
    }

    // ══════════════════════════════════════════════════════════
    //  Unsafe / 偏移 探测
    // ══════════════════════════════════════════════════════════

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static long determineKlassOffset() {
        Object probe = new Object();
        long markWord = U.getLong(probe, 0L);
        for (long off : new long[]{8L, 12L, 16L, 4L}) {
            try {
                int maybe = U.getInt(probe, off);
                if (maybe != 0 && (maybe & 0xFFFFFFFFL) != (markWord & 0xFFFFFFFFL)) {
                    return off;
                }
            } catch (Exception ignored) {}
        }
        return 8L;
    }
}
