package net.minecraft.client.yiz.effect.perception;

import net.minecraft.client.yiz.core.data.EffectNBTHandler;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * 物品绑定感知 — 词缀 (Affix)
 * 效果绑定到物品上，当物品在指定槽位时感知。
 */
public class ItemPerception implements PerceptionMode {

    private final Set<ItemSlot> slots;

    public ItemPerception(ItemSlot... slots) {
        this.slots = Set.of(slots);
    }

    public Set<ItemSlot> getSlots() {
        return slots;
    }

    @Override
    public boolean check(LivingEntity entity, EffectContext context) {
        for (ItemSlot slot : slots) {
            ItemStack stack = switch (slot) {
                case MAIN_HAND -> entity.getMainHandItem();
                case OFF_HAND -> entity.getOffhandItem();
                case HEAD -> entity.getItemBySlot(EquipmentSlot.HEAD);
                case CHEST -> entity.getItemBySlot(EquipmentSlot.CHEST);
                case LEGS -> entity.getItemBySlot(EquipmentSlot.LEGS);
                case FEET -> entity.getItemBySlot(EquipmentSlot.FEET);
                case INVENTORY -> entity instanceof net.minecraft.world.entity.player.Player player
                    ? getStackFromInventory(player, context)
                    : ItemStack.EMPTY;
            };

            if (!stack.isEmpty()) {
                // 验证物品实际绑定了此效果（通过 NBT 检测）
                if (context != null && context.effect() != null) {
                    List<AbstractEffect> itemEffects = EffectNBTHandler.getItemEffects(stack);
                    boolean hasEffect = itemEffects.stream()
                        .anyMatch(e -> e.getId().equals(context.effect().getId()));
                    if (!hasEffect) {
                        continue;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private ItemStack getStackFromInventory(net.minecraft.world.entity.player.Player player, EffectContext context) {
        // 1. 优先检查 context 来源物品（如交互事件传入的物品）
        if (context != null && context.itemStack() != null && !context.itemStack().isEmpty()) {
            return context.itemStack();
        }
        // 2. 遍历玩家背包 36 格（快捷栏 0-8 + 背包 9-35），找绑定此效果的物品
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                if (context != null && context.effect() != null) {
                    List<AbstractEffect> itemEffects = EffectNBTHandler.getItemEffects(stack);
                    boolean hasEffect = itemEffects.stream()
                        .anyMatch(e -> e.getId().equals(context.effect().getId()));
                    if (hasEffect) return stack;
                } else {
                    return stack; // 无效果上下文时，返回第一件非空物品
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public String getTypeName() {
        return "词缀 (Affix)";
    }

    @Override
    public PerceptionType getPerceptionType() {
        return PerceptionType.ITEM;
    }

    public enum ItemSlot {
        MAIN_HAND,
        OFF_HAND,
        HEAD,
        CHEST,
        LEGS,
        FEET,
        INVENTORY
    }
}
