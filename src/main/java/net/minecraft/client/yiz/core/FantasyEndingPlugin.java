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
 * 在 Mixin 初始化阶段触发 ASM Agent 的加载。
 *
 * 为什么选择 Mixin 插件作为触发点：
 * - Mixin 插件在 Mixin 框架初始化时加载，早于任何实体类
 * - Mixin 插件的静态初始化在 ModLauncher 阶段执行
 * - 此时 Minecraft 的核心类尚未加载，ASM Transformer 能及时注册
 *
 * 触发时序：
 * Mixin 框架初始化
 *   → FantasyEndingPlugin.&lt;clinit&gt;
 *     → AsmBootstrapper.start()
 *       → 提取 agent jar → 绕过自 attach → attach 自身 → loadAgent
 *         → agent() 被调用 → Instrumentation.addTransformer(LivingHealthTransformer)
 *           → 后续 LivingEntity 子类加载时 → ASM 改写
 */
public final class FantasyEndingPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FantasyEndingPlugin");

    static {
        LOGGER.info("[FantasyEndingPlugin] Triggering ASM agent bootstrap...");
        try {
            AsmBootstrapper.start();
        } catch (Exception e) {
            LOGGER.error("[FantasyEndingPlugin] Failed to bootstrap ASM agent", e);
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
        // Mixin 包加载时的回调
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
