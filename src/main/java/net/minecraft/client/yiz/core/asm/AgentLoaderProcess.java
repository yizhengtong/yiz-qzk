package net.minecraft.client.yiz.core.asm;

import com.sun.tools.attach.VirtualMachine;

/**
 * 子进程 Agent 加载器
 * 由 VmAttachment 以子进程方式启动，绕过 JDK 21 的自 attach 限制。
 *
 * <p>JDK 9+ 默认禁止进程 attach 自身（self-attach），但跨进程 attach 是允许的。
 * 此类的 main() 作为独立 JVM 进程运行，attach 到父 JVM 并加载 agent。</p>
 *
 * <p>此类的依赖被刻意控制在 JDK 标准库 + jdk.attach 模块范围内，
 * 不引用任何 Minecraft/NeoForge 类。</p>
 *
 * <p><b>双副本说明：</b>此类同时存在于主源码集（本文件）和 agent 源码集
 * （{@code agent/src/.../AgentLoaderProcess.java}）中，确保在开发环境
 * （通过 {@code java.class.path} 加载）和生产环境（通过 agent JAR 加载）
 * 都能被子进程找到。两份副本保持完全一致，修改时需同步更新。</p>
 *
 * <p>用法：AgentLoaderProcess &lt;parentPid&gt; &lt;agentJarPath&gt;</p>
 */
public final class AgentLoaderProcess {

    private AgentLoaderProcess() {}

    /**
     * 子进程入口。
     *
     * @param args [parentPid, agentJarPath]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("[AgentLoaderProcess] Usage: AgentLoaderProcess <pid> <agentJar>");
            System.exit(1);
            return;
        }

        String pid = args[0];
        String agentJar = args[1];

        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentJar, "");
            } finally {
                vm.detach();
            }
            System.out.println("[AgentLoaderProcess] Agent loaded successfully: pid=" + pid + " jar=" + agentJar);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[AgentLoaderProcess] Failed to load agent: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
