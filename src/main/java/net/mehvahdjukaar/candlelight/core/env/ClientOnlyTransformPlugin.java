package net.mehvahdjukaar.candlelight.core.env;

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.TaskProvider;
import org.objectweb.asm.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ClientOnlyTransformPlugin {

    private static final String NEUTRAL_ANNOTATION = "Lnet/mehvahdjukaar/candlelight/api/ClientOnly;";

    private enum LoaderType {
        FABRIC("net.fabricmc.api.Environment"),
        FORGE("net.minecraftforge.api.distmarker.OnlyIn"),
        NEOFORGE("net.neoforged.api.distmarker.OnlyIn");

        final String desc;

        LoaderType(String className) {
            this.desc = "L" + className.replace('.', '/') + ";";
        }

        static LoaderType infer(String projectName) {
            String name = projectName.toLowerCase();
            if (name.contains("fabric")) return FABRIC;
            if (name.contains("neoforge")) return NEOFORGE;
            if (name.contains("forge")) return FORGE;
            return null;
        }
    }

    public static void apply(Project rootProject) {
        System.out.println("[ClientOnlyTransformPlugin] Applying to root project: " + rootProject.getName());

        rootProject.getAllprojects().forEach(project -> {
            System.out.println("[ClientOnlyTransformPlugin] Configuring project: " + project.getName());

            project.getTasks().withType(Jar.class).configureEach(jar -> {

                // Ensure classes task runs first
                jar.dependsOn(project.getTasks().findByName("classes"));

                jar.doFirst(task -> {
                    System.out.println("[ClientOnlyTransformPlugin] Running doFirst for JAR: " + jar.getName());

                    LoaderType loader = LoaderType.infer(project.getName());
                    if (loader == null) {
                        System.out.println("[ClientOnlyTransformPlugin] No loader detected for project: " + project.getName());
                        return;
                    }

                    System.out.println("[ClientOnlyTransformPlugin] Detected loader: " + loader);

                    File classesDir = new File(project.getBuildDir(), "classes/java/main");
                    if (!classesDir.exists()) {
                        System.out.println("[ClientOnlyTransformPlugin] Classes directory does not exist: " + classesDir.getAbsolutePath());
                        return;
                    }
                    System.out.println("[ClientOnlyTransformPlugin] Classes directory: " + classesDir.getAbsolutePath());

                    try {
                        transformClasses(classesDir.toPath(), loader);
                        System.out.println("[ClientOnlyTransformPlugin] Transformation complete for project: " + project.getName());
                    } catch (Exception e) {
                        System.err.println("[ClientOnlyTransformPlugin] Failed to transform classes for project: " + project.getName());
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                });
            });
        });
    }

    private static void transformClasses(Path dir, LoaderType loader) throws Exception {
        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(classFile -> {
                    System.out.println("[ClientOnlyTransformPlugin] Transforming class file: " + classFile);

                    try {
                        byte[] original = Files.readAllBytes(classFile);
                        ClassReader reader = new ClassReader(original);
                        ClassWriter writer = new ClassWriter(reader, 0);

                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

                            @Override
                            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                if (NEUTRAL_ANNOTATION.equals(descriptor)) {
                                    System.out.println("[ClientOnlyTransformPlugin] Replacing class annotation: " + descriptor + " -> " + loader.desc);
                                    return super.visitAnnotation(loader.desc, visible);
                                }
                                return super.visitAnnotation(descriptor, visible);
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        if (NEUTRAL_ANNOTATION.equals(desc)) {
                                            System.out.println("[ClientOnlyTransformPlugin] Replacing method annotation in " + name + ": " + desc + " -> " + loader.desc);
                                            return super.visitAnnotation(loader.desc, visible);
                                        }
                                        return super.visitAnnotation(desc, visible);
                                    }
                                };
                            }

                            @Override
                            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                                FieldVisitor fv = super.visitField(access, name, descriptor, signature, value);
                                return new FieldVisitor(Opcodes.ASM9, fv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        if (NEUTRAL_ANNOTATION.equals(desc)) {
                                            System.out.println("[ClientOnlyTransformPlugin] Replacing field annotation in " + name + ": " + desc + " -> " + loader.desc);
                                            return super.visitAnnotation(loader.desc, visible);
                                        }
                                        return super.visitAnnotation(desc, visible);
                                    }
                                };
                            }
                        };

                        reader.accept(visitor, 0);
                        byte[] transformed = writer.toByteArray();
                        Files.write(classFile, transformed, StandardOpenOption.TRUNCATE_EXISTING);

                    } catch (Exception e) {
                        System.err.println("[ClientOnlyTransformPlugin] Error transforming class: " + classFile);
                        throw new RuntimeException(e);
                    }
                });
    }
}
