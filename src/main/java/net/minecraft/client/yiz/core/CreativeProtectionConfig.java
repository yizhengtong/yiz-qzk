package net.minecraft.client.yiz.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 创造模式保护配置。
 *
 * <p>JSON 格式（config/yizmodqzk-creative-protection.json）：</p>
 * <pre>{
 *   "creativeProtection": true
 * }</pre>
 */
public final class CreativeProtectionConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("CreativeProtectionConfig");

    private static final String FILE_NAME = "yizmodqzk-creative-protection.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static boolean creativeProtection = false;
    private static volatile boolean loaded = false;

    private CreativeProtectionConfig() {}

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            Path configDir = FMLPaths.CONFIGDIR.get();
            Path file = configDir.resolve(FILE_NAME);
            if (!Files.exists(file)) {
                writeTemplate(file);
                LOGGER.info("[CreativeProtection] No config found; wrote default at {}", file);
                return;
            }

            try (FileReader reader = new FileReader(file.toFile())) {
                Type t = new TypeToken<Map<String, Object>>(){}.getType();
                Map<String, Object> data = GSON.fromJson(reader, t);
                if (data == null) return;

                if (data.get("creativeProtection") instanceof Boolean b) {
                    creativeProtection = b;
                }
                LOGGER.info("[CreativeProtection] Loaded: creativeProtection={}", creativeProtection);
            }
        } catch (Throwable t) {
            LOGGER.error("[CreativeProtection] Load failed: {}", t.getMessage(), t);
        }
    }

    private static void writeTemplate(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("_comment", "创造模式自动开启保护态（/yiz th）。设为 false 关闭此功能。");
            template.put("creativeProtection", true);
            Files.writeString(file, GSON.toJson(template));
        } catch (Throwable ignored) {}
    }

    public static boolean isCreativeProtectionEnabled() {
        ensureLoaded();
        return creativeProtection;
    }
}
