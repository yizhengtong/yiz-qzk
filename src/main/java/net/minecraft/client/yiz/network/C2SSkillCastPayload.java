package net.minecraft.client.yiz.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S：客户端请求释放技能（槽位 0=big, 1-3=skill）。
 * 服务端校验冷却后设置冷却时间戳，同步回客户端。
 */
public record C2SSkillCastPayload(int slot) implements CustomPacketPayload {

    public static final Type<C2SSkillCastPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("yizmodqzk", "skill_cast"));

    public static final StreamCodec<ByteBuf, C2SSkillCastPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SSkillCastPayload::slot,
            C2SSkillCastPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 客户端发送 */
    public static void send(int slot) {
        PacketDistributor.sendToServer(new C2SSkillCastPayload(slot));
    }

    /** 服务端处理 */
    public static void handle(C2SSkillCastPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            Player player = sp;
            int slot = payload.slot();

            // 若当前持有"临时+1上限"buff（满6次攻击奖励）→ 本次施法消耗它
            net.minecraft.client.yiz.handler.PassiveChargeTracker.onSkillCast(player);

            // 技能后首击标记 + 触发技能逻辑
            net.minecraft.world.item.ItemStack item = getSlotItem(sp, slot);
            if (!item.isEmpty()) {
                // 开关型技能（有MANA_COST_PER_SEC）跳过充能检查
                float manaPerSec = readManaPerSec(item);
                if (manaPerSec <= 0 && !net.minecraft.client.yiz.handler.SkillChargeManager.tryConsume(sp, slot))
                    return;

                net.minecraft.client.yiz.handler.PostSkillAttackTracker.mark(player, item);
                if (item.getItem() instanceof net.minecraft.client.yiz.api.ISkillItem si) {
                    net.minecraft.client.yiz.handler.LastCastSlotTracker.set(slot);
                    si.onCast(player, item);
                    net.minecraft.client.yiz.handler.LastCastSlotTracker.clear();
                    // 强化标签的 ACTIVATE 分发已下放到各技能的 onCast 内（onActivate），
                    // 由技能决定在耗蓝成功等前置通过后再触发，避免空蓝时误触发标签。
                }
            }
        });
    }

    private static net.minecraft.world.item.ItemStack getSlotItem(ServerPlayer sp, int slot) {
        net.minecraft.client.yiz.editor.SkillConfigStorage.Data data =
            net.minecraft.client.yiz.editor.SkillConfigStorage.get(sp.getUUID());
        if (data == null) return net.minecraft.world.item.ItemStack.EMPTY;
        return switch (slot) {
            case 0 -> data.bigLoad().getItem(0);
            case 1 -> data.skillLoad().getItem(0);
            case 2 -> data.skillLoad().getItem(1);
            case 3 -> data.skillLoad().getItem(2);
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
    }

    private static float readManaPerSec(net.minecraft.world.item.ItemStack stack) {
        var mods = stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
            net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        float val = 0;
        for (var e : mods.modifiers()) {
            if (e.attribute().is(net.minecraft.client.yiz.attribute.YizAttributes.MANA_COST_PER_SEC))
                val += (float) e.modifier().amount();
        }
        return val;
    }

    private static com.google.gson.JsonObject parseOrEmpty(String raw) {
        try {
            if (raw == null || raw.isEmpty()) return new com.google.gson.JsonObject();
            return com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) { return new com.google.gson.JsonObject(); }
    }
}
