package net.minecraft.client.yiz.core.asm;

import com.sun.tools.attach.VirtualMachine;
import net.minecraft.client.yiz.tizMod;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * ASM 启动引导器
 * 负责在 Mixin 初始化阶段动态加载 Java Agent，注册 ASM ClassFileTransformer。
 *
 * 完整时序：
 * 1. FantasyEndingPlugin.&lt;clinit&gt; 触发 start()
 * 2. 从主 JAR 的 /META-INF/jarjar/ 解压 agent jar 到临时文件
 * 3. VmAttachment.allowAttachSelf() 绕过 JDK 自 attach 限制
 * 4. VirtualMachine.attach(pid) → loadAgent(tempJar)
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
     */
    public static synchronized void start() {
        if (initialized) return;
        initialized = true;

        LOGGER.info("[AsmBootstrapper] Initializing ASM agent...");

        try {
            // 1. 解压 agent jar
            File agentJar = extractAgentJar();
            if (agentJar == null) {
                LOGGER.error("[AsmBootstrapper] Failed to extract agent jar");
                return;
            }

            // 2. 绕过自 attach 限制
            VmAttachment.allowAttachSelf();
            LOGGER.debug("[AsmBootstrapper] Attach self allowed");

            // 3. 获取 VirtualMachine 并加载 agent
            VirtualMachine vm = VmAttachment.attachToSelf();
            LOGGER.debug("[AsmBootstrapper] Attached to self: pid={}", VmAttachment.getCurrentPid());

            VmAttachment.loadAgent(vm, agentJar.getAbsolutePath(), "");
            agentLoaded = true;
            LOGGER.info("[AsmBootstrapper] Agent loaded successfully");

            // 4. detach
            VmAttachment.detach(vm);

        } catch (Exception e) {
            LOGGER.error("[AsmBootstrapper] Failed to load ASM agent", e);
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
