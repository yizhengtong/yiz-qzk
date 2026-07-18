package net.minecraft.client.yiz.editor;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：技能加强操作（+/- 等级调整）。
 * 服务端扣除经验值并记录加强等级（按技能注册名隔离）。
 */
public record C2SSkillEnhancePayload(String skillRegName, int enhanceSlot, int newLevel) implements CustomPacketPayload {

    public static final Type<C2SSkillEnhancePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "skill_enhance"));

    public static final StreamCodec<ByteBuf, C2SSkillEnhancePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SSkillEnhancePayload::skillRegName,
            ByteBufCodecs.VAR_INT, C2SSkillEnhancePayload::enhanceSlot,
            ByteBufCodecs.VAR_INT, C2SSkillEnhancePayload::newLevel,
            C2SSkillEnhancePayload::new
        );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送加强请求。 */
    public static void send(String skillRegName, int enhanceSlot, int newLevel) {
        PacketDistributor.sendToServer(new C2SSkillEnhancePayload(skillRegName, enhanceSlot, newLevel));
    }

    /** 服务端处理：扣除经验值，写入物品 NBT。 */
    public static void handle(C2SSkillEnhancePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            var data = SkillConfigStorage.get(sp.getUUID());
            if (data == null) return;
            ItemStack item = data.skillUpgrade().getItem(0);
            if (item.isEmpty()) return;
            int oldLevel = SkillConfigStorage.getEnhanceLevel(item, payload.enhanceSlot());
            int delta = payload.newLevel() - oldLevel;
            int xpCost = delta * 100;
            if (xpCost > 0) {
                int available = getTotalXp(sp);
                if (available < xpCost) return;
                sp.giveExperiencePoints(-xpCost);
            }
            SkillConfigStorage.setEnhanceLevel(item, payload.enhanceSlot(), payload.newLevel());
        });
    }

    /** 计算玩家当前总经验点数。 */
    public static int getTotalXp(net.minecraft.world.entity.player.Player player) {
        int total = 0;
        int level = player.experienceLevel;
        for (int i = 0; i < level; i++) total += xpForLevel(i);
        total += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return total;
    }

    private static int xpForLevel(int level) {
        if (level >= 30) return 9 * level - 158;
        if (level >= 15) return 5 * level - 38;
        return 2 * level + 7;
    }
}
