package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.util.Arrays;

public class OptionalInterfaceProcessor implements ClassProcessor {

    private static final String ANNOTATION_DESC =
            ClassUtils.toDescriptor("net.mehvahdjukaar.candlelight.api.OptionalInterface");

    @Override
    public byte[] transform(byte[] input, Project project, CandleLightExtension ext) {

        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

            private String iface;
            private String className;
            private boolean annotated = false;
            private boolean modified = false;

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {

                if (ANNOTATION_DESC.equals(desc)) {
                    annotated = true;

                    CandleLightPlugin.log(project,
                                " OptionalInterface found on " + className);

                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            iface = String.valueOf(value);

                            CandleLightPlugin.log(project,
                                    "  → target interface: " + iface);
                        }
                    };
                }

                return super.visitAnnotation(desc, visible);
            }

            @Override
            public void visit(int version,
                              int access,
                              String name,
                              String signature,
                              String superName,
                              String[] interfaces) {

                className = name;

                if (iface != null) {

                    String internal = iface.replace('.', '/');

                    if (interfaces == null) {
                        interfaces = new String[0];
                    }

                    boolean alreadyPresent = Arrays.asList(interfaces)
                            .contains(internal);

                    if (alreadyPresent) {
                        CandleLightPlugin.log(project,
                                " = skipping " + className.replace('/', '.')
                                        + " (already implements " + iface + ")");
                    } else {
                        String[] newInterfaces =
                                Arrays.copyOf(interfaces, interfaces.length + 1);

                        newInterfaces[interfaces.length] = internal;

                        interfaces = newInterfaces;
                        modified = true;

                        CandleLightPlugin.log(project,
                                " + injecting interface " + iface +
                                        " into " + className.replace('/', '.'));
                    }
                }

                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public void visitEnd() {

                if (!annotated) {
                } else if (iface == null) {
                    CandleLightPlugin.log(project,
                            " ! OptionalInterface missing value on " +
                                    className.replace('/', '.'));
                }

                if (modified) {
                    CandleLightPlugin.log(project,
                            " ✓ processed " + className.replace('/', '.'));
                }

                super.visitEnd();
            }
        };

        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}