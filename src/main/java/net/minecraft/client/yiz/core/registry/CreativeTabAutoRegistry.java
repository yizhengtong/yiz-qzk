package net.minecraft.client.yiz.core.registry;

import net.minecraft.client.yiz.api.IGeneralItem;
import net.minecraft.client.yiz.api.ISkillItem;
import net.minecraft.client.yiz.api.IWeaponItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.*;

public final class CreativeTabAutoRegistry {

    private static final Map<String, Class<?>> TAB_CATEGORIES = new LinkedHashMap<>();
    static {
        TAB_CATEGORIES.put("skill", ISkillItem.class);
        TAB_CATEGORIES.put("item", IGeneralItem.class);
        TAB_CATEGORIES.put("weapon", IWeaponItem.class);
    }

    private static final Map<String, String> TAB_LABELS = Map.of(
        "skill", "技能",
        "item", "物品",
        "weapon", "武器装备"
    );

    private CreativeTabAutoRegistry() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterEvent.class, event -> {
            event.register(Registries.CREATIVE_MODE_TAB, helper ->
                registerAllTabs(helper)
            );
        });
    }

    private static void registerAllTabs(
            RegisterEvent.RegisterHelper<CreativeModeTab> helper) {
        Map<String, Map<String, List<Item>>> grouped = new LinkedHashMap<>();

        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || "minecraft".equals(itemId.getNamespace())) continue;

            String modId = itemId.getNamespace();
            String category = getCategory(item);
            if (category == null) continue;

            grouped
                .computeIfAbsent(modId, k -> new LinkedHashMap<>())
                .computeIfAbsent(category, k -> new ArrayList<>())
                .add(item);
        }

        for (Map.Entry<String, Map<String, List<Item>>> modEntry : grouped.entrySet()) {
            String modId = modEntry.getKey();
            String modName = getModDisplayName(modId);
            Map<String, List<Item>> itemCats = modEntry.getValue();

            for (Map.Entry<String, List<Item>> catEntry : itemCats.entrySet()) {
                String categoryKey = catEntry.getKey();
                List<Item> items = catEntry.getValue();

                String label = TAB_LABELS.getOrDefault(categoryKey, categoryKey);
                ResourceLocation tabId = ResourceLocation.fromNamespaceAndPath(modId, categoryKey);

                Item iconItem = findIconItem(items);
                CreativeModeTab tab = CreativeModeTab.builder()
                    .title(Component.literal(modName + "-" + label))
                    .icon(() -> new ItemStack(iconItem))
                    .displayItems((params, output) -> {
                        for (Item item : items) {
                            if (item != null) output.accept(item);
                        }
                    })
                    .build();

                helper.register(tabId, tab);
            }
        }
    }

    private static String getCategory(Item item) {
        for (Map.Entry<String, Class<?>> entry : TAB_CATEGORIES.entrySet()) {
            if (entry.getValue().isInstance(item)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static Item findIconItem(List<Item> items) {
        if (items.isEmpty()) return null;
        Item first = items.get(0);
        if (first instanceof ISkillItem s) return s.getTabIcon();
        if (first instanceof IGeneralItem g) return g.getTabIcon();
        if (first instanceof IWeaponItem w) return w.getTabIcon();
        return first;
    }

    private static String getModDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
            .map(c -> c.getModInfo().getDisplayName())
            .orElse(modId);
    }
}