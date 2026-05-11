package net.minecraft.client.yiz.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent 入口点。
 * 由 VirtualMachine.loadAgent() 调用，获取 Instrumentation 实例后注册 ASM 转换器。
 */
public final class HealthAgent {

    private HealthAgent() {}

    /**
     * agent() 是标准 Java Agent 入口，JVM 通过此方法传递 Instrumentation。
     */
    public static void agent(String args, Instrumentation inst) {
        inst.addTransformer(new LivingHealthTransformer(), true);
    }

    /**
     * premain() 兼容 JVM 启动时 -javaagent 加载方式。
     */
    public static void premain(String args, Instrumentation inst) {
        agent(args, inst);
    }
}
