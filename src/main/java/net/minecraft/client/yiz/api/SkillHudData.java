package net.minecraft.client.yiz.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 技能 HUD 数据读取器（纯客户端）。
 *
 * <p>从 PlayerDataAPI 读取 {@code yizmodqzk:load_slots} 键的 JSON 字符串，
 * 解析出大装载槽 + 3 个技能装载槽的物品。</p>
 *
 * <p>数据由 SkillConfigMenu.removed() 在服务端写入并自动同步到客户端。
 * 数据格式：
 * <pre>{@code
 * {"big":"{id:\"minecraft:diamond\",count:1}","s0":"{}","s1":"{}","s2":"{}"}
 * }</pre>
 * 空槽对应空字符串。</p>
 *
 * <h3>冷却扩展预留</h3>
 * 后续可在 JSON 中附加冷却字段，HUD 据此决定是否灰显：
 * <pre>{@code
 * {"big":"...","s0":"...","cd_big":200,"cd_s0":0,...}
 * }</pre>
 */
public final class SkillHudData {

    private SkillHudData() {}

    private static final String KEY = "yizmodqzk:load_slots";
    private static final String CHARGES_KEY = "yizmodqzk:skill_charges";

    /** 装载槽 + 充能数据。 */
    public record Slots(ItemStack big, ItemStack s0, ItemStack s1, ItemStack s2,
                        int chBig, int chS0, int chS1, int chS2,
                        long rcBig, long rcS0, long rcS1, long rcS2) {
        public boolean isEmpty() { return big.isEmpty() && s0.isEmpty() && s1.isEmpty() && s2.isEmpty(); }

        /** 指定槽位当前充能数。 */
        public int charges(int slot) {
            return switch (slot) {
                case 0 -> chBig; case 1 -> chS0; case 2 -> chS1; case 3 -> chS2;
                default -> 0;
            };
        }

        /** 指定槽位回充完成时间戳（gameTick，0=不在回充）。 */
        public long rechargeEnd(int slot) {
            return switch (slot) {
                case 0 -> rcBig; case 1 -> rcS0; case 2 -> rcS1; case 3 -> rcS2;
                default -> 0L;
            };
        }

        /** 是否在回充中（充能未满且时间未到）。 */
        public boolean isRecharging(int slot, long gameTime) {
            return rechargeEnd(slot) > gameTime;
        }

        /** 回充剩余 tick（0=已就绪）。 */
        public int rechargeRemaining(int slot, long gameTime) {
            long r = rechargeEnd(slot) - gameTime;
            return r <= 0 ? 0 : (r > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) r);
        }
    }

    /** 读取当前玩家装载槽物品 + 充能。 */
    public static Slots read() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return EMPTY;
        try {
            String raw = PlayerDataAPI.get(player, KEY);
            JsonObject json = (raw != null && !raw.isEmpty())
                ? JsonParser.parseString(raw).getAsJsonObject() : new JsonObject();

            String chRaw;
            try { chRaw = PlayerDataAPI.get(player, CHARGES_KEY); } catch (Exception e) { chRaw = null; }
            JsonObject chJson = (chRaw != null && !chRaw.isEmpty())
                ? JsonParser.parseString(chRaw).getAsJsonObject() : new JsonObject();

            return new Slots(
                parseItem(json, "big"), parseItem(json, "s0"),
                parseItem(json, "s1"), parseItem(json, "s2"),
                chargeOf(chJson, "big"), chargeOf(chJson, "s0"),
                chargeOf(chJson, "s1"), chargeOf(chJson, "s2"),
                rechargeOf(chJson, "big"), rechargeOf(chJson, "s0"),
                rechargeOf(chJson, "s1"), rechargeOf(chJson, "s2")
            );
        } catch (Exception e) {
            return EMPTY;
        }
    }

    private static int chargeOf(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return 0;
        JsonObject e = json.getAsJsonObject(key);
        return e.has("charges") ? e.get("charges").getAsInt() : 0;
    }

    private static long rechargeOf(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return 0L;
        JsonObject e = json.getAsJsonObject(key);
        return e.has("rechargeEnd") ? e.get("rechargeEnd").getAsLong() : 0L;
    }

    private static ItemStack parseItem(JsonObject json, String key) {
        String snbt = json.has(key) ? json.get(key).getAsString() : "";
        if (snbt.isEmpty()) return ItemStack.EMPTY;
        try {
            return ItemStack.parse(
                Minecraft.getInstance().player.registryAccess(),
                TagParser.parseTag(snbt)
            ).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static final Slots EMPTY = new Slots(
        ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
        0, 0, 0, 0, 0L, 0L, 0L, 0L);
}
