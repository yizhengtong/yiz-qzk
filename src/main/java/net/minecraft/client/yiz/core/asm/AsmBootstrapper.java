package net.minecraft.client.yiz.core.asm;

import net.minecraft.client.yiz.tizMod;
import org.slf4j.Logger;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * ASM 启动引导器
 * 负责在 Mixin 初始化阶段动态加载 Java Agent，注册 ASM ClassFileTransformer。
 *
 * 完整时序：
 * 1. FantasyEndingPlugin.&lt;clinit&gt; 触发 start()
 * 2. 从主 JAR 的 /META-INF/jarjar/ 解压 agent jar 到临时文件
 * 3. 尝试直接 VirtualMachine.attach(pid) → loadAgent(tempJar)
 * 4. 若直接 attach 失败（JDK 21 自 attach 限制），回退到子进程 attach
 * 5. Agent 的 agent() 被调用 → Instrumentation 注册 LivingHealthTransformer
 * 6. 此后所有 LivingEntity 子类加载时都被 ASM 改写
 */
public final class AsmBootstrapper {

    private static final Logger LOGGER = tizMod.LOGGER;
    private static final String AGENT_RESOURCE_PATH = "/META-INF/jarjar/yizmodqzk-agent.jar";
    private static volatile boolean initialized = false;
    private static volatile boolean agentLoaded = false;

    private AsmBootstrapper() {}

    /**
     * 检查 ASM Agent 是否已成功加载。
     */
    public static boolean isAgentLoaded() {
        return agentLoaded;
    }

    /**
     * 启动 ASM Agent 加载流程。
     * 可被重复调用，只会执行一次。
     *
     * <p>加载策略优先级：</p>
     * <ol>
     *   <li>直接 attach — 在允许自 attach 的 JVM 上工作（JDK 8 及部分早期 JDK 版本）</li>
     *   <li>子进程 attach — 绕过 JDK 21 的自 attach 限制（标准方案，ByteBuddy 同款思路）</li>
     * </ol>
     */
    public static synchronized void start() {
        if (initialized) return;
        initialized = true;

        LOGGER.info("[AsmBootstrapper] Initializing ASM agent...");

        // 1. 解压 agent jar
        File agentJar = extractAgentJar();
        if (agentJar == null) {
            LOGGER.error("[AsmBootstrapper] Failed to extract agent jar");
            return;
        }

        // 2. 尝试直接 attach（策略 1）
        if (tryDirectAttach(agentJar)) {
            agentLoaded = true;
            LOGGER.info("[AsmBootstrapper] Agent loaded successfully via direct attach");
            return;
        }

        // 3. 回退到子进程 attach（策略 2）
        LOGGER.warn("[AsmBootstrapper] Direct attach failed, trying subprocess approach...");
        if (VmAttachment.loadAgentViaSubprocess(agentJar.getAbsolutePath())) {
            agentLoaded = true;
            LOGGER.info("[AsmBootstrapper] Agent loaded successfully via subprocess");
            return;
        }

        // 4. 所有方式均失败
        LOGGER.error("[AsmBootstrapper] All agent loading strategies failed");
    }

    /**
     * 尝试直接 attach 方式加载 agent。
     * 包括绕过自 attach 限制后再尝试。
     */
    private static boolean tryDirectAttach(File agentJar) {
        try {
            // 绕过自 attach 限制
            Field f = Class.forName("sun.tools.attach.HotSpotVirtualMachine")
                    .getDeclaredField("ALLOW_ATTACH_SELF");
            sun.misc.Unsafe unsafe = getUnsafe();
            unsafe.putBoolean(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), true);

            // 反射方式 attach（避免模块限制，参考 HelperLib）
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean()
                    .getName().split("@")[0];
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, pid);
            vmClass.getMethod("loadAgent", String.class).invoke(vm,
                    agentJar.getAbsolutePath());
            vmClass.getMethod("detach").invoke(vm);

            LOGGER.info("[AsmBootstrapper] Agent loaded successfully via reflection attach");
            return true;
        } catch (Exception e) {
            LOGGER.warn("[AsmBootstrapper] Direct attach failed: {}", e.getMessage());
            return false;
        }
    }

    private static sun.misc.Unsafe getUnsafe() {
        try {
            Constructor<sun.misc.Unsafe> c = sun.misc.Unsafe.class.getDeclaredConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从 classpath 中提取嵌入的 agent jar 到临时文件。
     */
    private static File extractAgentJar() {
        // 1. 首先尝试从 classpath 资源加载（生产环境：嵌入在 mod jar 中）
        InputStream in = AsmBootstrapper.class.getResourceAsStream(AGENT_RESOURCE_PATH);
        if (in != null) {
            try {
                File tempFile = File.createTempFile("fe_agent", ".jar");
                tempFile.deleteOnExit();
                Files.copy(in, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("[AsmBootstrapper] Agent extracted to {}", tempFile.getAbsolutePath());
                return tempFile;
            } catch (IOException e) {
                LOGGER.error("[AsmBootstrapper] Failed to extract agent jar", e);
                return null;
            } finally {
                try { in.close(); } catch (IOException ignored) {}
            }
        }

        LOGGER.warn("[AsmBootstrapper] Agent jar not found at {}", AGENT_RESOURCE_PATH);

        // 2. 开发环境：从系统属性获取路径（Gradle 传递）
        String propPath = System.getProperty("yizmodqzk.agent.jar");
        if (propPath != null && !propPath.isEmpty()) {
            File propFile = new File(propPath);
            if (propFile.isFile()) {
                LOGGER.debug("[AsmBootstrapper] Found agent jar via system property: {}", propFile.getAbsolutePath());
                return propFile;
            }
        }

        // 3. 开发环境回退：从 user.dir 向上搜索 build/libs
        File cwd = new File(System.getProperty("user.dir", "."));
        File buildLibs = new File(cwd, "build/libs");
        if (!buildLibs.isDirectory()) {
            // 尝试项目根目录（run/client -> .. -> project root）
            buildLibs = new File(cwd.getParentFile(), "build/libs");
        }
        if (buildLibs.isDirectory()) {
            File[] matches = buildLibs.listFiles((dir, name) -> name.startsWith("yizmodqzk-agent-") && name.endsWith(".jar"));
            if (matches != null && matches.length > 0) {
                LOGGER.debug("[AsmBootstrapper] Found agent jar at {}", matches[0].getAbsolutePath());
                return matches[0];
            }
        }

        LOGGER.error("[AsmBootstrapper] Agent jar not found in any location");
        return null;
    }
}
