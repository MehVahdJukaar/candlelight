package net.mehvahdjukaar.candlelight;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

@ApiStatus.Internal
public class CandleLightPlugin implements Plugin<Project> {

    private static final String ANNO_GENERATE_GETTERS =
            "Lnet/mehvahdjukaar/candlelight/api/GenerateGetters;";
    private static final String ANNO_FLAVOUR =
            "Lnet/mehvahdjukaar/candlelight/api/Flavour;";
    
    private static final String PROPERTY_FLAVOUR = "candlelight.flavour";

    @Override
    public void apply(Project project) {
        // IMPORTANT: flavour present => flavoured project, absent => common project
        String flavour = (String) project.findProperty(PROPERTY_FLAVOUR);
        final boolean isFlavouredProject = flavour != null && !flavour.isBlank();
        final String flavourName = isFlavouredProject ? flavour : null;

        project.getTasks().withType(JavaCompile.class).configureEach(compileTask -> {
            compileTask.doLast(task -> {
                File classesDir = compileTask.getDestinationDirectory().get().getAsFile();
                project.getLogger().lifecycle(
                        "[Candlelight] scanning classes (flavoured=" + isFlavouredProject + "): " + classesDir
                );
                try {
                    transformClasses(classesDir, isFlavouredProject, flavourName, project);
                } catch (IOException e) {
                    throw new GradleException("Candlelight plugin failed processing due to I/O error", e);
                }
            });
        });
    }

    private void transformClasses(
            File classesDir,
            boolean isFlavouredProject,
            String flavour,
            Project project
    ) throws IOException {

        if (!classesDir.exists()) return;

        List<String> errors = new ArrayList<>();

        walkClasses(classesDir, file -> {
            byte[] original = readAllBytes(file);

            ClassScanResult scan;
            try {
                scan = scanClass(original, file, isFlavouredProject);
            } catch (GradleException e) {
                errors.add(e.getMessage());
                return;
            }

            // If no getters and no flavour usage, skip entirely
            if (!scan.hasGenerateGetters && scan.flavourMethods.isEmpty()) return;

            String implInternalName = null;

            if (!scan.flavourMethods.isEmpty()) {
                if (!isFlavouredProject) {
                    errors.add(
                            "[Candlelight] @Flavour used in common project.\n" +
                                    "  Class: " + scan.internalName.replace('/', '.') + "\n" +
                                    "  File: " + file.getAbsolutePath()
                    );
                    return;
                }

                implInternalName = computeImplInternalName(scan.internalName, flavour);
                File implFile = new File(classesDir, implInternalName + ".class");

                if (!implFile.exists()) {
                    errors.add(
                            "[Candlelight] Missing flavour implementation.\n" +
                                    "  Common class: " + scan.internalName.replace('/', '.') + "\n" +
                                    "  Expected impl: " + implInternalName.replace('/', '.') + "\n" +
                                    "  Flavour: " + flavour
                    );
                    return;
                }
            }

            byte[] modified = transformClass(original, scan, implInternalName);

            if (!Arrays.equals(original, modified)) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(modified);
                }
            }
        });

        if (!errors.isEmpty()) {
            throw new GradleException(
                    "[Candlelight] Build failed with " + errors.size() + " error(s):\n\n" +
                            String.join("\n\n", errors)
            );
        }
    }

    private ClassScanResult scanClass(
            byte[] classBytes,
            File fileForContext,
            boolean isFlavouredProject
    ) {

        ClassReader cr = new ClassReader(classBytes);
        ClassScanResult result = new ClassScanResult();

        cr.accept(new ClassVisitor(Opcodes.ASM9) {

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                result.internalName = name;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (ANNO_GENERATE_GETTERS.equals(desc)) {
                    result.hasGenerateGetters = true;
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {

                result.existingMethods.add(name);

                if ((access & Opcodes.ACC_STATIC) == 0
                        && !name.equals("<init>")
                        && descriptor.startsWith("()")
                        && Type.getReturnType(descriptor).getSort() != Type.VOID) {
                    result.getterCandidates.add(new MethodData(name, descriptor));
                }

                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean hasFlavour = false;

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        if (ANNO_FLAVOUR.equals(desc)) hasFlavour = true;
                        return super.visitAnnotation(desc, visible);
                    }

                    @Override
                    public void visitEnd() {
                        if (hasFlavour) {
                            if ((access & Opcodes.ACC_STATIC) == 0) {
                                throw new GradleException(
                                        "[Candlelight] @Flavour method must be static:\n" +
                                                "  " + result.internalName.replace('/', '.') +
                                                "#" + name + descriptor
                                );
                            }
                            if (isFlavouredProject) {
                                result.flavourMethods.add(new MethodData(name, descriptor));
                            }
                        }
                        super.visitEnd();
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return result;
    }

    private byte[] transformClass(
            byte[] originalBytes,
            ClassScanResult scan,
            String implInternalName
    ) {

        ClassReader cr = new ClassReader(originalBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        boolean[] modified = {false};

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {

                if (containsMethod(scan.flavourMethods, name, descriptor)) {
                    modified[0] = true;
                    MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
                    mv.visitCode();

                    Type[] args = Type.getArgumentTypes(descriptor);
                    int idx = 0;
                    for (Type t : args) {
                        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), idx);
                        idx += t.getSize();
                    }

                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            implInternalName,
                            name,
                            descriptor,
                            false
                    );

                    mv.visitInsn(getReturnOpcode(descriptor));
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    return null;
                }

                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                if (scan.hasGenerateGetters) {
                    for (MethodData m : scan.getterCandidates) {
                        String getter = "get" + Character.toUpperCase(m.name.charAt(0))
                                + m.name.substring(1);
                        if (scan.existingMethods.contains(getter)) continue;

                        MethodVisitor mv = cv.visitMethod(
                                Opcodes.ACC_PUBLIC,
                                getter,
                                m.descriptor,
                                null,
                                null
                        );
                        mv.visitCode();
                        mv.visitVarInsn(Opcodes.ALOAD, 0);
                        mv.visitMethodInsn(
                                Opcodes.INVOKEVIRTUAL,
                                scan.internalName,
                                m.name,
                                m.descriptor,
                                false
                        );
                        mv.visitInsn(getReturnOpcode(m.descriptor));
                        mv.visitMaxs(0, 0);
                        mv.visitEnd();
                        modified[0] = true;
                    }
                }
                super.visitEnd();
            }
        }, 0);

        return modified[0] ? cw.toByteArray() : originalBytes;
    }

    /* ---- helpers unchanged ---- */

    private static boolean containsMethod(List<MethodData> list, String name, String desc) {
        return list.stream().anyMatch(m -> m.name.equals(name) && m.descriptor.equals(desc));
    }

    private static String computeImplInternalName(String internalName, String flavour) {
        int idx = internalName.lastIndexOf('/');
        String pkg = idx >= 0 ? internalName.substring(0, idx) : "";
        String simple = idx >= 0 ? internalName.substring(idx + 1) : internalName;
        return pkg.isEmpty()
                ? flavour + "/" + simple + "Impl"
                : pkg + "/" + flavour + "/" + simple + "Impl";
    }

    private static int getReturnOpcode(String desc) {
        return switch (Type.getReturnType(desc).getSort()) {
            case Type.VOID -> Opcodes.RETURN;
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.IRETURN;
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            default -> Opcodes.ARETURN;
        };
    }

    private static void walkClasses(File dir, ClassFileConsumer c) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) walkClasses(f, c);
            else if (f.getName().endsWith(".class")) c.accept(f);
        }
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return in.readAllBytes();
        }
    }

    /* ---- data ---- */

    private static final class ClassScanResult {
        String internalName;
        boolean hasGenerateGetters;
        List<MethodData> getterCandidates = new ArrayList<>();
        List<MethodData> flavourMethods = new ArrayList<>();
        Set<String> existingMethods = new HashSet<>();
    }

    private record MethodData(String name, String descriptor) {}

    private interface ClassFileConsumer {
        void accept(File f) throws IOException;
    }
}
