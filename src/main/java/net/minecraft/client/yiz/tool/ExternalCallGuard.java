package net.minecraft.client.yiz.tool;

import java.util.Set;

/**
 * 外部调用判定工具 —— 全栈区段白名单检查。
 *
 * <p>判定当前调用栈是否全部来自受信任帧（本家包 / 引擎帧且非外部 mixin 注入）。
 * 供速度动量门禁（YizxianMob.motionGate）与属性驱动的击退免疫拦截复用。</p>
 */
public final class ExternalCallGuard {

    private static final String FAMILY_PACKAGE = "net.minecraft.client.yiz";
    private static final String[] ENGINE_PREFIXES = {"net.minecraft.", "net.neoforged.", "com.mojang."};

    private ExternalCallGuard() {}

    /**
     * 当前调用栈是否全部来自受信任帧。
     *
     * @param skipMethods 需跳过的门禁方法名（override 自身链），可为 null
     * @return true=可信（放行）；false=存在外部帧（拒绝）
     */
    public static boolean isTrustedCall(Set<String> skipMethods) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 3; i < stack.length; i++) {
            String cn = stack[i].getClassName();
            if (cn.startsWith(FAMILY_PACKAGE)) {
                if (skipMethods != null && skipMethods.contains(stack[i].getMethodName())) continue;
                continue; // 本家业务帧
            }
            if (isEngineFrame(cn, stack[i].getMethodName())) continue;
            return false; // 非白名单帧
        }
        return true;
    }

    private static boolean isEngineFrame(String className, String methodName) {
        // 主动外力帧（爆炸等）：引擎包但属外力注入 → 非引擎
        if (className.startsWith("net.minecraft.world.level.Explosion")) return false;
        for (String p : ENGINE_PREFIXES) {
            if (className.startsWith(p)) {
                return !isExternalMixinFrame(className, methodName);
            }
        }
        return false;
    }

    /** 识别外部模组 mixin 注入到引擎类的方法：方法带 MixinMerged 注解且注入来源非本家包。 */
    private static boolean isExternalMixinFrame(String className, String methodName) {
        try {
            for (java.lang.reflect.Method m : Class.forName(className).getDeclaredMethods()) {
                if (!m.getName().equals(methodName)) continue;
                var anno = m.getAnnotation(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class);
                if (anno != null) {
                    return !anno.mixin().startsWith(FAMILY_PACKAGE);
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
