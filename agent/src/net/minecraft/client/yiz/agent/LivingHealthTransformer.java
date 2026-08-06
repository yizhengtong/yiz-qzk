package net.minecraft.client.yiz.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * ASM ClassFileTransformer
 * 在类加载时改写所有 LivingEntity 子类的关键方法：
 * - getHealth()          → 注入 specialGetHealth() 调用
 * - isAlive()             → 注入 specialIsAlive() 调用
 * - isDeadOrDying()       → 注入 specialIsDeadOrDying() 调用
 * - heal(float)           → 注入 applyHealBan() 调用（ASM 级禁疗）
 * - 非 LivingEntity 类中对上述方法的静态调用 → 重定向
 */
public class LivingHealthTransformer implements ClassFileTransformer {

    private static final String LIVING_ENTITY = "net/minecraft/world/entity/LivingEntity";
    private static final String ASM_UTIL = "net/minecraft/client/yiz/tool/health/EntityASMUtil";
    private static final String LOCK_CLASS = "net/minecraft/client/yiz/core/PlayerClassSwapper";

    /** 是否至少转换过一个类 */
    public static volatile boolean transformed = false;

    /** 已转换的类名集合（去重用） */
    private static final java.util.Set<String> transformedClasses =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** 已记录的模组类计数 */
    private static volatile int modClassCount = 0;

    private volatile boolean asmUtilAvailable = false;

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) return null;
        if (isExcluded(className)) return null;

        boolean isEntity = "net/minecraft/world/entity/Entity".equals(className);
        if (!isEntity && !isLivingEntitySubclass(classfileBuffer, loader)) return null;

        boolean isModClass = isModClass(className, classfileBuffer);
        transformed = true;

        // 记录被改写的类（去重）
        if (!transformedClasses.contains(className)) {
            transformedClasses.add(className);
            if (isModClass) {
                modClassCount++;
                System.out.println("[YizModQZK Agent] Rewriting mod class #" +
                        modClassCount + ": " + className.replace('/', '.') +
                        (isEntity ? " (Entity)" : ""));
            }
        }
        try {
            Class<?> bridgeClass = Class.forName("net.minecraft.client.yiz.core.asm.AgentBridge");
            bridgeClass.getMethod("markTransformed").invoke(null);
        } catch (Exception ignored) {}

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            String superName = cr.getSuperName();
            try {
                // 优先重算栈帧（默认 loader）。原版类 / 简单 mod 类正常。
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
                ClassVisitor cv = new HealthClassVisitor(cw, className, superName, isModClass, isEntity);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);
                return cw.toByteArray();
            } catch (Throwable frameFail) {
                // 复杂 mod 类（如 Legendary-Monsters）重算帧失败：默认 loader 找不到其类型 → TypeNotPresentException。
                // 注意：不能用自定义 loader 重算帧——成功加载 mod 类型会改变类加载/启动时序，
                // 触发属性 bake 阶段 swim_speed 等 unbound（Sodium 启动崩溃）。
                // 方案：fallback 到 COMPUTE_MAXS（只重算 maxs、保留原始栈帧）。本变换注入都是「方法头插段」或
                // 「返回前值替换」，不改变栈帧结构，保留原始帧安全。
                System.err.println("[PoshiAgent] COMPUTE_FRAMES fail " + className.replace('/', '.')
                    + ", fallback safe-mode(COMPUTE_MAXS): " + frameFail.getClass().getSimpleName());
                // 安全模式：跳过会产生分支的注入（破时 hurt/破无敌 die/remove），
                // 只做「值替换/开头存值」的帧安全注入（getHealth/heal/isAlive/setHealth）→ 保留原始帧不会 VerifyError。
                // 注意：COMPUTE_FRAMES + mod loader 对 mod 类重算帧会触发启动时序崩溃（swim_speed unbound），不可用。
                ClassWriter cw2 = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                ClassVisitor cv2 = new HealthClassVisitor(cw2, className, superName, isModClass, isEntity, true);
                cr.accept(cv2, ClassReader.EXPAND_FRAMES);
                return cw2.toByteArray();
            }
        } catch (Throwable e) {
            System.err.println("[PoshiAgent] TRANSFORM FAILED for " + className.replace('/', '.')
                + ": " + e.getClass().getName() + " - " + e.getMessage());
            if (e instanceof Error) e.printStackTrace(System.err);
            // 转换失败则返回原始字节码，不崩
            return null;
        }
    }

    // ==================== 排除名单 ====================

    private static boolean isExcluded(String className) {
        if (className.startsWith("net/minecraft/client/yiz")) return true;   // 本模组
        if (className.startsWith("net/minecraft/client/player")) return true; // LocalPlayer 特殊处理
        if (className.contains("$$")) return true; // Mixin 生成的内部类
        return false;
    }

    /**
     * 获取所有被 Agent 改写的类名列表（只读快照）。
     * 供主模组在启动完成后输出完整改写报告。
     */
    public static java.util.List<String> getTransformedClassNames() {
        synchronized (transformedClasses) {
            return new java.util.ArrayList<>(transformedClasses);
        }
    }

    /**
     * 获取被改写的第三方模组类数量。
     */
    public static int getModClassCount() {
        return modClassCount;
    }

    /**
     * 输出改写汇总报告到标准输出。
     */
    public static void printTransformationSummary() {
        java.util.List<String> all = getTransformedClassNames();
        System.out.println("[YizModQZK Agent] ========== Transformation Summary ==========");
        System.out.println("[YizModQZK Agent] Total classes rewritten: " + all.size());
        System.out.println("[YizModQZK Agent] Third-party mod classes: " + modClassCount);

        if (modClassCount > 0) {
            System.out.println("[YizModQZK Agent] --- Mod classes affected ---");
            synchronized (transformedClasses) {
                transformedClasses.stream()
                        .filter(name -> !name.startsWith("net/minecraft/"))
                        .filter(name -> !name.startsWith("java/"))
                        .filter(name -> !name.startsWith("jdk/"))
                        .filter(name -> !name.startsWith("sun/"))
                        .sorted()
                        .forEach(name -> System.out.println(
                                "[YizModQZK Agent]   " + name.replace('/', '.')));
            }
        }
        System.out.println("[YizModQZK Agent] ===========================================");
    }

    // ==================== 类检测 ====================

    private static boolean isLivingEntitySubclass(byte[] classBuffer, ClassLoader loader) {
        try {
            ClassReader cr = new ClassReader(classBuffer);
            String superName = cr.getSuperName();
            while (superName != null) {
                if (LIVING_ENTITY.equals(superName)) return true;
                superName = getSuperClassOf(superName, loader);
                if (superName == null) break;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String getSuperClassOf(String internalName, ClassLoader loader) {
        try {
            Class<?> clazz = Class.forName(internalName.replace('/', '.'), false,
                loader != null ? loader : ClassLoader.getSystemClassLoader());
            Class<?> superClazz = clazz.getSuperclass();
            return superClazz != null ? Type.getInternalName(superClazz) : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean isModClass(String className, byte[] classfileBuffer) {
        // 非 net.minecraft 包下的类视为模组类
        return !className.startsWith("net/minecraft/");
    }

    // ==================== ASM ClassVisitor ====================

    private static class HealthClassVisitor extends ClassVisitor {
        private final String className;
        private final String superName;
        private final boolean isModClass;
        private final boolean isEntityOnly;
        /** 安全模式：只做帧安全的「值替换/开头存值」注入，跳过会产生分支的注入（破时/die/remove）。 */
        private final boolean safeOnly;

        HealthClassVisitor(ClassWriter cw, String className, String superName, boolean isModClass, boolean isEntityOnly) {
            this(cw, className, superName, isModClass, isEntityOnly, false);
        }

        HealthClassVisitor(ClassWriter cw, String className, String superName, boolean isModClass, boolean isEntityOnly, boolean safeOnly) {
            super(Opcodes.ASM9, cw);
            this.className = className;
            this.superName = superName;
            this.isModClass = isModClass;
            this.isEntityOnly = isEntityOnly;
            this.safeOnly = safeOnly;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (mv == null) return null;

            // 安全模式（COMPUTE_MAXS fallback）：只注入帧安全的「值替换/开头存值」方法
            if (safeOnly) {
                if (isGetHealthMethod(name, desc)) return new GetHealthMethodVisitor(mv, access, name, desc);
                if (isIsAliveMethod(name, desc)) return new IsAliveMethodVisitor(mv, access, name, desc);
                if (isIsDeadOrDyingMethod(name, desc)) return new IsDeadOrDyingMethodVisitor(mv, access, name, desc);
                if (isHealMethod(name, desc)) return new HealMethodVisitor(mv, access, name, desc);
                if (isSetHealthMethod(name, desc)) return new SetHealthMethodVisitor(mv, access, name, desc);
                if (isModClass) return new StaticHealthCallVisitor(mv, access, name, desc);
                return mv;
            }

            // 破时 Agent 绕过
            if (!isEntityOnly && isHurtMethod(name, desc)) {
                System.err.println("[PoshiAgent] HURT " + className.replace('/', '.'));
                return new HurtBypassMethodVisitor(mv, superName);
            }
            if (!isEntityOnly && isInvulnerableToMethod(name, desc)) {
                System.err.println("[PoshiAgent] INVULN " + className.replace('/', '.'));
                return new InvulnerableBypassVisitor(mv);
            }

            // die() / remove() / setHealth 保护态拦截
            if (isDieMethod(name, desc)) {
                return new DieMethodVisitor(mv, access, name, desc);
            } else if (isRemoveMethod(name, desc)) {
                return new RemoveMethodVisitor(mv, access, name, desc);
            } else if (isSetHealthMethod(name, desc)) {
                return new SetHealthMethodVisitor(mv, access, name, desc);
            }

            // Entity.class 只需要 die/remove/setHealth，跳过健康值相关注入
            if (isEntityOnly) return mv;

            // 判断是否需要对此方法注入
            if (isGetHealthMethod(name, desc)) {
                return new GetHealthMethodVisitor(mv, access, name, desc);
            } else if (isIsAliveMethod(name, desc)) {
                return new IsAliveMethodVisitor(mv, access, name, desc);
            } else if (isIsDeadOrDyingMethod(name, desc)) {
                return new IsDeadOrDyingMethodVisitor(mv, access, name, desc);
            } else if (isHealMethod(name, desc)) {
                // 对 heal(float) 注入 ASM 级禁疗
                return new HealMethodVisitor(mv, access, name, desc);
            } else if (isModClass) {
                // 对模组类（非 Minecraft 原生类）的方法，检查是否有对 getHealth/isAlive/isDeadOrDying 的静态调用
                return new StaticHealthCallVisitor(mv, access, name, desc);
            }
            return mv;
        }
    }

    // ==================== 方法匹配规则 ====================

    private static boolean isGetHealthMethod(String name, String desc) {
        String lower = name.toLowerCase();
        if (!desc.equals("()F")) return false; // 必须返回 float
        if (lower.contains("gethealth")) return true;
        if (lower.startsWith("get") && (lower.endsWith("health") || lower.endsWith("hp"))) return true;
        if (name.equals("getCombatProgress")) return true;
        return false;
    }

    private static boolean isIsAliveMethod(String name, String desc) {
        return name.equals("isAlive") && desc.equals("()Z");
    }

    private static boolean isIsDeadOrDyingMethod(String name, String desc) {
        return (name.equals("isDead") || name.equals("isDeadOrDying")) && desc.equals("()Z");
    }

    private static boolean isHealMethod(String name, String desc) {
        return name.equals("heal") && desc.equals("(F)V");
    }

    private static boolean isDieMethod(String name, String desc) {
        return name.equals("die") && desc.startsWith("(Lnet/minecraft/world/damagesource/DamageSource;)");
    }

    private static boolean isRemoveMethod(String name, String desc) {
        return name.equals("remove") && desc.contains("RemovalReason");
    }

    private static boolean isSetHealthMethod(String name, String desc) {
        return name.equals("setHealth") && desc.equals("(F)V");
    }

    private static boolean isHurtMethod(String name, String desc) {
        return name.equals("hurt") && desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z");
    }

    private static boolean isInvulnerableToMethod(String name, String desc) {
        return name.equals("isInvulnerableTo")
            && desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;)Z");
    }

    // ==================== ASM: setHealth() 生命值纠正 ====================

    /**
     * 在 setHealth(float) 入口注入保护纠正：若实体处于保护态，
     * 强制 clamp 到 ≥1 且非 NaN。
     * 伪代码：
     *   if (isProtectedByUuid(this.getStringUUID()))
     *       newHealth = max(1, isNaN(newHealth) ? 1 : newHealth);
     */
    private static class SetHealthMethodVisitor extends AdviceAdapter {
        SetHealthMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv, access, name, desc);
        }

        @Override
        protected void onMethodEnter() {
            // ── 1. 保护态 clamp（原有逻辑）──
            Label notProtected = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/entity/Entity", "getStringUUID",
                    "()Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    LOCK_CLASS, "isProtectedByUuid",
                    "(Ljava/lang/String;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, notProtected);

            // 受保护：clamp health
            mv.visitVarInsn(Opcodes.FLOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    ASM_UTIL, "clampProtectedHealth",
                    "(F)F", false);
            mv.visitVarInsn(Opcodes.FSTORE, 1);

            mv.visitLabel(notProtected);
        }
    }

    // ==================== ASM: die() 保护态拦截 ====================

    /**
     * 在 die(DamageSource) 入口注入保护检查。
     * 伪代码: if (isProtectedByUuid(this.getStringUUID())) return;
     */
    private static class DieMethodVisitor extends AdviceAdapter {
        DieMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv, access, name, desc);
        }

        @Override
        protected void onMethodEnter() {
            Label after = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/entity/Entity", "getStringUUID",
                    "()Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "net/minecraft/client/yiz/core/PlayerClassSwapper",
                    "isProtectedByUuid", "(Ljava/lang/String;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, after);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(after);
        }
    }

    // ==================== ASM: remove() 保护态拦截 ====================

    /**
     * 在 remove(RemovalReason) 入口注入保护检查。
     * 伪代码: if (isProtectedByUuid(this.getStringUUID())) return;
     */
    private static class RemoveMethodVisitor extends AdviceAdapter {
        RemoveMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv, access, name, desc);
        }

        @Override
        protected void onMethodEnter() {
            Label after = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/entity/Entity", "getStringUUID",
                    "()Ljava/lang/String;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "net/minecraft/client/yiz/core/PlayerClassSwapper",
                    "isProtectedByUuid", "(Ljava/lang/String;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, after);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitLabel(after);
        }
    }

    // ==================== ASM: hurt() / isInvulnerableTo() 破时绕过 ====================

    private static class HurtBypassMethodVisitor extends MethodVisitor {
        private static final String BRIDGE = "net/minecraft/client/yiz/editor/PoshiBypassBridge";
        private final String superName;

        HurtBypassMethodVisitor(MethodVisitor mv, String superName) {
            super(Opcodes.ASM9, mv);
            this.superName = superName;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            Label notBypass = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/damagesource/DamageSource", "getEntity",
                    "()Lnet/minecraft/world/entity/Entity;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "shouldBypass",
                    "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, notBypass);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.FLOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "hurt",
                    "(Lnet/minecraft/world/damagesource/DamageSource;F)Z", false);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(notBypass);
        }
    }

    private static class InvulnerableBypassVisitor extends MethodVisitor {
        private static final String BRIDGE = "net/minecraft/client/yiz/editor/PoshiBypassBridge";

        InvulnerableBypassVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            Label notBypass = new Label();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/damagesource/DamageSource", "getEntity",
                    "()Lnet/minecraft/world/entity/Entity;", false);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, "shouldBypass",
                    "(Ljava/lang/Object;)Z", false);
            mv.visitJumpInsn(Opcodes.IFEQ, notBypass);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitInsn(Opcodes.IRETURN);
            mv.visitLabel(notBypass);
        }
    }

    // ==================== ASM MethodVisitor: getHealth ====================

    /**
     * 在 getHealth() 方法的 FRETURN 指令前注入：
     *   EntityASMUtil.specialGetHealth(栈顶float, this)
     *
     * 匹配规则：方法名包含 "gethealth"、以 "get" 开头 "health"/"hp" 结尾、"getCombatProgress"
     */
    private static class GetHealthMethodVisitor extends MethodVisitor {
        private final int access;
        private final String name;
        private final String desc;

        GetHealthMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv);
            this.access = access;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.FRETURN) {
                // 将 this 压栈
                if ((access & Opcodes.ACC_STATIC) == 0) {
                    // 非静态方法：ALOAD 0 加载 this
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                } else {
                    // 静态方法：ALOAD null
                    mv.visitInsn(Opcodes.ACONST_NULL);
                }
                // 调用 specialGetHealth(float, Object)
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialGetHealth",
                        "(FLjava/lang/Object;)F",
                        false
                );
            }
            super.visitInsn(opcode);
        }
    }

    // ==================== ASM MethodVisitor: isAlive ====================

    /**
     * 在 isAlive() 方法的 IRETURN 指令前注入：
     *   EntityASMUtil.specialIsAlive(栈顶boolean, this)
     */
    private static class IsAliveMethodVisitor extends MethodVisitor {
        private final int access;

        IsAliveMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv);
            this.access = access;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.IRETURN) {
                if ((access & Opcodes.ACC_STATIC) == 0) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                } else {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                }
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialIsAlive",
                        "(ZLjava/lang/Object;)Z",
                        false
                );
            }
            super.visitInsn(opcode);
        }
    }

    // ==================== ASM MethodVisitor: isDeadOrDying ====================

    /**
     * 在 isDeadOrDying() 方法的 IRETURN 指令前注入：
     *   EntityASMUtil.specialIsDeadOrDying(栈顶boolean, this)
     */
    private static class IsDeadOrDyingMethodVisitor extends MethodVisitor {
        private final int access;

        IsDeadOrDyingMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv);
            this.access = access;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.IRETURN) {
                if ((access & Opcodes.ACC_STATIC) == 0) {
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                } else {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                }
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialIsDeadOrDying",
                        "(ZLjava/lang/Object;)Z",
                        false
                );
            }
            super.visitInsn(opcode);
        }
    }

    // ==================== ASM MethodVisitor: heal(float) 禁疗注入 ====================

    /**
     * 在 heal(float) 方法的头部注入：
     *   float healAmount = EntityASMUtil.applyHealBan(this, healAmount);
     *
     * 这将替换传入的治疗量为应用禁疗配置后的值，
     * 对所有 LivingEntity 子类生效（包括重写 heal() 的模组实体）。
     *
     * 字节码注入序列：
     *   ALOAD 0        → this
     *   FLOAD 1        → healAmount 参数
     *   INVOKESTATIC   → EntityASMUtil.applyHealBan(LivingEntity, float) float
     *   FSTORE 1       → 将结果存回 healAmount 参数
     */
    private static class HealMethodVisitor extends MethodVisitor {

        HealMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            // ALOAD 0 (this)
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            // FLOAD 1 (healAmount)
            mv.visitVarInsn(Opcodes.FLOAD, 1);
            // INVOKESTATIC EntityASMUtil.applyHealBan(LivingEntity, float) float
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    ASM_UTIL,
                    "applyHealBan",
                    "(Lnet/minecraft/world/entity/LivingEntity;F)F",
                    false
            );
            // FSTORE 1 (store result back to healAmount)
            mv.visitVarInsn(Opcodes.FSTORE, 1);
        }
    }

    // ==================== ASM MethodVisitor: 静态调用重定向 ====================

    /**
     * 在模组类的所有方法中，将对 getHealth()/isAlive()/isDeadOrDying()
     * 的 INVOKEVIRTUAL 调用重定向到 EntityASMUtil.special* 方法。
     *
     * 这确保了第三方模组在自身代码中调用 entity.getHealth() 时，
     * 也能被我们的系统拦截（即使 entity 不是 LivingEntity 的子类）。
     */
    private static class StaticHealthCallVisitor extends MethodVisitor {

        StaticHealthCallVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            // 将 INVOKEVIRTUAL getHealth() → INVOKESTATIC specialGetHealth()
            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("getHealth") && desc.equals("()F")) {
                // swap: 将 (this, args...) 转为 (stackTopFloat, this)
                // 但 INVOKEVIRTUAL 的 this 已经在栈上，我们需要复制它
                mv.visitInsn(Opcodes.DUP);          // 复制 this 引用（用于 specialGetHealth 的第二个参数）
                mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        owner, name, desc, false    // 先正常调用 getHealth()，结果在栈顶
                );
                mv.visitInsn(Opcodes.SWAP);         // 交换：栈顶变为 this, 下面为 health
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialGetHealth",
                        "(FLjava/lang/Object;)F",
                        false
                );
                return;
            }

            // INVOKEVIRTUAL isAlive() → INVOKESTATIC specialIsAlive()
            if (opcode == Opcodes.INVOKEVIRTUAL && name.equals("isAlive") && desc.equals("()Z")) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        owner, name, desc, false
                );
                mv.visitInsn(Opcodes.SWAP);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialIsAlive",
                        "(ZLjava/lang/Object;)Z",
                        false
                );
                return;
            }

            // INVOKEVIRTUAL isDeadOrDying() / isDead() → INVOKESTATIC specialIsDeadOrDying()
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && (name.equals("isDeadOrDying") || name.equals("isDead"))
                    && desc.equals("()Z")) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        owner, name, desc, false
                );
                mv.visitInsn(Opcodes.SWAP);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        ASM_UTIL,
                        "specialIsDeadOrDying",
                        "(ZLjava/lang/Object;)Z",
                        false
                );
                return;
            }

            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
