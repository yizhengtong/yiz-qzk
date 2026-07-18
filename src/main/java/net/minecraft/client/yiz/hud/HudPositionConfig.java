package net.minecraft.client.yiz.hud;

import com.google.gson.*;
import net.minecraft.client.Minecraft;

import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD 位置/缩放/显隐持久化（纯客户端 JSON）。
 *
 * <p>路径 {@code config/yizmodqzk/hud_positions.json}，结构：
 * <pre>{@code
 * { "_version": 3, "_guiW": 960, "_guiH": 540,
 *   "huds": { "boost": {"x":150,"y":200,"enabled":true,"scale":1.5} } }
 * }</pre>
 *
 * <h3>GUI 缩放适配（v3）</h3>
 * 存储时记录当前 guiScaledWidth / guiScaledHeight，加载时若当前逻辑分辨率
 * 与存储时不同则按宽高比分别换算坐标。比 guiScale 更准确。
 */
public final class HudPositionConfig {

    private static final Path PATH = Path.of("config", "yizmodqzk", "hud_positions.json");

    private static final Map<String, Entry> HUDS = new ConcurrentHashMap<>();

    public record Entry(int x, int y, boolean enabled, float scale) {}

    private HudPositionConfig() {}

    public static Entry get(String id) { return HUDS.get(id); }

    public static void put(String id, int x, int y, boolean enabled, float scale) {
        HUDS.put(id, new Entry(x, y, enabled, scale));
        save();
    }

    public static void clear() { HUDS.clear(); save(); }

    public static void load() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            if (!Files.exists(p)) return;
            var mc = Minecraft.getInstance();
            int curW = mc.getWindow().getGuiScaledWidth();
            int curH = mc.getWindow().getGuiScaledHeight();

            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            int savedW = root.has("_guiW") ? root.get("_guiW").getAsInt() : curW;
            int savedH = root.has("_guiH") ? root.get("_guiH").getAsInt() : curH;
            double rx = (savedW > 0) ? (double) curW / savedW : 1.0;
            double ry = (savedH > 0) ? (double) curH / savedH : 1.0;

            JsonObject huds = root.getAsJsonObject("huds");
            if (huds == null) return;
            for (String id : huds.keySet()) {
                JsonObject o = huds.getAsJsonObject(id);
                int x = (int) Math.round(o.get("x").getAsInt() * rx);
                int y = (int) Math.round(o.get("y").getAsInt() * ry);
                HUDS.put(id, new Entry(x, y,
                    o.has("enabled") ? o.get("enabled").getAsBoolean() : true,
                    o.has("scale") ? o.get("scale").getAsFloat() : 1f));
            }
        } catch (Exception ignored) { }
    }

    public static void save() {
        try {
            Path p = Path.of("").toAbsolutePath().resolve(PATH);
            Files.createDirectories(p.getParent());
            var mc = Minecraft.getInstance();
            JsonObject root = new JsonObject();
            root.addProperty("_version", 3);
            root.addProperty("_guiW", mc.getWindow().getGuiScaledWidth());
            root.addProperty("_guiH", mc.getWindow().getGuiScaledHeight());
            JsonObject huds = new JsonObject();
            HUDS.forEach((id, e) -> {
                JsonObject o = new JsonObject();
                o.addProperty("x", e.x());
                o.addProperty("y", e.y());
                o.addProperty("enabled", e.enabled());
                o.addProperty("scale", e.scale());
                huds.add(id, o);
            });
            root.add("huds", huds);
            Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ignored) { }
    }
}
