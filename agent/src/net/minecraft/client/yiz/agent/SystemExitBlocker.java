package net.minecraft.client.yiz.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class SystemExitBlocker implements ClassFileTransformer {

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) throws IllegalClassFormatException {
        if (className == null || classfileBuffer == null) return null;
        if (className.startsWith("net/minecraft/client/yiz")) return null;

        try {
            ClassReader cr = new ClassReader(classfileBuffer);
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
            ClassVisitor cv = new SystemExitClassVisitor(cw, className);
            cr.accept(cv, ClassReader.EXPAND_FRAMES);
            return cw.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    private static class SystemExitClassVisitor extends ClassVisitor {
        private final String className;

        SystemExitClassVisitor(ClassWriter cw, String className) {
            super(Opcodes.ASM9, cw);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if ("<clinit>".equals(name)) {
                return new SystemExitMethodVisitor(mv, className);
            }
            return mv;
        }
    }

    private static class SystemExitMethodVisitor extends MethodVisitor {
        private final String className;

        SystemExitMethodVisitor(MethodVisitor mv, String className) {
            super(Opcodes.ASM9, mv);
            this.className = className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
            if (opcode == Opcodes.INVOKESTATIC
                    && "java/lang/System".equals(owner)
                    && "exit".equals(name)
                    && "(I)V".equals(desc)) {
                mv.visitInsn(Opcodes.POP);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Thread", "dumpStack",
                        "()V", false
                );
                System.err.println("[SystemExitBlocker] Blocked System.exit() in " + className.replace('/', '.'));
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}
