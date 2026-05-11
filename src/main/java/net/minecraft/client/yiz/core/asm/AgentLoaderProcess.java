package net.minecraft.client.yiz.core.asm;

/**
 * 子进程 Agent 加载器
 * 由 VmAttachment 以子进程方式启动，绕过 JDK 21 的自 attach 限制。
 *
 * <p>此类同时存在于主源码集和 agent 源码集中，确保在开发环境和生产环境
 * 都能被子进程加载。</p>
 *
 * <p>用法：AgentLoaderProcess &lt;parentPid&gt; &lt;agentJarPath&gt;</p>
 */
public final class AgentLoaderProcess {

    private AgentLoaderProcess() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("[AgentLoaderProcess] Usage: AgentLoaderProcess <pid> <agentJar>");
            System.exit(1);
            return;
        }

        String pid = args[0];
        String agentJar = args[1];

        try {
            var vm = com.sun.tools.attach.VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentJar, "");
            } finally {
                vm.detach();
            }
            System.out.println("[AgentLoaderProcess] Agent loaded successfully: pid=" + pid + " jar=" + agentJar);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[AgentLoaderProcess] Failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
