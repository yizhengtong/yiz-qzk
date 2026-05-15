package net.minecraft.client.yiz.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;

/**
 * 容器数据持久化接口 — 将任意 {@link Container} 绑定到世界存档自动存读。
 *
 * <p>其他模组可通过 {@link #register(String, String, Container)} 注册自己的容器，
 * 以 namespace 隔离避免 key 冲突。
 *
 * <pre>{@code
 * // 在模组构造期间注册，返回 AutoCloseable，模组卸载时 close()
 * ContainerDataStorage storage = ContainerDataStorage.getInstance();
 * AutoCloseable handle = storage.register("mod_a", "tombstone_data", container);
 * }</pre>
 */
public interface ContainerDataStorage {

    /**
     * 获取全局唯一的持久化存储实例。
     * 由实现方在模组加载时设置，在此接口中通过静态方法引用。
     */
    static ContainerDataStorage getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * 注册一个容器到世界存档自动持久化系统。
     *
     * @param namespace 命名空间（建议使用 modId，如 "mod_a"），不同模组互不干扰
     * @param key       容器标识键（如 "tombstone_data"），在同一 namespace 内唯一
     * @param container 需要持久化的容器实例
     * @return {@link AutoCloseable} 句柄，调用 {@link AutoCloseable#close()} 可注销
     */
    AutoCloseable register(String namespace, String key, Container container);

    /**
     * 注销一个已注册的容器，停止自动存档。
     *
     * @param id 容器的完整 {@link ResourceLocation}（namespace:key）
     */
    void unregister(ResourceLocation id);

    /**
     * 判断某个容器是否已注册。
     */
    boolean has(String namespace, String key);

    /**
     * 获取已注册的容器实例。
     */
    Container get(String namespace, String key);

    /**
     * 立即强制保存所有已注册容器的当前状态到世界存档。
     */
    void saveAll();

    /**
     * 当前数据格式版本号。
     * 版本不匹配时，实现方应自动执行数据迁移而非崩溃。
     */
    int getDataVersion();

    // ── 内部持有引用，避免接口静态方法无法直接访问实现类 ──
    final class Holder {
        private static ContainerDataStorage INSTANCE;

        private Holder() {}

        /**
         * 由实现方（ChestDataManager）在模组初始化时调用一次。
         */
        public static void setInstance(ContainerDataStorage instance) {
            if (INSTANCE != null && instance != null) {
                // 允许覆盖但打日志，防止多个实现互相覆盖
            }
            INSTANCE = instance;
        }
    }
}
