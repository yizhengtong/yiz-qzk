package net.minecraft.client.yiz;

import net.minecraft.client.yiz.api.ContainerDataStorage;
import net.minecraft.client.yiz.impl.WorldContainerDataStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 容器数据持久化管理器 — 公开 API 门面。
 *
 * <p>本类同时承担两个角色：</p>
 * <ul>
 *   <li>{@link ContainerDataStorage} 的实现注册方（通过 {@link #getInstance()}）</li>
 *   <li>向下兼容的静态便捷方法入口</li>
 * </ul>
 *
 * <p>外部模组推荐用法：</p>
 * <pre>{@code
 * AutoCloseable handle = ChestDataManager.register("mod_a", "backpack", myContainer);
 * // 不再需要时
 * handle.close();
 * }</pre>
 */
public class ChestDataManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("yizmodqzk:ChestDataManager");
    private static final ChestDataManager INSTANCE = new ChestDataManager();

    /** 当前活跃的世界存档存储实例，在 LevelEvent.Load 时设置。 */
    private volatile WorldContainerDataStorage activeStorage;

    /** 在 activeStorage 就绪前的暂存注册，level load 后自动迁移。 */
    private final Map<ResourceLocation, Container> pendingRegistrations = new ConcurrentHashMap<>();

    static {
        ContainerDataStorage.Holder.setInstance(new ContainerDataStorage() {
            @Override public AutoCloseable register(String ns, String k, Container c) { return INSTANCE.doRegister(ns, k, c); }
            @Override public void unregister(ResourceLocation id) { INSTANCE.doUnregister(id); }
            @Override public boolean has(String ns, String k) { return INSTANCE.doHas(ns, k); }
            @Override public Container get(String ns, String k) { return INSTANCE.doGet(ns, k); }
            @Override public void saveAll() { if (INSTANCE.activeStorage != null) INSTANCE.activeStorage.setDirty(); }
            @Override public int getDataVersion() { return WorldContainerDataStorage.currentVersion(); }
        });
    }

    private ChestDataManager() {}

    // ══════════════════════════════════════════════════════════════════
    //  静态便捷方法（外部模组入口）
    // ══════════════════════════════════════════════════════════════════

    public static AutoCloseable register(String key, Container container) {
        return INSTANCE.doRegister(detectCallerNamespace(), key, container);
    }

    public static AutoCloseable register(String namespace, String key, Container container) {
        return INSTANCE.doRegister(namespace, key, container);
    }

    public static Container getContainer(String namespace, String key) {
        return INSTANCE.doGet(namespace, key);
    }

    public static void setActiveWorldStorage(WorldContainerDataStorage storage) {
        INSTANCE.setActiveStorage(storage);
    }

    public static void clearActiveWorldStorage() {
        INSTANCE.clearActiveStorage();
    }

    // ══════════════════════════════════════════════════════════════════
    //  包内可见：供 tizModClient 挂钩使用
    // ══════════════════════════════════════════════════════════════════

    /**
     * 设置或替换活跃的世界存档存储实例。
     * 由 tizModClient.onLevelLoad() 在维度加载时调用。
     * 同时将暂存的 pending 注册迁移到新存储。
     */
    void setActiveStorage(WorldContainerDataStorage storage) {
        this.activeStorage = storage;
        // 迁移暂存的 pending 注册
        if (!pendingRegistrations.isEmpty()) {
            LOGGER.info("迁移 {} 个暂存注册到世界存档存储", pendingRegistrations.size());
            for (Map.Entry<ResourceLocation, Container> entry : pendingRegistrations.entrySet()) {
                ResourceLocation id = entry.getKey();
                storage.register(id.getNamespace(), id.getPath(), entry.getValue());
            }
            pendingRegistrations.clear();
        }
    }

    /**
     * 清除活跃存储引用（世界卸载时调用）。
     */
    void clearActiveStorage() {
        this.activeStorage = null;
    }

    // ══════════════════════════════════════════════════════════════════
  //  内部实现
  // ══════════════════════════════════════════════════════════════════

    private AutoCloseable doRegister(String namespace, String key, Container container) {
        if (namespace == null || namespace.isEmpty()) {
            namespace = detectCallerNamespace();
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, key);

        WorldContainerDataStorage storage = activeStorage;
        if (storage != null) {
            return storage.register(namespace, key, container);
        } else {
            // 存档尚未加载，先暂存
            pendingRegistrations.put(id, container);
            LOGGER.info("容器暂存（存档未就绪）: {}", id);
            return () -> {
                pendingRegistrations.remove(id);
                WorldContainerDataStorage s = activeStorage;
                if (s != null) s.unregister(id);
            };
        }
    }

    private void doUnregister(ResourceLocation id) {
        pendingRegistrations.remove(id);
        WorldContainerDataStorage storage = activeStorage;
        if (storage != null) storage.unregister(id);
    }

    private boolean doHas(String namespace, String key) {
        if (activeStorage != null && activeStorage.has(namespace, key)) return true;
        return pendingRegistrations.containsKey(ResourceLocation.fromNamespaceAndPath(namespace, key));
    }

    private Container doGet(String namespace, String key) {
        if (activeStorage != null) {
            Container c = activeStorage.get(namespace, key);
            if (c != null) return c;
        }
        return pendingRegistrations.get(ResourceLocation.fromNamespaceAndPath(namespace, key));
    }

    private Map<String, Map<String, Container>> getActiveRegistry() {
        // 合并 active + pending 视图
        Map<String, Map<String, Container>> result = new ConcurrentHashMap<>();
        WorldContainerDataStorage storage = activeStorage;
        // 通过 WorldContainerDataStorage 包内可见方法... 简化返回 pending
        for (Map.Entry<ResourceLocation, Container> entry : pendingRegistrations.entrySet()) {
            result.computeIfAbsent(entry.getKey().getNamespace(), k -> new ConcurrentHashMap<>())
                  .put(entry.getKey().getPath(), entry.getValue());
        }
        return result;
    }

    /**
     * 自动检测调用方的 modId（namespace）。
     *
     * <p>通过 StackWalker 回溯调用栈，找到第一个非本类的调用者，
     * 然后检查其是否标注了 {@code @Mod} 注解来确定 modId。</p>
     */
    private static String detectCallerNamespace() {
        try {
            Class<?> callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames
                            .skip(2)  // 跳过 getCallerClass 和 doRegister
                            .findFirst()
                            .map(StackWalker.StackFrame::getDeclaringClass)
                            .orElse(null));
            if (callerClass == null) return "unknown";

            // 检查 @Mod 注解
            for (Annotation ann : callerClass.getAnnotations()) {
                if (ann.annotationType().getName().equals("net.neoforged.fml.common.Mod")) {
                    try {
                        return (String) ann.annotationType().getMethod("value").invoke(ann);
                    } catch (Exception ignored) {}
                }
            }

            // 回退：使用包名（如 "mod_a" 取自 "com.example.mod_a" 的末段）
            String pkg = callerClass.getPackageName();
            int lastDot = pkg.lastIndexOf('.');
            return lastDot >= 0 ? pkg.substring(lastDot + 1) : pkg;

        } catch (Exception e) {
            LOGGER.warn("无法检测调用方 modId，回退为 unknown", e);
            return "unknown";
        }
    }
}
