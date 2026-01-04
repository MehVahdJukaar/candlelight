package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class BeanGettersProcessor implements ClassProcessor {

    private static final String ANNOTATION_DESC =
            ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.BeanGetters");

    private static final String NO_ALIAS_DESC =
            ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.NoAlias");

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
            private final List<MethodData> candidates = new ArrayList<>();
            private boolean enabled = false;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (ANNOTATION_DESC.equals(descriptor)) {
                    enabled = true;
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                existingMethods.add(name);

                if (!name.matches("[a-z].*")) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }

                MethodData data = new MethodData(name, descriptor, access);

                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(ASM9, mv) {
                    private boolean noAlias = false;

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (NO_ALIAS_DESC.equals(desc)) {
                            noAlias = true;
                        }
                        return super.visitAnnotation(desc, visible);
                    }

                    @Override
                    public void visitEnd() {
                        if (!noAlias) {
                            candidates.add(data);
                        }
                        super.visitEnd();
                    }
                };
            }

            @Override
            public void visitEnd() {
                if (!enabled) {
                    super.visitEnd();
                    return;
                }

                project.getLogger().lifecycle(
                        "[Candlelight] Generating getters for " + className.replace('/', '.')
                );

                for (MethodData m : candidates) {
                    Type returnType = Type.getReturnType(m.descriptor);
                    boolean isBoolean = returnType.getSort() == Type.BOOLEAN;

                    String prefix = isBoolean ? "is" : "get";
                    String alias =
                            prefix + Character.toUpperCase(m.name.charAt(0)) + m.name.substring(1);

                    if (existingMethods.contains(alias)) continue;

                    MethodVisitor mv = cv.visitMethod(
                            ACC_PUBLIC | (m.isStatic() ? ACC_STATIC : 0),
                            alias,
                            m.descriptor,
                            null,
                            null
                    );

                    mv.visitCode();

                    int localIndex = 0;

                    // load `this` if instance method
                    if (!m.isStatic()) {
                        mv.visitVarInsn(ALOAD, localIndex++);
                    }

                    // load all arguments
                    for (Type arg : Type.getArgumentTypes(m.descriptor)) {
                        mv.visitVarInsn(arg.getOpcode(ILOAD), localIndex);
                        localIndex += arg.getSize();
                    }

                    int opcode = m.isStatic() ? INVOKESTATIC : INVOKEVIRTUAL;

                    mv.visitMethodInsn(
                            opcode,
                            className,
                            m.name,
                            m.descriptor,
                            false
                    );

                    mv.visitInsn(returnType.getOpcode(IRETURN));
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();

                    project.getLogger().lifecycle(
                            "[Candlelight]  + generated getter: " + alias
                    );
                    modified[0] = true;
                }

                super.visitEnd();
            }

        }, 0);

        return modified[0] ? cw.toByteArray() : classBytes;
    }

    private record MethodData(String name, String descriptor, int access) {

        boolean isStatic() {
                return (access & ACC_STATIC) != 0;
            }
        }
}
