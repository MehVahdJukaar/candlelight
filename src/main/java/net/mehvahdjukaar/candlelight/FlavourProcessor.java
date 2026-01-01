package net.mehvahdjukaar.candlelight;

import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class FlavourProcessor implements ClassAnnotationProcessor {

    private static final String PROPERTY_FLAVOUR = "candlelight.flavour";
    private static final String FLAVOUR_ANNOTATION_DESC = "Lnet/mehvahdjukaar/candlelight/api/Flavour;";

    private final Project project;
    private final String flavour;

    public FlavourProcessor(Project project) {
        this.project = project;
        this.flavour = (String) project.findProperty(PROPERTY_FLAVOUR);
    }

    @Override
    public byte[] transform(byte[] classBytes) {
        if (flavour == null || flavour.isBlank()) return classBytes;

        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        final boolean[] modified = {false};

        cr.accept(new ClassVisitor(ASM9, cw) {
            private String className;
            private final List<MethodData> flavourMethods = new ArrayList<>();

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                className = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);

                return new MethodVisitor(ASM9, mv) {
                    boolean isFlavour;

                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDesc, boolean visible) {
                        if (FLAVOUR_ANNOTATION_DESC.equals(annotationDesc)) {
                            isFlavour = true;
                        }
                        return super.visitAnnotation(annotationDesc, visible);
                    }

                    @Override
                    public void visitEnd() {
                        if (isFlavour) {
                            if ((access & ACC_STATIC) == 0) {
                                throw new RuntimeException("@Flavour method must be static: "
                                        + className.replace('/', '.') + "#" + name + desc);
                            }
                            flavourMethods.add(new MethodData(access, name, desc, sig, ex));
                        }
                        super.visitEnd();
                    }
                };
            }

            @Override
            public void visitEnd() {
                // Rewrite detected @Flavour methods
                for (MethodData m : flavourMethods) {
                    modified[0] = true;

                    String implInternalName = computeImplInternalName(className, flavour);
                    File implFile = new File(project.getBuildDir(), "classes/java/main/" + implInternalName + ".class");

                    if (!implFile.exists()) {
                        throw new RuntimeException("[Candlelight] Missing flavour implementation for "
                                + className.replace('/', '.') + "#" + m.name
                                + "\nExpected: " + implInternalName.replace('/', '.'));
                    }

                    project.getLogger().lifecycle("[Candlelight] Rewriting @Flavour method: "
                            + className.replace('/', '.') + "#" + m.name
                            + " -> " + implInternalName.replace('/', '.'));

                    MethodVisitor mv = cv.visitMethod(m.access, m.name, m.descriptor, m.signature, m.exceptions);
                    mv.visitCode();

                    Type[] args = Type.getArgumentTypes(m.descriptor);
                    int idx = 0;
                    for (Type t : args) {
                        mv.visitVarInsn(t.getOpcode(ILOAD), idx);
                        idx += t.getSize();
                    }

                    mv.visitMethodInsn(INVOKESTATIC, implInternalName, m.name, m.descriptor, false);
                    mv.visitInsn(CandleLightClassWalker.getReturnOpcode(m.descriptor));
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }

                super.visitEnd();
            }
        }, 0);

        return modified[0] ? cw.toByteArray() : classBytes;
    }

    private static String computeImplInternalName(String internalName, String flavour) {
        int idx = internalName.lastIndexOf('/');
        String pkg = idx >= 0 ? internalName.substring(0, idx) : "";
        String simple = idx >= 0 ? internalName.substring(idx + 1) : internalName;
        return pkg.isEmpty()
                ? flavour + "/" + simple + "Impl"
                : pkg + "/" + flavour + "/" + simple + "Impl";
    }


    private record MethodData(int access, String name, String descriptor, String signature, String[] exceptions) {}
}
