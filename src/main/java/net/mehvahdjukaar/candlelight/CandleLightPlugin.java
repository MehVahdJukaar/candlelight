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
        String flavour = (String) project.findProperty(PROPERTY_FLAVOUR);
        boolean isFlavouredProject = flavour != null && !flavour.isBlank();

        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.doLast(t -> {
                File classesDir = task.getDestinationDirectory().get().getAsFile();

                project.getLogger().lifecycle(
                        "\n[Candlelight] ============================================\n" +
                                "[Candlelight] Starting class transformation\n" +
                                "[Candlelight]  Flavoured project : " + isFlavouredProject + "\n" +
                                "[Candlelight]  Flavour name      : " + flavour + "\n" +
                                "[Candlelight]  Classes dir       : " + classesDir + "\n" +
                                "[Candlelight] ============================================\n"
                );

                try {
                    transformClasses(classesDir, isFlavouredProject, flavour, project);
                } catch (IOException e) {
                    throw new GradleException("Candlelight I/O failure", e);
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

        long startNanos = System.nanoTime();

        int totalClasses = 0;
        int scannedClasses = 0;
        int flavourClasses = 0;
        int getterClasses = 0;
        int modifiedClasses = 0;
        int flavourMethodsTotal = 0;

        List<String> errors = new ArrayList<>();

        if (!classesDir.exists()) {
            project.getLogger().lifecycle("[Candlelight] Classes directory does not exist");
            return;
        }

        walkClasses(classesDir, file -> {

            project.getLogger().lifecycle(
                    "[Candlelight] Scanning file: " + file.getAbsolutePath()
            );

            byte[] original = readAllBytes(file);
            ClassScanResult scan;

            try {
                scan = scanClass(original, isFlavouredProject, project);
            } catch (GradleException e) {
                errors.add(e.getMessage());
                return;
            }

            project.getLogger().lifecycle(
                    "[Candlelight] Scan result for " + scan.internalName.replace('/', '.') + "\n" +
                            "  @GenerateGetters : " + scan.hasGenerateGetters + "\n" +
                            "  @Flavour methods : " + scan.flavourMethods.size()
            );

            if (!scan.hasGenerateGetters && scan.flavourMethods.isEmpty()) {
                project.getLogger().lifecycle(
                        "[Candlelight] No transformations needed for this class"
                );
                return;
            }

            String implInternalName = null;

            if (!scan.flavourMethods.isEmpty()) {

                if (!isFlavouredProject) {
                    errors.add(
                            "[Candlelight] @Flavour used in non-flavoured project:\n" +
                                    "  " + scan.internalName.replace('/', '.')
                    );
                    return;
                }

                implInternalName = computeImplInternalName(scan.internalName, flavour);
                File implFile = new File(classesDir, implInternalName + ".class");

                project.getLogger().lifecycle(
                        "[Candlelight] FLAVOUR processing\n" +
                                "  Common class : " + scan.internalName.replace('/', '.') + "\n" +
                                "  Impl class   : " + implInternalName.replace('/', '.') + "\n" +
                                "  Impl path    : " + implFile.getAbsolutePath() + "\n" +
                                "  Exists       : " + implFile.exists()
                );

                if (!implFile.exists()) {
                    errors.add(
                            "[Candlelight] Missing flavour implementation:\n" +
                                    "  Common class : " + scan.internalName.replace('/', '.') + "\n" +
                                    "  Expected impl: " + implInternalName.replace('/', '.')
                    );
                    return;
                }
            }

            byte[] modified = transformClass(original, scan, implInternalName, project);

            if (!Arrays.equals(original, modified)) {
                project.getLogger().lifecycle(
                        "[Candlelight] MODIFIED class written: " +
                                scan.internalName.replace('/', '.')
                );
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(modified);
                }
            }
        });

        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        project.getLogger().lifecycle(
                "\n[Candlelight] ============================================\n" +
                        "[Candlelight] Scan summary\n" +
                        "[Candlelight]  Total class files     : " + totalClasses + "\n" +
                        "[Candlelight]  Classes scanned       : " + scannedClasses + "\n" +
                        "[Candlelight]  Classes w/ @Flavour   : " + flavourClasses + "\n" +
                        "[Candlelight]  Flavour methods total : " + flavourMethodsTotal + "\n" +
                        "[Candlelight]  Classes w/ getters    : " + getterClasses + "\n" +
                        "[Candlelight]  Classes modified      : " + modifiedClasses + "\n" +
                        "[Candlelight]  Time taken            : " + elapsedMs + " ms\n" +
                        "[Candlelight] ============================================\n"
        );

        if (!errors.isEmpty()) {
            throw new GradleException(
                    "[Candlelight] Build failed with errors:\n\n" +
                            String.join("\n\n", errors)
            );
        }
    }

    private ClassScanResult scanClass(
            byte[] classBytes,
            boolean isFlavouredProject,
            Project project
    ) {
        ClassScanResult result = new ClassScanResult();
        ClassReader cr = new ClassReader(classBytes);

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
                    project.getLogger().lifecycle(
                            "[Candlelight] Found @GenerateGetters on " +
                                    result.internalName.replace('/', '.')
                    );
                }
                return super.visitAnnotation(desc, visible);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] ex) {

                result.existingMethods.add(name);

                if ((access & Opcodes.ACC_STATIC) == 0 &&
                        !name.equals("<init>") &&
                        desc.startsWith("()") &&
                        Type.getReturnType(desc).getSort() != Type.VOID) {
                    result.getterCandidates.add(new MethodData(name, desc));
                }

                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);

                return new MethodVisitor(Opcodes.ASM9, mv) {
                    boolean hasFlavour;

                    @Override
                    public AnnotationVisitor visitAnnotation(String aDesc, boolean vis) {
                        if (ANNO_FLAVOUR.equals(aDesc)) {
                            hasFlavour = true;
                            project.getLogger().lifecycle(
                                    "[Candlelight] Found @Flavour method: " +
                                            result.internalName.replace('/', '.') +
                                            "#" + name + desc
                            );
                        }
                        return super.visitAnnotation(aDesc, vis);
                    }

                    @Override
                    public void visitEnd() {
                        if (hasFlavour) {
                            if ((access & Opcodes.ACC_STATIC) == 0) {
                                throw new GradleException(
                                        "[Candlelight] @Flavour method must be static:\n" +
                                                "  " + result.internalName.replace('/', '.') +
                                                "#" + name + desc
                                );
                            }

                            // ALWAYS record it
                            result.flavourMethods.add(new MethodData(name, desc));

                            project.getLogger().lifecycle(
                                    "[Candlelight] Registered @Flavour method: " +
                                            result.internalName.replace('/', '.') +
                                            "#" + name + desc
                            );
                        }
                        super.visitEnd();
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return result;
    }

    private byte[] transformClass(
            byte[] original,
            ClassScanResult scan,
            String implInternalName,
            Project project
    ) {

        ClassReader cr = new ClassReader(original);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        boolean[] modified = {false};

        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String sig, String[] ex) {

                if (containsMethod(scan.flavourMethods, name, desc)) {
                    modified[0] = true;

                    project.getLogger().lifecycle(
                            "[Candlelight] Rewriting @Flavour method:\n" +
                                    "  class  : " + scan.internalName.replace('/', '.') + "\n" +
                                    "  method : " + name + desc + "\n" +
                                    "  target : " + implInternalName.replace('/', '.')
                    );

                    MethodVisitor mv = cv.visitMethod(access, name, desc, sig, ex);
                    mv.visitCode();

                    Type[] args = Type.getArgumentTypes(desc);
                    int idx = 0;
                    for (Type t : args) {
                        mv.visitVarInsn(t.getOpcode(Opcodes.ILOAD), idx);
                        idx += t.getSize();
                    }

                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            implInternalName,
                            name,
                            desc,
                            false
                    );

                    mv.visitInsn(getReturnOpcode(desc));
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                    return null;
                }

                return super.visitMethod(access, name, desc, sig, ex);
            }

            @Override
            public void visitEnd() {
                if (scan.hasGenerateGetters) {
                    project.getLogger().lifecycle(
                            "[Candlelight] Generating getters for " +
                                    scan.internalName.replace('/', '.')
                    );

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

                        project.getLogger().lifecycle(
                                "[Candlelight]  + generated getter: " + getter
                        );

                        modified[0] = true;
                    }
                }
                super.visitEnd();
            }
        }, 0);

        return modified[0] ? cw.toByteArray() : original;
    }

    /* ---- helpers ---- */

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
