package net.minecraft.client.yiz.tool.attribute;

import net.minecraft.client.yiz.tizMod;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受保护实体属性维护门禁 —— 给实体分配「受保护」属性值的统一入口。
 *
 * <p>本门禁解决两个问题：</p>
 * <ol>
 *   <li><b>统一分配</b>：给任意 {@link LivingEntity} 挂/改/移除 yizmodqzk 自定义属性值，
 *       使用专属前缀 {@value #PROTECTED_PREFIX} 的 modifier id（{@code yizmodqzk:prot_<idKey>}），
 *       与物品装备路径的 {@code item_/attr_/entity_/sync_} 前缀互不干扰。</li>
 *   <li><b>防外部移除</b>：配合 {@link net.minecraft.client.yiz.mixin.AttributeInstanceMixin}，
 *       对 {@value #PROTECTED_PREFIX} 前缀的 modifier 移除做「调用栈 + 包名」鉴权——
 *       本家（前置库 + 所有下游共用 {@code net.minecraft.client.yiz} 包根）、引擎帧、
 *       白名单 modid 放行；其他模组的移除操作被当场拒绝，保证我们分配的实体属性不被清掉。</li>
 * </ol>
 *
 * <p>鉴权判定复用 {@link net.minecraft.client.yiz.api.YizModQZKAPI#detectCallerModId} 的
 * StackWalker 惯例，以及 {@code YizxianMob.motionGate} 的"跳过门禁帧、定位真实调用者"思路。</p>
 */
public final class EntityAttributeGate {

    private EntityAttributeGate() {}

    /** 受保护 modifier id 前缀。完整 id = {@code yizmodqzk:prot_<idKey>}。 */
    public static final String PROTECTED_PREFIX = "prot_";

    /** 本家包前缀：前置库 + 所有下游共用此包根，形成信任边界。 */
    private static final String FAMILY_PACKAGE = "net.minecraft.client.yiz";

    /** 引擎帧前缀（原版 / NeoForge / Mojang 库）。 */
    private static final String[] ENGINE_PREFIXES = {
        "net.minecraft.",
        "net.neoforged.",
        "com.mojang.",
    };

    /** 被拦截目标类（mixin 注入到 AttributeInstance，必须跳过它的帧，否则外部移除会被误判为引擎帧放行）。 */
    private static final String TARGET_CLASS = "net.minecraft.world.entity.ai.attributes.AttributeInstance";

    /** 受信任 modid 白名单。 */
    private static final Set<String> TRUSTED_MODIDS = ConcurrentHashMap.newKeySet();
    static {
        TRUSTED_MODIDS.add("yizmodqzk");
        TRUSTED_MODIDS.add("yizxianmod");
    }

    /** 扩展受信任 modid（供未来下游模组接入）。 */
    public static void addTrustedModId(String modId) {
        TRUSTED_MODIDS.add(modId);
    }

    /** 判断 modifier id 是否受保护（path 以 {@value #PROTECTED_PREFIX} 开头）。 */
    public static boolean isProtectedId(ResourceLocation id) {
        return id != null && id.getPath().startsWith(PROTECTED_PREFIX);
    }

    /** 构造受保护 modifier id：{@code yizmodqzk:prot_<idKey>}。 */
    public static ResourceLocation protectedId(String idKey) {
        return ResourceLocation.fromNamespaceAndPath("yizmodqzk", PROTECTED_PREFIX + idKey);
    }

    /**
     * 受保护写入：给实体挂/改某属性值。鉴权后 remove + addPermanentModifier。
     * <p>value = 0 时仅移除（等价 {@link #remove}）。属性未挂在实体 AttributeSupplier 上时静默跳过。</p>
     */
    public static void set(LivingEntity entity, Holder<Attribute> attr, String idKey, double value) {
        AttributeInstance inst = entity != null ? entity.getAttribute(attr) : null;
        if (inst == null) return;
        if (!isCallerTrusted()) {
            tizMod.LOGGER.warn("[AttributeGate] 拒绝非受信任调用方写入受保护属性: {} idKey={}", attr, idKey);
            return;
        }
        ResourceLocation id = protectedId(idKey);
        inst.removeModifier(id);
        if (value != 0.0) {
            inst.addPermanentModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /** 受保护移除：鉴权后移除实体的某受保护属性 modifier。 */
    public static void remove(LivingEntity entity, Holder<Attribute> attr, String idKey) {
        if (entity == null) return;
        if (!isCallerTrusted()) {
            tizMod.LOGGER.warn("[AttributeGate] 拒绝非受信任调用方移除受保护属性: {} idKey={}", attr, idKey);
            return;
        }
        AttributeInstance inst = entity.getAttribute(attr);
        if (inst != null) inst.removeModifier(protectedId(idKey));
    }

    /**
     * 调用栈 + 包名鉴权：判定当前操作是否来自受信任调用方。
     * <p>供 {@link net.minecraft.client.yiz.mixin.AttributeInstanceMixin} 与 {@link #set}/{@link #remove} 复用。
     * 从栈顶向下跳过框架帧（门禁类包 / mixin 注入包 / 被拦截目标类 AttributeInstance），
     * 对第一个决定性调用者判定：本家包、引擎帧、或 @Mod 白名单 modid → 信任；否则拒绝。</p>
     */
    public static boolean isCallerTrusted() {
        try {
            // 只看「第一个决定性调用者」：跳过门禁/目标类/mixin 框架帧后，栈顶第一个业务帧判定。
            // 此前「全栈区段检查」（任一帧非信任即拒）过严——外部 mod 向引擎 tick 链注入的 mixin 帧
            // 会让本家 applyEntityAttributes / 实体编辑器写入被误拒。改为首决定性帧：
            // 外部 mod 直接调本门禁/写受保护属性时，其帧就是第一个决定性帧 → 正确拒绝；本家/引擎正常路径 → 放行。
            java.util.concurrent.atomic.AtomicReference<Boolean> verdict = new java.util.concurrent.atomic.AtomicReference<>();
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> {
                frames.forEach(frame -> {
                    if (verdict.get() != null) return;
                    Class<?> clazz = frame.getDeclaringClass();
                    if (!isDecisiveFrame(clazz)) return; // 跳过框架帧
                    verdict.set(isTrustedFrame(clazz, frame.getMethodName())); // 第一个决定性帧
                });
                return null;
            });
            return verdict.get() == null || verdict.get();
        } catch (Exception e) {
            tizMod.LOGGER.warn("[AttributeGate] 调用栈鉴权异常，默认拒绝", e);
            return false;
        }
    }

    /** 过滤框架帧，只留"决定性调用者"。 */
    private static boolean isDecisiveFrame(Class<?> clazz) {
        String pkg = clazz.getPackageName();
        // 跳过：门禁类所在包、mixin 注入包、被拦截目标类
        if (pkg.equals(EntityAttributeGate.class.getPackageName())) return false;
        if (pkg.startsWith("net.minecraft.client.yiz.mixin")) return false;
        return !clazz.getName().equals(TARGET_CLASS);
    }

    /** 判定单个调用帧是否受信任（本家 / 引擎帧且非外部 mixin / @Mod 白名单）。 */
    private static boolean isTrustedFrame(Class<?> clazz, String methodName) {
        String pkg = clazz.getPackageName();
        // 1. 本家：前置库 + 所有下游
        if (pkg.startsWith(FAMILY_PACKAGE)) return true;
        // 2. 引擎帧（原版引擎死亡/存档清理等场景）——但外部模组 mixin 注入引擎类的方法要识别
        if (isEngineFrame(pkg)) {
            return !isExternalMixinFrame(clazz, methodName);
        }
        // 3. @Mod 注解白名单
        for (Annotation ann : clazz.getAnnotations()) {
            if (ann.annotationType().getName().equals("net.neoforged.fml.common.Mod")) {
                try {
                    String modid = (String) ann.annotationType().getMethod("value").invoke(ann);
                    if (TRUSTED_MODIDS.contains(modid)) return true;
                } catch (Exception ignored) {}
            }
        }
        return false;
    }

    /** 识别外部模组 mixin 注入到引擎类的方法：方法带 MixinMerged 注解且注入来源非本家包。 */
    private static boolean isExternalMixinFrame(Class<?> clazz, String methodName) {
        try {
            for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                var anno = m.getAnnotation(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class);
                if (anno != null) {
                    return !anno.mixin().startsWith(FAMILY_PACKAGE);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isEngineFrame(String pkg) {
        for (String p : ENGINE_PREFIXES) {
            if (pkg.startsWith(p)) return true;
        }
        return false;
    }
}
