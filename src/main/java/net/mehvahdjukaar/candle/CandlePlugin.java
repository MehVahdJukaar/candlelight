package net.mehvahdjukaar.candle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.objectweb.asm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class CandlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().withType(JavaCompile.class).configureEach(compileTask -> {
            compileTask.doLast(task -> {
                File classesDir = compileTask.getDestinationDirectory().get().getAsFile();
                project.getLogger().lifecycle("[Candle] Injecting getters into: " + classesDir);

                try {
                    injectGetters(classesDir, project);
                } catch (IOException e) {
                    throw new RuntimeException("Getter injection failed", e);
                }
            });
        });
    }

    private void injectGetters(File classesDir, Project project) throws IOException {
        walkClasses(classesDir, file -> {
            try (FileInputStream fis = new FileInputStream(file)) {

                ClassReader reader = new ClassReader(fis);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

                final boolean[] hasAnnotation = { false };
                final List<MethodData> candidates = new ArrayList<>();
                final Set<String> existingMethods = new HashSet<>();
                final String[] internalName = { null };

                ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

                    @Override
                    public void visit(
                            int version,
                            int access,
                            String name,
                            String signature,
                            String superName,
                            String[] interfaces
                    ) {
                        internalName[0] = name;
                        super.visit(version, access, name, signature, superName, interfaces);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals("Lnet/mehvahdjukaar/candle/api/GenerateGetters;")) {
                            hasAnnotation[0] = true;
                        }
                        return super.visitAnnotation(descriptor, visible);
                    }

                    @Override
                    public MethodVisitor visitMethod(
                            int access,
                            String name,
                            String descriptor,
                            String signature,
                            String[] exceptions
                    ) {
                        existingMethods.add(name);

                        // skip static
                        if ((access & Opcodes.ACC_STATIC) != 0)
                            return super.visitMethod(access, name, descriptor, signature, exceptions);

                        // skip constructors / clinit
                        if (name.equals("<init>") || name.equals("<clinit>"))
                            return super.visitMethod(access, name, descriptor, signature, exceptions);

                        // only zero-arg
                        if (!descriptor.startsWith("()"))
                            return super.visitMethod(access, name, descriptor, signature, exceptions);

                        // skip void
                        if (Type.getReturnType(descriptor).getSort() == Type.VOID)
                            return super.visitMethod(access, name, descriptor, signature, exceptions);

                        candidates.add(new MethodData(name, descriptor));
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }

                    @Override
                    public void visitEnd() {
                        if (!hasAnnotation[0]) {
                            super.visitEnd();
                            return;
                        }

                        project.getLogger().lifecycle("[Candle] Processing " + internalName[0]);

                        for (MethodData m : candidates) {
                            String getterName =
                                    "get" + Character.toUpperCase(m.name.charAt(0)) + m.name.substring(1);

                            if (existingMethods.contains(getterName))
                                continue;

                            MethodVisitor mv = writer.visitMethod(
                                    Opcodes.ACC_PUBLIC,
                                    getterName,
                                    m.descriptor,
                                    null,
                                    null
                            );

                            mv.visitCode();
                            mv.visitVarInsn(Opcodes.ALOAD, 0);
                            mv.visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL,
                                    internalName[0],
                                    m.name,
                                    m.descriptor,
                                    false
                            );
                            mv.visitInsn(getReturnOpcode(m.descriptor));
                            mv.visitMaxs(0, 0);
                            mv.visitEnd();

                            project.getLogger().lifecycle(
                                    "[Candle]   + " + getterName + "()"
                            );
                        }

                        super.visitEnd();
                    }
                };

                reader.accept(visitor, 0);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(writer.toByteArray());
                }

            } catch (Exception e) {
                project.getLogger().error("[Candle] Failed to process " + file, e);
            }
        });
    }

    private static int getReturnOpcode(String descriptor) {
        return switch (Type.getReturnType(descriptor).getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.IRETURN;
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.ARRAY, Type.OBJECT -> Opcodes.ARETURN;
            default -> throw new IllegalStateException("Unsupported return type");
        };
    }

    private static void walkClasses(File dir, ClassFileConsumer consumer) throws IOException {
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                walkClasses(file, consumer);
            } else if (file.getName().endsWith(".class")) {
                consumer.accept(file);
            }
        }
    }

    private interface ClassFileConsumer {
        void accept(File file) throws IOException;
    }

    private record MethodData(String name, String descriptor) {}
}
