package net.minecraft.client.yiz;

import net.minecraft.client.yiz.api.AttributeBalanceRegistry;
import net.minecraft.client.yiz.api.StatusEffectAttributeRegistry;
import net.minecraft.client.yiz.api.StatusEffectAttributeRegistry.StatusEffectType;
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

        // 注册技能配置界面 Menu
        net.minecraft.client.yiz.editor.SkillConfigRegistries.register(modEventBus);

        // 注册技能强化物品 + 标签页
        net.minecraft.client.yiz.editor.EnhanceItemRegistries.register(modEventBus);

        // 注册 /yiz abolish / /yiz restore 物品废除 + 背包废除指令
        net.minecraft.client.yiz.tool.abolish.YizAbolishCommand.register();

        // 注册 /yiz setHealth <选择器> <数值> <类型1|2> 生命值修改指令
        net.minecraft.client.yiz.tool.YizSetHealthCommand.register();

        // 注册禁疗事件处理器（攻击后禁疗 + 治疗拦截）
        HealBanHandler.register();

        // 注册玩家数据附件
        ModAttachments.register(modEventBus);

        // 注册技能装载槽数据键（技能 HUD 同步用）
        net.minecraft.client.yiz.api.PlayerDataAPI.register(
            "yizmodqzk:load_slots", com.mojang.serialization.Codec.STRING, "");
        // 注册技能冷却数据键
        net.minecraft.client.yiz.api.PlayerDataAPI.register(
            "yizmodqzk:skill_cooldowns", com.mojang.serialization.Codec.STRING, "{}");
        // 注册技能充能数据键（充能式冷却：当前充能数 + 回充时间戳）
        net.minecraft.client.yiz.api.PlayerDataAPI.register(
            "yizmodqzk:skill_charges", com.mojang.serialization.Codec.STRING, "{}");
        // 注册被动充能状态键（攻击计数 + 临时buff态，供 HUD 同步）
        net.minecraft.client.yiz.api.PlayerDataAPI.register(
            "yizmodqzk:charge_state", com.mojang.serialization.Codec.STRING, "{}");
        // 注册技能配置容器持久化键
        net.minecraft.client.yiz.api.PlayerDataAPI.register(
            "yizmodqzk:skill_config_slots", com.mojang.serialization.Codec.STRING, "");

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
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COOLDOWN_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.HUIXIN);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KEGONG);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHIELD_VALUE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.DAMAGE_BLOCK);
                // 蓝条系统
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAX_MANA);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MANA_REGEN);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MANA_REGEN_PCT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MANA_COST_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MANA_COST);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MANA_COST_PER_SEC);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.GENERIC_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.DAMAGE_REDUCTION);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ON_HURT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_RATE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_VALUE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COUNTER_COUNT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COMBO_RATE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COMBO_VALUE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COMBO_COUNT);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.UNDYING);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ARMOR);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.ATTACK_STRENGTH);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SPELL_DEFENSE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SPELL_POWER);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.COOLDOWN_VALUE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SKILL_RANGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SKILL_INTERVAL);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.MAX_CHARGES);
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
                // 状态效果属性
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.STUN_ATTACK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SLOW_ATTACK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FREEZE_ATTACK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_ATTACK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KNOCKBACK_ATTACK);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.STUN_DEFENSE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SLOW_DEFENSE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FREEZE_DEFENSE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_DEFENSE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KNOCKBACK_DEFENSE);
                // 状态效果共享属性
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.STUN_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SLOW_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FREEZE_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_RANGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_INTERVAL);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KNOCKBACK_TIME);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.STUN_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SLOW_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.FREEZE_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.SHOCK_DAMAGE);
                e.add(net.minecraft.world.entity.EntityType.PLAYER, YizAttributes.KNOCKBACK_DAMAGE);
            });

        // 声明走全槽位汇总的 yizmodqzk 自定义属性（主手/副手/盔甲/饰品槽全部生效）
        // 注：未在此注册的 yizmodqzk 属性仍会被汇总生效，只是卸装时不会被主动清理 modifier
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DAMAGE_REDUCTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHIELD_VALUE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DAMAGE_BLOCK);
        // 蓝条系统
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MAX_MANA);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MANA_REGEN);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MANA_REGEN_PCT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MANA_COST_REDUCTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MANA_COST);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.MANA_COST_PER_SEC);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ARMOR);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.ATTACK_STRENGTH);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SPELL_DEFENSE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SPELL_POWER);
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
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COOLDOWN_REDUCTION);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.HUIXIN);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KEGONG);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.DODGE_CHANCE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.INVINCIBILITY_MULT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LIFE_REGEN_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.LIFE_REGEN_PCT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_VALUE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COUNTER_COUNT);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COMBO_RATE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COMBO_VALUE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.COMBO_COUNT);
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
        // 状态效果属性
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.STUN_ATTACK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SLOW_ATTACK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.FREEZE_ATTACK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_ATTACK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KNOCKBACK_ATTACK);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.STUN_DEFENSE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SLOW_DEFENSE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.FREEZE_DEFENSE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_DEFENSE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KNOCKBACK_DEFENSE);
        // 状态效果共享属性
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.STUN_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SLOW_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.FREEZE_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_RANGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_INTERVAL);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KNOCKBACK_TIME);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.STUN_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SLOW_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.FREEZE_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.SHOCK_DAMAGE);
        net.minecraft.client.yiz.core.sync.EquipmentAttributeSync.registerTrackedAttribute(YizAttributes.KNOCKBACK_DAMAGE);

        // 状态效果属性 → 注册表绑定
        StatusEffectAttributeRegistry.registerAttack(YizAttributes.STUN_ATTACK,      StatusEffectType.STUN);
        StatusEffectAttributeRegistry.registerAttack(YizAttributes.SLOW_ATTACK,      StatusEffectType.SLOW);
        StatusEffectAttributeRegistry.registerAttack(YizAttributes.FREEZE_ATTACK,    StatusEffectType.FREEZE);
        StatusEffectAttributeRegistry.registerAttack(YizAttributes.SHOCK_ATTACK,     StatusEffectType.SHOCK);
        StatusEffectAttributeRegistry.registerAttack(YizAttributes.KNOCKBACK_ATTACK, StatusEffectType.KNOCKBACK);
        StatusEffectAttributeRegistry.registerDefense(YizAttributes.STUN_DEFENSE,      StatusEffectType.STUN);
        StatusEffectAttributeRegistry.registerDefense(YizAttributes.SLOW_DEFENSE,      StatusEffectType.SLOW);
        StatusEffectAttributeRegistry.registerDefense(YizAttributes.FREEZE_DEFENSE,    StatusEffectType.FREEZE);
        StatusEffectAttributeRegistry.registerDefense(YizAttributes.SHOCK_DEFENSE,     StatusEffectType.SHOCK);
        StatusEffectAttributeRegistry.registerDefense(YizAttributes.KNOCKBACK_DEFENSE, StatusEffectType.KNOCKBACK);

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
        // 锁定系统（会心/渴攻）——框架内置逻辑
        NeoForge.EVENT_BUS.addListener(net.minecraft.client.yiz.handler.LockOnHandler::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(net.minecraft.client.yiz.handler.LockOnHandler::onLivingDamagePre);

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
            net.minecraft.client.yiz.handler.PassiveChargeTracker.onLogin(sp);
            // 加载技能配置存储（被动/技能/装载槽），使 onWornTick 分发器登录后即可读到被动槽内容
            var skillData = net.minecraft.client.yiz.editor.SkillConfigStorage.getOrCreate(sp.getUUID());
            net.minecraft.client.yiz.editor.SkillConfigStorage.loadFromPlayerData(sp, skillData);
            // 初始化技能充能（各槽补满起步）
            net.minecraft.client.yiz.handler.SkillChargeManager.onLogin(sp);
            // 攻击强度默认 1 点
            var atkStr = sp.getAttribute(YizAttributes.ATTACK_STRENGTH);
            if (atkStr != null && atkStr.getBaseValue() < 1.0) {
                atkStr.setBaseValue(1.0);
            }
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
        mirrorSpellDefense(event.getEntity());
        // ATTACK_RANGE 镜像：1 点 = +1 格交互距离
        mirrorAttackRange(event.getEntity());
        // 创造模式自动保护 + 重生无敌过期检查
        CreativeProtectionHandler.onPlayerTick(event.getEntity());
        // 被动物品 tick 分发（服务端权威）：遍历被动装载槽，调用 IPassiveItem.onWornTick
        dispatchPassiveTick(event.getEntity());
        // 技能充能回充（服务端，仅 ServerPlayer）
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.client.yiz.editor.EnhanceTagRegistry.tickLeixiaoshan(sp);
            net.minecraft.client.yiz.editor.EnhanceTagRegistry.tickBenleixi(sp);
            net.minecraft.client.yiz.editor.EnhanceTagRegistry.tickLeizhenqianli(sp);
            net.minecraft.client.yiz.handler.SkillChargeManager.tickRecharge(sp);
            net.minecraft.client.yiz.handler.TempAttributeHelper.tick(sp);
            net.minecraft.client.yiz.tool.health.ManaTracker.tickRegen(sp);
            net.minecraft.client.yiz.handler.ChargedShockTracker.tick(sp);
        }
    }

    /** 服务端每 tick 遍历被动装载槽，分发 {@link net.minecraft.client.yiz.api.IPassiveItem#onWornTick}（框架契约）。 */
    private static void dispatchPassiveTick(net.minecraft.world.entity.player.Player player) {
        if (player.level().isClientSide()) return;
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return;
        for (int i = 0; i < 3; i++) {
            net.minecraft.world.item.ItemStack stack = data.passiveLoad().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.client.yiz.api.IPassiveItem passive) {
                passive.onWornTick(player, stack);
            }
        }
    }

    /**
     * 玩家攻击命中时遍历被动装载槽，分发 {@link net.minecraft.client.yiz.api.IPassiveItem#onAttack}。
     * <p>由 LivingEntityMixin 在攻击事件中调用。供需要"每次攻击"响应的被动（如天雷引充能）使用。</p>
     *
     * @param target 被攻击的实体
     */
    public static void dispatchPassiveAttack(net.minecraft.server.level.ServerPlayer player,
                                             net.minecraft.world.entity.LivingEntity target) {
        if (player.level().isClientSide()) return;
        var data = net.minecraft.client.yiz.editor.SkillConfigStorage.get(player.getUUID());
        if (data == null) return;
        for (int i = 0; i < 3; i++) {
            net.minecraft.world.item.ItemStack stack = data.passiveLoad().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.client.yiz.api.IPassiveItem passive) {
                passive.onAttack(player, stack, target);
            }
        }
    }

    /** 攻击冷却缩减（实际逻辑在 PlayerMixin，此处仅触发）。 */

    /** 玩家重生 → 3 秒保护态。 */
    private void onCreativeProtectionRespawn(PlayerEvent.PlayerRespawnEvent event) {
        CreativeProtectionHandler.onPlayerRespawn(event.getEntity());
    }

    /** 法术防御镜像：≤20 提供击退韧性，>20 切换为击退免疫+无碰撞。 */
    private static void mirrorSpellDefense(net.minecraft.world.entity.LivingEntity entity) {
        var inst = entity.getAttribute(YizAttributes.SPELL_DEFENSE);
        if (inst == null) return;
        double val = inst.getValue();
        if (val <= 20.0) {
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                "yiz_spell_defense_mirror", val,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, YizAttributes.KNOCKBACK_IMMUNITY,
                "yiz_spell_defense_ki", 0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, YizAttributes.NO_COLLISION,
                "yiz_spell_defense_nc", 0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        } else {
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
                "yiz_spell_defense_mirror", 0,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, YizAttributes.KNOCKBACK_IMMUNITY,
                "yiz_spell_defense_ki", 1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
            net.minecraft.client.yiz.tool.attribute.ItemAttributeHandler.setEntityAttribute(
                entity, YizAttributes.NO_COLLISION,
                "yiz_spell_defense_nc", 1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
        }
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
