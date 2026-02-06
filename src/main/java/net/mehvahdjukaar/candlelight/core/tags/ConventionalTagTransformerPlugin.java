package net.mehvahdjukaar.candlelight.core.tags;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.jvm.tasks.Jar;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConventionalTagTransformerPlugin {

    private static final Pattern FABRIC_CONVENTIONAL_TAG =
            Pattern.compile(
                    "net/fabricmc/fabric/api/tag/convention/v2/Conventional(.*)Tags"
            );

    private static final String NEOFORGE_TAGS_BASE =
            "net/neoforged/neoforge/common/Tags$";

    public static void apply(Project rootProject) {
        rootProject.getAllprojects().forEach(project -> {

            Loader loader = Loader.infer(project.getName());
            if (loader != Loader.NEOFORGE) {
                return;
            }

            project.getTasks().withType(Jar.class).configureEach(jar -> {
                jar.doFirst(task -> {
                    Path classesDir = project.getBuildDir()
                            .toPath()
                            .resolve("classes/java/main");

                    if (!Files.exists(classesDir)) {
                        return;
                    }

                    project.getLogger().lifecycle(
                            "[ConventionalTagTransform] Rewriting Fabric conventional tags → NeoForge in {}",
                            project.getName()
                    );

                    try {
                        transform(classesDir);
                    } catch (Exception e) {
                        throw new GradleException(
                                "Failed transforming Fabric conventional tags for project " +
                                        project.getName(),
                                e
                        );
                    }
                });
            });
        });
    }

    private static void transform(Path classesDir) throws IOException {
        Files.walk(classesDir)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(ConventionalTagTransformerPlugin::transformClass);
    }

    private static void transformClass(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);

            ClassReader reader = new ClassReader(bytes);
            ClassWriter writer = new ClassWriter(reader, 0);

            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    MethodVisitor mv = super.visitMethod(
                            access, name, descriptor, signature, exceptions
                    );

                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitFieldInsn(
                                int opcode,
                                String owner,
                                String name,
                                String desc
                        ) {
                            String newOwner = mapOwner(owner);

                            if (!owner.equals(newOwner)) {
                                super.visitFieldInsn(opcode, newOwner, name, desc);
                            } else {
                                super.visitFieldInsn(opcode, owner, name, desc);
                            }
                        }
                    };
                }
            };

            reader.accept(visitor, 0);
            Files.write(classFile, writer.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Failed transforming " + classFile, e);
        }
    }

    private static String mapOwner(String owner) {
        Matcher matcher = FABRIC_CONVENTIONAL_TAG.matcher(owner);
        if (!matcher.matches()) {
            return owner;
        }

        // Example capture: "Item", "Block", "Fluid", "Biome", "EntityType"
        String type = matcher.group(1);

        return NEOFORGE_TAGS_BASE + type + "s";
    }

    private enum Loader {
        FABRIC,
        NEOFORGE,
        FORGE,
        UNKNOWN;

        static Loader infer(String projectName) {
            String n = projectName.toLowerCase();
            if (n.contains("neoforge")) return NEOFORGE;
            if (n.contains("fabric")) return FABRIC;
            if (n.contains("forge")) return FORGE;
            return UNKNOWN;
        }
    }
}
