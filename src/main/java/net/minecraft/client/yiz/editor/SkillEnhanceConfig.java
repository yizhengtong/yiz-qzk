package net.minecraft.client.yiz.editor;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * 技能→强化物品槽位映射配置。
 *
 * <p>每个主动技能最多 6 槽，被动最多 3 槽。
 * <b>匹配方式：</b>先用注册名精确匹配，失败则用物品 displayName 匹配。
 * 推荐在 static 块中同时写两个 key 以兼容不同场景。</p>
 */
public final class SkillEnhanceConfig {

    private SkillEnhanceConfig() {}

    private record SkillConfig(int maxSlots, List<String> itemKeys) {}

    /** skill 显示名或注册名 → 槽位配置 */
    private static final Map<String, SkillConfig> SKILLS = new LinkedHashMap<>();
    /** enhance 物品 key → 对应 Item */
    private static final Map<String, DeferredHolderRef> ENHANCE_ITEMS = new LinkedHashMap<>();

    @FunctionalInterface
    public interface DeferredHolderRef {
        Item get();
    }

    // ═══════════════════════════════════════════════════════════
    //  注册 API
    // ═══════════════════════════════════════════════════════════

    public static void registerItem(String key, DeferredHolderRef ref) {
        ENHANCE_ITEMS.put(key, ref);
    }

    /** 注册技能强化槽分配。key 可写中文显示名或注册名，支持多 key 指向同一配置。 */
    public static void registerSkill(String skillKey, int maxSlots, String... itemKeys) {
        SKILLS.put(skillKey, new SkillConfig(maxSlots, List.of(itemKeys)));
    }

    // ═══════════════════════════════════════════════════════════
    //  查询 API
    // ═══════════════════════════════════════════════════════════

    public static List<EnhanceEntry> getEnhancementsFor(ItemStack skillItem) {
        if (skillItem.isEmpty()) return List.of();
        SkillConfig cfg = findConfig(skillItem);
        if (cfg == null) return List.of();

        List<EnhanceEntry> entries = new ArrayList<>();
        for (int i = 0; i < cfg.itemKeys().size() && i < cfg.maxSlots(); i++) {
            String itemKey = cfg.itemKeys().get(i);
            DeferredHolderRef ref = ENHANCE_ITEMS.get(itemKey);
            if (ref == null) continue;
            Item item = ref.get();
            String displayName = item.getDescription().getString();
            String desc = EnhanceTagRegistry.description(itemKey);
            entries.add(new EnhanceEntry.Tag(itemKey, displayName, desc));
        }
        return entries;
    }

    public static int getMaxSlotsFor(ItemStack skillItem) {
        if (skillItem.isEmpty()) return 0;
        SkillConfig cfg = findConfig(skillItem);
        return cfg != null ? cfg.maxSlots() : 0;
    }

    public static ItemStack getItemFor(String enhanceKey) {
        DeferredHolderRef ref = ENHANCE_ITEMS.get(enhanceKey);
        return ref != null ? new ItemStack(ref.get()) : ItemStack.EMPTY;
    }

    /** 多策略查找：注册名 → 显示名 → null */
    private static SkillConfig findConfig(ItemStack stack) {
        // 策略1：注册名精确匹配
        String regKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        SkillConfig cfg = SKILLS.get(regKey);
        if (cfg != null) return cfg;
        // 策略2：中文显示名匹配
        String displayName = stack.getHoverName().getString();
        cfg = SKILLS.get(displayName);
        if (cfg != null) return cfg;
        // 策略3：显示名去掉格式符号后匹配
        String plain = net.minecraft.network.chat.Component.literal(displayName).getString().trim();
        return SKILLS.get(plain);
    }

    // ═══════════════════════════════════════════════════════════
    //  技能配置数据
    // ═══════════════════════════════════════════════════════════

    static {
        // ── 奔雷疾（主动，6槽）──
        registerSkill("奔雷疾", 6,
            "wuyingji", "gandian", "leizhenqianli", "benleixi", "pili", "leixiaoshan");

        // ── 雷鸣电甲（主动，6槽）──
        registerSkill("雷鸣电甲", 6,
            "pozhenjinshen", "leishen", "leizhenqianli", "benleixi", "pili", "leixiaoshan");

        // ── 天雷引（被动，3槽）──
        registerSkill("天雷引", 3,
            "wuyingji", "leishen", "pili");
    }
}
