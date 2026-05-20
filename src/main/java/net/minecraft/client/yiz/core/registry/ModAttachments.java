package net.minecraft.client.yiz.core.registry;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mojang.serialization.Codec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

/**
 * 前置模组的附件类型注册
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, net.minecraft.client.yiz.tizMod.MODID);

    /**
     * 玩家数据附件：存储所有下游模组注册的键值对数据。
     * 以 NBT 字符串形式持久化到玩家存档。
     * 通过 {@link net.minecraft.client.yiz.api.PlayerDataAPI} 访问。
     */
    public static final Supplier<AttachmentType<String>> PLAYER_DATA_ATTACHMENT = ATTACHMENT_TYPES.register(
            "player_data",
            () -> AttachmentType.builder(() -> "{}")
                    .serialize(Codec.STRING)
                    .copyOnDeath()
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }

    private ModAttachments() {}
}
