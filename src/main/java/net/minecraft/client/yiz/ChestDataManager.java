package net.minecraft.client.yiz;

import net.minecraft.world.Container;

import java.util.HashMap;
import java.util.Map;

/**
 * 公开 API — 将任意容器绑定到世界存档自动持久化。
 *
 * <p>使用方式：在 mod 构造期间调用 {@link #register(String, Container)}，
 * 此后该容器的数据会随主世界存档自动保存和恢复。
 *
 * <pre>{@code
 * // 例：注册一个 27 格的容器
 * SimpleContainer chest = new SimpleContainer(27);
 * ChestDataManager.register("mymod:chest", chest);
 * }</pre>
 */
public class ChestDataManager {
    private static final Map<String, Container> REGISTRY = new HashMap<>();

    /**
     * 注册一个容器到世界存档自动持久化系统。
     *
     * @param key       唯一标识键（建议带命名空间，如 "mymod:data"）
     * @param container 需要持久化的容器实例
     */
    public static void register(String key, Container container) {
        REGISTRY.put(key, container);
    }

    /**
     * 注销一个容器，停止自动存档。
     *
     * @param key 注册时使用的键
     */
    public static void unregister(String key) {
        REGISTRY.remove(key);
    }

    // ── 包内可见：供 ChestSavedData 和 tizModClient 使用 ──

    static Map<String, Container> getRegistry() {
        return REGISTRY;
    }
}
