package net.minecraft.client.yiz.api;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 复活系统注册表，挂接原版不死图腾路径 {@code checkTotemDeathProtection}。
 * 自定义复活动画同原版不死图腾。
 */
// 大白话: 不死方法
public final class UndyingRegistry {

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();
    public static final AtomicReference<ItemStack> PENDING_REVIVE_ITEM = new AtomicReference<>(ItemStack.EMPTY);

    private UndyingRegistry() {}

    @FunctionalInterface
    public interface Handler {
        ReviveResult getRevive(LivingEntity entity, DamageSource source);
    }

    public record ReviveResult(float targetHealth, ItemStack displayItem) {
        public static final ReviveResult NONE = new ReviveResult(0, ItemStack.EMPTY);

        public static ReviveResult revive(float health) {
            return new ReviveResult(health, ItemStack.EMPTY);
        }

        public static ReviveResult revive(float health, ItemStack display) {
            return new ReviveResult(health, display);
        }
    }

    public static void register(Handler handler) {
        HANDLERS.add(handler);
    }

    /**
     * 由 Mixin 在 checkTotemDeathProtection RETURN 调用
     */
    public static float tryRevive(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return 0;

        for (Handler handler : HANDLERS) {
            ReviveResult result = handler.getRevive(entity, source);
            if (result.targetHealth() > 0) {
                performRevive(entity, result);
                return result.targetHealth();
            }
        }
        return 0;
    }

    private static void performRevive(LivingEntity entity, ReviveResult result) {
        entity.setHealth(result.targetHealth());
        entity.deathTime = 0;
        entity.hurtTime = 0;
        entity.hurtMarked = false;

        if (entity instanceof Player player) {
            player.setInvisible(false);
        }

        // 原版不死图腾动画（entity event 35：粒子+音效）
        // 图标由客户端 Mixin 从 PENDING_REVIVE_ITEM 读取并替换
        if (!result.displayItem().isEmpty()) {
            PENDING_REVIVE_ITEM.set(result.displayItem());
        }
        entity.level().broadcastEntityEvent(entity, (byte) 35);
        PENDING_REVIVE_ITEM.set(ItemStack.EMPTY);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F);
    }
}
