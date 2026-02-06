package net.mehvahdjukaar.candlelight.core.env;

import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;
import org.objectweb.asm.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ClientOnlyTransformPlugin {

    private static final String CLIENT_ONLY = "Lnet/mehvahdjukaar/candlelight/api/ClientOnly;";

    private enum LoaderType {
        FABRIC("net.fabricmc.api.EnvType",
                "net.fabricmc.api.Environment"),
        FORGE("net.minecraftforge.api.distmarker.Dist",
                "net.minecraftforge.api.distmarker.OnlyIn"),
        NEOFORGE("net.neoforged.api.distmarker.Dist",
                "net.neoforged.api.distmarker.OnlyIn");

        final String valueDesc; // type of parameter for annotation
        final String annotationDesc; // annotation class descriptor

        LoaderType(String valueClass, String annotationClass) {
            this.valueDesc = "L" + valueClass.replace('.', '/') + ";";
            this.annotationDesc = "L" + annotationClass.replace('.', '/') + ";";
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
        // Apply to all subprojects
        rootProject.getRootProject().getAllprojects().forEach(project -> {
            project.getTasks().withType(Jar.class).configureEach(jar -> {
                // Only transform the main JAR
                if (!jar.getName().equals("jar") && !jar.getName().equals("remapJar")) return;
                System.out.println("[ClientOnlyTransformPlugin] Setting up transformation for project " + project.getName());

                jar.doFirst(task -> {
                    LoaderType loader = LoaderType.infer(project.getName());
                    if (loader == null) {
                        System.out.println("[ClientOnlyTransformPlugin] Unknown loader for project " + project.getName() + ", skipping");
                        return;
                    }

                    File classesDir = new File(project.getBuildDir(), "classes/java/main");
                    if (!classesDir.exists()) {
                        System.out.println("[ClientOnlyTransformPlugin] No classes dir for project " + project.getName());
                        return;
                    }

                    System.out.println("[ClientOnlyTransformPlugin] Transforming classes for loader: " + loader.name());
                    try {
                        transformClasses(classesDir.toPath(), loader);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to transform ClientOnly annotations", e);
                    }
                });
            });
        });
    }

    private static void transformClasses(Path dir, LoaderType loader) throws Exception {
        Files.walk(dir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(classFile -> {
                    try {
                        byte[] original = Files.readAllBytes(classFile);
                        ClassReader reader = new ClassReader(original);
                        ClassWriter writer = new ClassWriter(reader, 0);

                        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {

                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                if (CLIENT_ONLY.equals(desc)) {
                                    System.out.println("[ClientOnlyTransformPlugin] Replacing @ClientOnly with " + loader.annotationDesc + " on class");

                                    AnnotationVisitor av = super.visitAnnotation(loader.annotationDesc, visible);
                                    if (loader == LoaderType.FABRIC) {
                                        // Fabric: @Environment(EnvType.CLIENT)
                                        av.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
                                    } else {
                                        // Forge/NeoForge: @OnlyIn(Dist.CLIENT)
                                        av.visitEnum("value", "Lnet/minecraftforge/api/distmarker/Dist;", "CLIENT");
                                    }
                                    return av;
                                }
                                return super.visitAnnotation(desc, visible);
                            }


                            @Override
                            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                                return new MethodVisitor(Opcodes.ASM9, mv) {
                                    @Override
                                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                                        if (CLIENT_ONLY.equals(desc)) {
                                            System.out.println("[ClientOnlyTransformPlugin] Replacing @ClientOnly with " + loader.annotationDesc + " on class");

                                            AnnotationVisitor av = super.visitAnnotation(loader.annotationDesc, visible);
                                            if (loader == LoaderType.FABRIC) {
                                                // Fabric: @Environment(EnvType.CLIENT)
                                                av.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
                                            } else {
                                                // Forge/NeoForge: @OnlyIn(Dist.CLIENT)
                                                av.visitEnum("value", "Lnet/minecraftforge/api/distmarker/Dist;", "CLIENT");
                                            }
                                            return av;
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
                                        if (CLIENT_ONLY.equals(desc)) {
                                            System.out.println("[ClientOnlyTransformPlugin] Replacing @ClientOnly with " + loader.annotationDesc + " on class");

                                            AnnotationVisitor av = super.visitAnnotation(loader.annotationDesc, visible);
                                            if (loader == LoaderType.FABRIC) {
                                                // Fabric: @Environment(EnvType.CLIENT)
                                                av.visitEnum("value", "Lnet/fabricmc/api/EnvType;", "CLIENT");
                                            } else {
                                                // Forge/NeoForge: @OnlyIn(Dist.CLIENT)
                                                av.visitEnum("value", "Lnet/minecraftforge/api/distmarker/Dist;", "CLIENT");
                                            }
                                            return av;
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
                        throw new RuntimeException("Failed to transform class: " + classFile, e);
                    }
                });
    }
}
