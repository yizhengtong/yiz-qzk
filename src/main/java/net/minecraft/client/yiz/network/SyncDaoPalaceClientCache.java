package net.minecraft.client.yiz.network;

import java.util.Collections;
import java.util.List;

/**
 * 客户端道宫数据缓存。
 * 接收 SyncDaoPalacePayload 后存这里，UI 层从此读取。
 */
public final class SyncDaoPalaceClientCache {

    private static volatile List<SyncDaoPalacePayload.DaoPalaceEntry> cached = Collections.emptyList();

    private SyncDaoPalaceClientCache() {}

    public static void update(List<SyncDaoPalacePayload.DaoPalaceEntry> palaces) {
        cached = List.copyOf(palaces);
    }

    public static List<SyncDaoPalacePayload.DaoPalaceEntry> get() {
        return cached;
    }
}
