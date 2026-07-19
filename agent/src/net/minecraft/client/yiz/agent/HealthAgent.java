package net.minecraft.client.yiz.agent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java Agent 入口点。
 * 由 VirtualMachine.loadAgent() 调用 JVM 标准入口 agentmain()，
 * 获取 Instrumentation 实例后注册 ASM 转换器。
 *
 * 加载流程：
 * 1. 注册 LivingHealthTransformer（支持 retransform）
 * 2. 将 Instrumentation 存入 AgentBridge（供主模组运行时使用）
 * 3. 扫描已加载类，对 LivingEntity 子类执行 retransform——
 *    确保在 Agent 加载前已实例化的实体类也能被 ASM 改写
 *
 * JVM Agent 规范说明：
 * - 动态加载（VirtualMachine.loadAgent）→ agentmain(String, Instrumentation)
 * - 启动时加载（-javaagent）→ premain(String, Instrumentation)
 * - 自定义命名如 agent() 不会被 JVM 识别
 */
public final class HealthAgent {

    private HealthAgent() {}

    private static final String BRIDGE_CLASS = "net.minecraft.client.yiz.core.asm.AgentBridge";

    public static void agentmain(String args, Instrumentation inst) {
        System.err.println("[HealthAgent] Initializing agent...");
        try {
            // 1. 注册 Transformer（canRetransform=true 支持后续 retransform）
            System.err.println("[HealthAgent] Registering LivingHealthTransformer...");
            inst.addTransformer(new LivingHealthTransformer(), true);
            System.err.println("[HealthAgent] Registering SystemExitBlocker...");
            inst.addTransformer(new SystemExitBlocker(), true);

            // 2. 通过反射将 Instrumentation 存入主模组的 AgentBridge
            storeInstrumentation(inst);

            // 3. 对已加载的 LivingEntity 子类执行 retransform
            retransformLoadedEntities(inst);

            System.err.println("[HealthAgent] Agent initialized successfully");
        } catch (Throwable t) {
            System.err.println("[HealthAgent] FAILED to initialize agent:");
            t.printStackTrace();
            throw t;
        }
    }

    /**
     * 将 Instrumentation 实例通过反射存入主模组的 AgentBridge 类。
     * Agent jar 编译时不依赖主源码，故使用 Class.forName 反射访问。
     */
    private static void storeInstrumentation(Instrumentation inst) {
        try {
            Class<?> bridge = Class.forName(BRIDGE_CLASS);
            bridge.getMethod("setInstrumentation", Instrumentation.class).invoke(null, inst);
            System.err.println("[HealthAgent] Instrumentation stored in AgentBridge");
        } catch (Exception e) {
            System.err.println("[HealthAgent] Failed to store Instrumentation: " + e.getMessage());
        }
    }

    /**
     * 扫描当前 JVM 中所有已加载的类，找出 LivingEntity 子类并执行 retransform。
     *
     * 为什么要做这一步：
     * - inst.addTransformer() 只对注册后新加载的类生效
     * - 在 Agent 加载时，Minecraft 可能已经加载了 Player、Zombie 等实体类
     * - 如果不 retransform，这些已加载的实体类不会被 ASM 改写
     * - 后续通过 retransformClasses() 让 Transformer 重新处理它们的字节码
     */
    private static void retransformLoadedEntities(Instrumentation inst) {
        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        List<Class<?>> toRetransform = new ArrayList<>();

        for (Class<?> clazz : loadedClasses) {
            if (!isModifiableEntityClass(clazz)) continue;
            toRetransform.add(clazz);
        }

        if (toRetransform.isEmpty()) {
            System.err.println("[HealthAgent] No loaded LivingEntity subclasses to retransform");
            return;
        }

        System.err.println("[HealthAgent] Retransforming " + toRetransform.size()
                + " loaded LivingEntity subclasses...");
        try {
            inst.retransformClasses(toRetransform.toArray(new Class<?>[0]));
            System.err.println("[HealthAgent] Retransformation complete");
        } catch (UnmodifiableClassException e) {
            System.err.println("[HealthAgent] Some classes could not be retransformed: "
                    + e.getMessage());
        } catch (Throwable t) {
            System.err.println("[HealthAgent] Retransformation failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * 判断一个已加载类是否需要被 retransform：
     * - 是 LivingEntity 子类
     * - 不在排除名单中
     */
    private static boolean isModifiableEntityClass(Class<?> clazz) {
        String name = clazz.getName();

        // 排除本模组类
        if (name.startsWith("net.minecraft.client.yiz")) return false;
        // 排除客户端玩家类（由 PlayerMixin 单独处理）
        if (name.startsWith("net.minecraft.client.player")) return false;
        // 排除 Mixin 生成类
        if (name.contains("$$")) return false;

        // 检查是否为 LivingEntity 子类
        Class<?> superClass = clazz.getSuperclass();
        while (superClass != null) {
            if (superClass.getName().equals("net.minecraft.world.entity.LivingEntity")) {
                return true;
            }
            superClass = superClass.getSuperclass();
        }
        return false;
    }

    /**
     * premain() 兼容 JVM 启动时 -javaagent 加载方式。
     */
    public static void premain(String args, Instrumentation inst) {
        agentmain(args, inst);
    }
}
