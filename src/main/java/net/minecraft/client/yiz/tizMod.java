package net.minecraft.client.yiz;

import net.minecraft.client.yiz.api.AttributeBalanceRegistry;
import net.minecraft.client.yiz.api.CritTracker;
import net.minecraft.client.yiz.api.ProjectileReflectionSystem;
import net.minecraft.client.yiz.attribute.YizAttributes;
import net.minecraft.client.yiz.core.CreativeProtectionConfig;
import net.minecraft.client.yiz.core.CreativeProtectionHandler;
import net.minecraft.client.yiz.core.VTableReplace;
import net.minecraft.client.yiz.core.asm.AsmBootstrapper;
import net.minecraft.client.yiz.core.registry.CreativeTabAutoRegistry;
import net.minecraft.client.yiz.core.registry.ModAttachments;
import net.minecraft.client.yiz.network.NetworkHandler;
import net.minecraft.client.yiz.tool.SimpleCommandRegistry;
import net.minecraft.client.yiz.tool.YizProtectCommand;
import net.minecraft.client.yiz.tool.health.HealBanHandler;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(tizMod.MODID)
public class tizMod {
    public static final String MODID = "yizmodqzk";
    public static final Logger LOGGER = LogUtils.getLogger();

    public tizMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // 注册网络同步处理器
        modEventBus.addListener(NetworkHandler::onRegisterPayloadHandlers);
        // 注册 PlayerDataAPI 自动同步
        NetworkHandler.registerPlayerDataSync();

        // 初始化简易指令注册器（下游模组通过 API 提交指令，无需自行订阅事件）
        SimpleCommandRegistry.init();

        // 注册 /yiz th 保护态切换指令
        YizProtectCommand.register();

        // 创造模式自动保护 + 重生 3 秒无敌（通过配置文件可关闭）
        CreativeProtectionConfig.ensureLoaded();
        NeoForge.EVENT_BUS.addListener(this::onCreativeProtectionRespawn);

        // 注册属性编辑台方块（阶段 A：方块+物品）
        net.minecraft.client.yiz.editor.AttributeEditorRegistries.register(modEventBus);

        // 注册 /yiz abolish / /yiz restore 物品废除 + 背包废除指令
        net.minecraft.client.yiz.tool.abolish.YizAbolishCommand.register();

        // 注册 /yiz setHealth <选择器> <数值> <类型1|2> 生命值修改指令
        net.minecraft.client.yiz.tool.YizSetHealthCommand.register();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // 注册玩家数据附件
        ModAttachments.register(modEventBus);

        // 注册自定义属性（暴击率、暴伤等）
        YizAttributes.ATTRIBUTES.register(modEventBus);

        // 创造标签页由下游模组手动注册，不再自动扫描
        // CreativeTabAutoRegistry.init(modEventBus);

        // 将自定义属性挂载到玩家实体（否则 getAttributeValue 抛异常）
        modEventBus.addListener(
            net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent.class,
            e -> {
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.CRIT_RATE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.CRIT_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LIFE_STEAL);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SPLASH_RADIUS);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SPLASH_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SPLASH_FALLOFF);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.HUIXIN);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KEGONG);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.DAMAGE_BLOCK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.GENERIC_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.DAMAGE_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ON_HURT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_RATE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_VALUE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_COUNT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.UNDYING);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARMOR);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.PROJECTILE_REFLECTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.NO_COLLISION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KNOCKBACK_IMMUNITY);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.PROJECTILE_IMMUNITY);
                // 迁自 EffectTag 的新属性
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MOVE_SPEED);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAX_RUN_SPEED);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.JUMP_STRENGTH);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.AIR_SPEED);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.JUMP_COUNT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.JUMP_HEIGHT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FALL_SAFE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FALL_REDUCE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.DODGE_CHANCE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.INVINCIBILITY_MULT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_IMMUNE_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_DAMAGE_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LIFE_REGEN_RATE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LIFE_REGEN_PCT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MELEE_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.RANGED_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAGIC_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SUMMON_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARMOR_PENETRATION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARMOR_PENETRATION_FLAT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_IMMUNE_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_IMMUNE_TIME_FLAT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_DAMAGE_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.LAVA_DAMAGE_REDUCTION_FLAT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.WATER_BREATH_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.WATER_BREATH_TIME_FLAT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ATTACK_RANGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FLIGHT_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.JUMP_SPEED);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAX_MINIONS);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAX_SENTRIES);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.WATER_BREATH_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARROW_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARROW_SPEED);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARROW_SAVE_CHANCE);
            });

        // 声明走全槽位汇总的 yizmodqzk 自定义属性（主手/副手/盔甲/饰品槽全部生效）
        // 注：未在此注册的 yizmodqzk 属性仍会被汇总生效，只是卸装时不会被主动清理 modifier
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DAMAGE_REDUCTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DAMAGE_BLOCK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ARMOR);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.GENERIC_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MELEE_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.RANGED_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ARMOR_PENETRATION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ARMOR_PENETRATION_FLAT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ATTACK_RANGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.JUMP_SPEED);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MAX_MINIONS);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MAX_SENTRIES);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.CRIT_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.CRIT_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LIFE_STEAL);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SPLASH_RADIUS);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SPLASH_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SPLASH_FALLOFF);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.HUIXIN);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KEGONG);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DODGE_CHANCE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.INVINCIBILITY_MULT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LIFE_REGEN_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LIFE_REGEN_PCT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_VALUE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_COUNT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.UNDYING);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KNOCKBACK_IMMUNITY);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.PROJECTILE_IMMUNITY);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.PROJECTILE_REFLECTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.NO_COLLISION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ON_HURT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LAVA_IMMUNE_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LAVA_IMMUNE_TIME_FLAT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LAVA_DAMAGE_REDUCTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LAVA_DAMAGE_REDUCTION_FLAT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.WATER_BREATH_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.WATER_BREATH_TIME_FLAT);

        // 配置伤害增幅属性的堆叠模式（默认 MULTIPLY → 改为 ADD 防膨胀）
        YizAttributes.setStackMode(YizAttributes.GENERIC_DAMAGE, YizAttributes.StackMode.ADD);
        YizAttributes.setStackMode(YizAttributes.MELEE_DAMAGE,   YizAttributes.StackMode.ADD);
        YizAttributes.setStackMode(YizAttributes.RANGED_DAMAGE,  YizAttributes.StackMode.ADD);
        YizAttributes.setStackMode(YizAttributes.MAGIC_DAMAGE,   YizAttributes.StackMode.ADD);
        YizAttributes.setStackMode(YizAttributes.SUMMON_DAMAGE,  YizAttributes.StackMode.ADD);
        YizAttributes.setStackMode(YizAttributes.CRIT_DAMAGE,    YizAttributes.StackMode.ADD);

        // 原版暴击标记 → 桥接 CriticalHitEvent → LivingDamageEvent
        NeoForge.EVENT_BUS.addListener((CriticalHitEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
                CritTracker.mark(player, event.isCriticalHit());
            }
        });

        // Register Forge event handlers
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(this::onPlayerTick);
        // onLevelLoad/onLevelSave removed (UnlockSavedData deleted)

        // 延迟加载 ASM Agent（此时 Mixin 已完成，不会与 geckolib 等模组冲突）
        try {
            AsmBootstrapper.start();
            LOGGER.info("ASM Agent bootstrapped from mod constructor");
        } catch (Exception e) {
            LOGGER.error("Failed to bootstrap ASM Agent from mod constructor", e);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("YizMod QZK Framework initialized");

        // 初始化 vtable 方法替换系统
        event.enqueueWork(() -> {
            try {
                VTableReplace.initDonors();
                LOGGER.info("VTableReplace donors initialized (available={})",
                        VTableReplace.isAvailable());
            } catch (Exception e) {
                LOGGER.warn("VTableReplace init skipped: {}", e.getMessage());
            }
        });
    }

    /**
     * Handle player login: 同步玩家数据到客户端。
     */
    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            resetUndying(sp);
            LOGGER.debug("Player {} logged in", sp.getGameProfile().getName());
        }
    }

    private void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer sp) {
            // 从旧实体读属性值（新实体属性可能尚未就绪），强制覆盖残留计数
            var oldInst = event.getOriginal().getAttribute(YizAttributes.UNDYING);
            int max = oldInst != null ? (int) oldInst.getValue() : 0;
            if (max > 0) net.minecraft.client.yiz.tool.health.EntityASMUtil
                .resetUndyingCharges(sp.getUUID(), max);
        }
    }

    private static void resetUndying(net.minecraft.world.entity.LivingEntity entity) {
        var inst = entity.getAttribute(YizAttributes.UNDYING);
        if (inst != null) {
            int max = (int) inst.getValue();
            if (max > 0) net.minecraft.client.yiz.tool.health.EntityASMUtil
                .resetUndyingCharges(entity.getUUID(), max);
        }
    }

    private void onPlayerTick(PlayerTickEvent.Post event) {
        ProjectileReflectionSystem.tick(event.getEntity());
        AttributeBalanceRegistry.enforceFloors(event.getEntity());
        // 全槽位同步 yizmodqzk 自定义属性（主手/副手/盔甲/饰品槽全部生效）
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.sync(event.getEntity());
        // ARMOR 镜像：1 点防御 = +1 护甲值 + +1 盔甲韧性
        mirrorArmor(event.getEntity());
        // ATTACK_RANGE 镜像：1 点 = +1 格交互距离
        mirrorAttackRange(event.getEntity());
        // 创造模式自动保护 + 重生无敌过期检查
        CreativeProtectionHandler.onPlayerTick(event.getEntity());
    }

    /** 玩家重生 → 3 秒保护态。 */
    private void onCreativeProtectionRespawn(PlayerEvent.PlayerRespawnEvent event) {
        CreativeProtectionHandler.onPlayerRespawn(event.getEntity());
    }

    /** 防御力镜像：读 yizmodqzk:armor → 1:1 写到原版 ARMOR + ARMOR_TOUGHNESS。 */
    private static void mirrorArmor(net.minecraft.world.entity.LivingEntity entity) {
        var inst = entity.getAttribute(YizAttributes.ARMOR);
        if (inst == null) return;
        double armor = inst.getValue();
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
            entity, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
            "yiz_armor_mirror", armor, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
            entity, net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS,
            "yiz_toughness_mirror", armor, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
    }

    /** 攻击距离镜像：读 yizmodqzk:attack_range → 1:1 写到原版 ENTITY_INTERACTION_RANGE。 */
    private static void mirrorAttackRange(net.minecraft.world.entity.LivingEntity entity) {
        var inst = entity.getAttribute(YizAttributes.ATTACK_RANGE);
        if (inst == null) return;
        double range = inst.getValue();
        net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
            entity, net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE,
            "yiz_range_mirror", range, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
    }
}
