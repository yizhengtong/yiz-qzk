package net.minecraft.client.yiz.core.asm;

import net.minecraft.client.yiz.tizMod;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

/**
 * Agent 桥接器
 * 连接 Java Agent 与主模组，提供 Instrumentation 访问和运行时类操作工具。
 *
 * <p>由 HealthAgent 通过反射注入 Instrumentation 实例（agent jar 编译时不依赖主源码）。
 * 主模组通过此类获取 Instrumentation，执行运行时类重定义/重转换。</p>
 */
public final class AgentBridge {

    private static final Logger LOGGER = tizMod.LOGGER;

    private static volatile Instrumentation instrumentation;
    private static volatile boolean agentActive = false;
    private static volatile boolean agentTransformed = false;

    private AgentBridge() {}

    /**
     * 由 HealthAgent 在 agent() 初始化时通过反射调用。
     */
    @SuppressWarnings("unused")
    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
        agentActive = true;
        LOGGER.info("[AgentBridge] Instrumentation received");
    }

    /**
     * 由 LivingHealthTransformer 通过反射调用，标记已转换过类。
     */
    @SuppressWarnings("unused")
    public static void markTransformed() {
        agentTransformed = true;
    }

    /** Agent 是否已成功加载 */
    public static boolean isAgentActive() { return agentActive; }

    /** Transformer 是否已处理过类 */
    public static boolean isAgentTransformed() { return agentTransformed; }

    /**
     * 获取 Instrumentation 实例。
     * 在 Agent 成功加载前返回 null。
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * 检查 Agent 是否已加载并提供 Instrumentation。
     */
    public static boolean isAvailable() {
        return instrumentation != null;
    }

    /**
     * 重定义指定类的字节码。
     *
     * <p>与 retransform 不同，redefine 直接替换类的字节码，
     * 不会经过已注册的 ClassFileTransformer。</p>
     *
     * @param clazz  要重定义的类
     * @param bytes  新的字节码
     * @return 0=成功, 负值=错误码
     */
    public static int redefineClass(Class<?> clazz, byte[] bytes) {
        Instrumentation inst = instrumentation;
        if (inst == null) {
            LOGGER.error("[AgentBridge] Instrumentation not available");
            return -1;
        }
        if (clazz == null) {
            LOGGER.error("[AgentBridge] Class is null");
            return -1;
        }
        if (bytes == null) {
            LOGGER.error("[AgentBridge] Bytecode is null");
            return -1;
        }

        try {
            ClassDefinition cd = new ClassDefinition(clazz, bytes);
            inst.redefineClasses(cd);
            LOGGER.debug("[AgentBridge] Redefined class: {}", clazz.getName());
            return 0;
        } catch (ClassNotFoundException e) {
            LOGGER.error("[AgentBridge] Class not found: {}", clazz.getName(), e);
            return -2;
        } catch (UnmodifiableClassException e) {
            LOGGER.error("[AgentBridge] Class not modifiable: {}", clazz.getName(), e);
            return -3;
        } catch (LinkageError e) {
            LOGGER.error("[AgentBridge] Linkage error redefining: {}", clazz.getName(), e);
            return -4;
        } catch (Exception e) {
            LOGGER.error("[AgentBridge] Failed to redefine: {}", clazz.getName(), e);
            return -5;
        }
    }

    /**
     * 从类加载器中读取指定类的原始字节码。
     */
    public static byte[] getClassBytes(Class<?> clazz) throws IOException {
        String className = clazz.getName();
        String classAsPath = className.replace('.', '/') + ".class";
        ClassLoader cl = clazz.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(classAsPath)) {
            if (in == null) {
                throw new IOException("Class resource not found: " + classAsPath);
            }
            return in.readAllBytes();
        }
    }

    /**
     * 对已加载的类执行 retransform。
     * 会触发所有已注册的 ClassFileTransformer（含 LivingHealthTransformer）。
     */
    public static void retransformClasses(Class<?>... classes) throws UnmodifiableClassException {
        Instrumentation inst = instrumentation;
        if (inst == null) {
            throw new IllegalStateException("Instrumentation not available");
        }
        inst.retransformClasses(classes);
    }
}
