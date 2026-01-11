package net.mehvahdjukaar.candlelight.core.access;

import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.io.IOException;

public final class AccessWidenerGradle {

    private static final String TRANSFORM_TASK = "transformAccessWidener";

    private static Provider<File> generatedAccessTransformer(Project project) {
        return project.getLayout()
                .getBuildDirectory()
                .file("generated/accesstransformer.cfg")
                .map(RegularFile::getAsFile)
                .map(AccessWidenerGradle::ensureParentDirsCreated);
    }

    private static File ensureParentDirsCreated(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IllegalStateException("Failed to create directories for " + file);
            }
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }

    public static Provider<File> generateAccessTransformer(
            Project project,
            Provider<File> from
    ) {
        Provider<File> output = generatedAccessTransformer(project);

        var transformAccessWidener = project.getTasks().register(
                TRANSFORM_TASK,
                task -> {
                    Remapper remapper = Remapper.detectMappings(project);
                    if (remapper.getTask() != null) {
                        task.dependsOn(remapper.getTask());
                    }

                    task.getOutputs().file(output);
                    task.getInputs().file(from);

                    task.doLast(t -> {
                        AccessWidener accessWidener =
                                AccessWidenerParser.parseAccessWidener(from.get());
                        String transformed =
                                AccessWidenerUtils.toAccessTransformer(
                                        accessWidener, remapper
                                );
                        try {
                            FileUtils.writeStringToFile(
                                    output.get(), transformed, "UTF-8"
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
        );

        project.getTasks()
                .withType(ProcessResources.class)
                .configureEach(task -> {
                    task.dependsOn(transformAccessWidener);
                    task.from(transformAccessWidener, spec -> {
                        spec.rename(name -> "accesstransformer.cfg");
                        spec.into("META-INF");
                    });
                });

        return output;
    }
}
