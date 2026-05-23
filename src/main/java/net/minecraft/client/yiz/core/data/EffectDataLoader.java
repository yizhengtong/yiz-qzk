package net.minecraft.client.yiz.core.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.yiz.core.registry.ModRegistries;
import net.minecraft.client.yiz.effect.AbstractEffect;
import net.minecraft.client.yiz.effect.EffectContext;
import net.minecraft.client.yiz.effect.activation.ActivationCondition;
import net.minecraft.client.yiz.effect.activation.EntityAttackCondition;
import net.minecraft.client.yiz.effect.activation.PassiveCondition;
import net.minecraft.client.yiz.effect.activation.ProjectileHitCondition;
import net.minecraft.client.yiz.effect.parent.ParentType;
import net.minecraft.client.yiz.effect.perception.ContainerPerception;
import net.minecraft.client.yiz.effect.perception.EntityPerception;
import net.minecraft.client.yiz.effect.perception.ItemPerception;
import net.minecraft.client.yiz.effect.perception.PerceptionMode;
import net.minecraft.client.yiz.effect.rarity.Rarity;
import net.minecraft.client.yiz.tizMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * JSON 效果数据加载器
 * 扫描 data/{modid}/yizmodqzk/effects/ 目录加载 JSON 配置的效果。
 * 通过 AddReloadListenerEvent 注册。
 */
public class EffectDataLoader implements PreparableReloadListener {

    private static final Logger LOGGER = tizMod.LOGGER;
    private static final String EFFECTS_PATH = "yizmodqzk/effects";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    @Override
    public CompletableFuture<Void> reload(
        PreparationBarrier barrier,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // 阶段1: 收集所有 JSON 文件
            Map<ResourceLocation, JsonElement> effectData = new HashMap<>();

            // 扫描所有命名空间下的效果目录
            for (String namespace : resourceManager.getNamespaces()) {
                String path = namespace + ":" + EFFECTS_PATH;
                try {
                    // 尝试加载目录下的所有 JSON 资源
                    Collection<Resource> resources = resourceManager.getResourceStack(
                        ResourceLocation.parse(namespace + ":" + EFFECTS_PATH)
                    );

                    for (Resource resource : resources) {
                        try (Reader reader = resource.openAsReader()) {
                            JsonElement json = GSON.fromJson(reader, JsonElement.class);
                            if (json != null && json.isJsonObject()) {
                                JsonObject obj = json.getAsJsonObject();
                                if (obj.has("id")) {
                                    ResourceLocation id = ResourceLocation.parse(
                                        obj.get("id").getAsString()
                                    );
                                    effectData.put(id, json);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to parse effect JSON from {}: {}", namespace, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // 目录不存在或读取失败是正常的
                    LOGGER.debug("Failed to load effects from namespace: {}", namespace);
                }
            }

            return effectData;
        }, backgroundExecutor).thenCompose(barrier::wait).thenAcceptAsync(effectData -> {
            // 阶段2: 解析并注册效果
            int loaded = 0;
            for (Map.Entry<ResourceLocation, JsonElement> entry : effectData.entrySet()) {
                try {
                    AbstractEffect effect = parseEffect(entry.getKey(), entry.getValue().getAsJsonObject());
                    if (effect != null) {
                        LOGGER.info("Loaded effect: {}", effect.getId());
                        loaded++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to parse effect: {}", entry.getKey(), e);
                }
            }
            LOGGER.info("Effect data reload complete. Loaded {} effects.", loaded);
        }, gameExecutor);
    }

    /**
     * 解析 JSON 为 AbstractEffect 实例。
     */
    private AbstractEffect parseEffect(ResourceLocation id, JsonObject json) {
        // 基础字段
        String displayName = GsonHelper.getAsString(json, "display_name", id.toString());

        // 父类
        ParentType parentType = ParentType.ECHO;
        if (json.has("parent_type")) {
            try {
                parentType = ParentType.valueOf(GsonHelper.getAsString(json, "parent_type"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown parent_type for effect {}: {}", id, GsonHelper.getAsString(json, "parent_type"));
            }
        }

        // 等级
        int level = GsonHelper.getAsInt(json, "level", 1);

        // 稀有度
        Rarity rarity = Rarity.COMMON;
        if (json.has("rarity")) {
            try {
                rarity = Rarity.valueOf(GsonHelper.getAsString(json, "rarity"));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown rarity for effect {}: {}", id, GsonHelper.getAsString(json, "rarity"));
            }
        }

        // 感知方式
        Set<PerceptionMode> perceptionModes = parsePerceptionModes(json);

        // 生效条件
        ActivationCondition activationCondition = parseActivationCondition(json);

        // 创建效果实例
        return new AbstractEffect(id, id.toLanguageKey(), displayName, parentType, level, perceptionModes, activationCondition, rarity) {
            @Override
            public void execute(EffectContext context) {
                // JSON 加载的效果默认无自定义逻辑
                // 子类应 Override 此方法
            }
        };
    }

    private Set<PerceptionMode> parsePerceptionModes(JsonObject json) {
        Set<PerceptionMode> modes = new HashSet<>();
        if (!json.has("perception_modes") || !json.get("perception_modes").isJsonArray()) {
            return modes;
        }

        for (JsonElement elem : json.getAsJsonArray("perception_modes")) {
            JsonObject modeObj = elem.getAsJsonObject();
            String type = GsonHelper.getAsString(modeObj, "type");

            switch (type.toUpperCase()) {
                case "ITEM" -> {
                    String slot = GsonHelper.getAsString(modeObj, "slot", "MAIN_HAND");
                    try {
                        modes.add(new ItemPerception(
                            ItemPerception.ItemSlot.valueOf(slot)
                        ));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Unknown ItemSlot: {}", slot);
                    }
                }
                case "ENTITY" -> modes.add(new EntityPerception());
                case "CONTAINER" -> {
                    String containerType = GsonHelper.getAsString(modeObj, "container_type", "PERSONAL_CONTAINER");
                    try {
                        modes.add(new ContainerPerception(
                            ContainerPerception.ContainerType.valueOf(containerType)
                        ));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Unknown ContainerType: {}", containerType);
                    }
                }
                default -> LOGGER.warn("Unknown perception mode type: {}", type);
            }
        }

        return modes;
    }

    private ActivationCondition parseActivationCondition(JsonObject json) {
        if (!json.has("activation_condition") || !json.get("activation_condition").isJsonObject()) {
            return new PassiveCondition();
        }

        JsonObject cond = json.getAsJsonObject("activation_condition");
        String type = GsonHelper.getAsString(cond, "type", "PASSIVE");

        return switch (type.toUpperCase()) {
            case "ENTITY_ATTACK" -> new EntityAttackCondition();
            case "PROJECTILE_HIT" -> new ProjectileHitCondition();
            default -> new PassiveCondition();
        };
    }
}
