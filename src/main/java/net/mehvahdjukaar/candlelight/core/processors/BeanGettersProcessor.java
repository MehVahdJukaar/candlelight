package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

public class BeanGettersProcessor implements ClassProcessor {

    private static final String ANNOTATION_DESC = ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.BeanGetters");

    private final Project project;

    public BeanGettersProcessor(Project project) {
        this.project = project;
    }

    @Override
    public byte[] transform(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        final boolean[] modified = {false};

        cr.accept(new ClassVisitor(ASM9, cw) {
            private String className;
            private final Set<String> existingMethods = new HashSet<>();
            private final List<MethodData> getterCandidates = new ArrayList<>();
            private boolean hasGenerateGetters = false;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (ANNOTATION_DESC.equals(descriptor)) {
                    hasGenerateGetters = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                existingMethods.add(name);
                // Only consider simple getters like this.age()
                if (name.matches("[a-z].*") && descriptor.startsWith("()")) {
                    getterCandidates.add(new MethodData(name, descriptor));
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                if (hasGenerateGetters) {
                    project.getLogger().lifecycle("[Candlelight] Generating getters for " + className.replace('/', '.'));
                    for (MethodData m : getterCandidates) {
                        String getter = "get" + Character.toUpperCase(m.name().charAt(0)) + m.name().substring(1);
                        if (existingMethods.contains(getter)) continue;

                        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, getter, m.descriptor(), null, null);
                        mv.visitCode();
                        mv.visitVarInsn(ALOAD, 0);
                        mv.visitMethodInsn(INVOKEVIRTUAL, className, m.name(), m.descriptor(), false);
                        mv.visitInsn(ClassUtils.getReturnOpcode(m.descriptor()));
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();

                        project.getLogger().lifecycle("[Candlelight]  + generated getter: " + getter);
                        modified[0] = true;
                    }
                }
                super.visitEnd();
            }

        }, 0);

        return modified[0] ? cw.toByteArray() : classBytes;
    }

    public record MethodData(String name, String descriptor) {}

}

