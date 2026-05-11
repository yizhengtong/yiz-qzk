package net.minecraft.client.yiz.core.asm;

import com.sun.tools.attach.VirtualMachine;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JVM 附着工具
 * 绕过 JDK 9+ 的 ALLOW_ATTACH_SELF 限制，使进程可以 attach 自身。
 *
 * <p>直接使用 {@link VirtualMachine} API（jdk.attach 模块导出 com.sun.tools.attach 包），
 * 无需反射或 {@code --add-opens}。</p>
 *
 * 核心操作：
 * 1. 用 Unsafe 修改 HotSpotVirtualMachine.ALLOW_ATTACH_SELF 为 true
 * 2. 获取当前 JVM PID，attach 自身
 * 3. 调用 vm.loadAgent() 加载 agent jar
 */
public final class VmAttachment {

    private static final Logger LOGGER = LoggerFactory.getLogger("VmAttachment");

    private VmAttachment() {}

    /**
     * 尝试绕过 JVM 自 attach 禁令。
     * JDK 9+ 默认禁止进程 attach 自身，需要通过 Unsafe 修改内部标志位。
     * JDK 21+ 机制可能变化，允许失败。
     */
    public static boolean allowAttachSelf() {
        try {
            // 方式 1: 修改 jdk.internal.module.AllowAllPermission
            try {
                Class<?> permissionClass = Class.forName("jdk.internal.module.AllowAllPermission");
                Field allowed = permissionClass.getDeclaredField("allowed");
                setFieldWithUnsafe(allowed, null, true);
                return true;
            } catch (ClassNotFoundException ignored) {}

            // 方式 2: 修改 HotSpotVirtualMachine.ALLOW_ATTACH_SELF
            try {
                Class<?> hotSpotVmClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
                Field allowSelf = hotSpotVmClass.getDeclaredField("ALLOW_ATTACH_SELF");
                allowSelf.setAccessible(true);
                setFieldWithUnsafe(allowSelf, null, true);
                return true;
            } catch (ClassNotFoundException | NoSuchFieldException e) {
                tryAllUnsafeApproaches();
                return true;
            }
            // tryAllUnsafeApproaches() 可能仍失败
        } catch (Exception e) {
            LOGGER.warn("[VmAttachment] Cannot bypass attach-self restriction", e);
        }
        return false;
    }

    /**
     * 获取当前 JVM 的进程 ID。
     */
    public static String getCurrentPid() {
        try {
            // java.lang.ProcessHandle.current().pid()
            Object processHandle = Class.forName("java.lang.ProcessHandle")
                    .getMethod("current")
                    .invoke(null);
            long pid = (long) processHandle.getClass()
                    .getMethod("pid")
                    .invoke(processHandle);
            return Long.toString(pid);
        } catch (Exception e) {
            // 回退方式
            var mxBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
            return mxBean.getName().split("@")[0];
        }
    }

    /**
     * 通过 VirtualMachine.attach() 获取 VirtualMachine 实例。
     * 先直接尝试 attach（某些 JDK 版本允许自 attach），
     * 失败后再尝试绕过限制。
     */
    public static VirtualMachine attachToSelf() throws Exception {
        String pid = getCurrentPid();

        // 先直接尝试
        try {
            return VirtualMachine.attach(pid);
        } catch (Exception e) {
            LOGGER.warn("[VmAttachment] Direct attach failed, trying bypass...");
        }

        // 绕过自 attach 限制后再试
        allowAttachSelf();
        return VirtualMachine.attach(pid);
    }

    /**
     * 通过 VirtualMachine.loadAgent() 加载 agent jar。
     * 使用公开 API，无需反射。
     */
    public static void loadAgent(VirtualMachine vm, String agentJarPath, String args) throws Exception {
        vm.loadAgent(agentJarPath, args);
    }

    /**
     * detach VirtualMachine 实例。
     */
    public static void detach(VirtualMachine vm) throws Exception {
        vm.detach();
    }

    /**
     * 通过子进程绕过 JDK 21 自 attach 限制。
     *
     * <p>JDK 9+ 默认禁止进程 attach 自身，JDK 21 进一步移除了
     * ALLOW_ATTACH_SELF 标志位。此方法启动一个独立的子 JVM，
     * 由子进程执行 {@link VirtualMachine#attach attach} +
     * {@link VirtualMachine#loadAgent loadAgent}。
     * 跨进程 attach 是允许的，由此绕过自 attach 限制。</p>
     *
     * <p>classpath 策略：</p>
     * <ol>
     *   <li>优先使用 {@code java.class.path}（NeoForge 开发环境可用）</li>
     *   <li>回退到 agent jar 自身作为 classpath（生产环境 AgentLoaderProcess 也在 agent jar 中）</li>
     * </ol>
     *
     * @param agentJarPath 要加载的 agent jar 的绝对路径
     * @return 子进程退出码为 0 时返回 true
     */
    public static boolean loadAgentViaSubprocess(String agentJarPath) {
        try {
            String pid = getCurrentPid();
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classPath = System.getProperty("java.class.path");
            // 合并 java.class.path（开发环境）和 agent jar（生产环境），
            // 确保子进程总能找到 AgentLoaderProcess
            String subCp = agentJarPath;
            if (classPath != null && !classPath.isEmpty()) {
                subCp = classPath + File.pathSeparator + agentJarPath;
            }

            // 子进程命令：
            //   java --add-modules=jdk.attach --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED
            //        -cp <classpath> AgentLoaderProcess <pid> <agentJar>
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("--add-modules=jdk.attach");
            command.add("--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED");
            command.add("-cp");
            command.add(subCp);
            command.add("net.minecraft.client.yiz.core.asm.AgentLoaderProcess");
            command.add(pid);
            command.add(agentJarPath);

            LOGGER.info("[VmAttachment] Starting subprocess agent loader: pid={}, subCp={}", pid, subCp);
            LOGGER.debug("[VmAttachment] Command: {}", String.join(" ", command));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // 合并 stdout/stderr
            Process child = pb.start();

            // 读取子进程全部输出（在 waitFor 之前消费缓冲区，防止管道阻塞）
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(child.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.warn("[SubProcess] {}", line);
                }
            }

            int exitCode = child.waitFor();

            if (exitCode == 0) {
                LOGGER.info("[VmAttachment] Subprocess completed successfully");
                return true;
            } else {
                LOGGER.warn("[VmAttachment] Subprocess exited with code {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            LOGGER.error("[VmAttachment] Subprocess approach failed", e);
            return false;
        }
    }

    // ==================== Unsafe 工具 ====================

    private static final sun.misc.Unsafe UNSAFE = getUnsafe();

    private static sun.misc.Unsafe getUnsafe() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (Exception e) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field f = unsafeClass.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                return (sun.misc.Unsafe) f.get(null);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot get Unsafe instance", e2);
            }
        }
    }

    private static void setFieldWithUnsafe(Field field, Object target, boolean value) throws Exception {
        long offset = UNSAFE.staticFieldOffset(field);
        Object base = UNSAFE.staticFieldBase(field);
        UNSAFE.putBoolean(base, offset, value);
    }

    private static void tryAllUnsafeApproaches() {
        try {
            Class<?> hotSpotVmClass = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            for (Field field : hotSpotVmClass.getDeclaredFields()) {
                if (field.getType() == boolean.class
                        && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    setFieldWithUnsafe(field, null, true);
                    return;
                }
            }
        } catch (Exception ignored) {}
        throw new RuntimeException("Cannot find ALLOW_ATTACH_SELF field");
    }
}
