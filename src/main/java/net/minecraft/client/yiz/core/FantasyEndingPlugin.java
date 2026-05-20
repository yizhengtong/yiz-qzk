package net.minecraft.client.yiz.core;

import net.minecraft.client.yiz.core.asm.AsmBootstrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin 配置插件
 * 在 Mixin onLoad 回调中触发 ASM Agent 的加载。
 *
 * 为什么选择 onLoad 而不是 static {}：
 * - static {} 在所有 mixin 配置准备之前执行，会导致 LivingEntity 过早加载
 * - onLoad 在其他模组的 mixin config 加载完成之后才调用
 * - 避免与 geckolib 等模组的 LivingEntityMixin 冲突 (MixinTargetAlreadyLoadedException)
 *
 * 触发时序：
 * Mixin 框架初始化（其他模组的 mixin config 先准备）
 *   → FantasyEndingPlugin.onLoad()
 *     → AsmBootstrapper.start()
 *       → 提取 agent jar → 绕过自 attach → attach 自身 → loadAgent
 *         → agent() 被调用 → Instrumentation.addTransformer(LivingHealthTransformer)
 *           → 后续 LivingEntity 子类加载时 → ASM 改写
 */
public final class FantasyEndingPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FantasyEndingPlugin");

    @Override
    public void onLoad(String mixinPackage) {
        // ASM Agent 加载已移至模组构造器 (tizMod.<init>)，
        // 避免在 Mixin 准备阶段触发 LivingEntity 过早加载，
        // 与 geckolib 等模组的 LivingEntityMixin 冲突。
        LOGGER.info("[FantasyEndingPlugin] onLoad skipped (ASM bootstrap deferred to mod constructor)");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 不需要处理
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 不需要处理
    }

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 不需要处理
    }
}
