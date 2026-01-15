package net.mehvahdjukaar.candlelight.core.access;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class AccessWidenerTransformationPlugin {

    private static final String TRANSFORM_TASK = "transformAccessWidener";

    public static void apply(Project project, CandleLightExtension extension) {


        // Defer task registration until after project evaluation (so extension values are set)
        project.afterEvaluate(p -> {
            Provider<File> inputFile = extension.getAccessWideners().map(RegularFile::getAsFile);
            registerTransformTask(project, inputFile);
        });
    }

    private static Provider<File> generatedAccessTransformer(Project project) {
        return project.getLayout()
                .getBuildDirectory()
                .file("generated/accesstransformer.cfg")
                .map(RegularFile::getAsFile);
    }

    private static File ensureParentDirsCreated(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directories for " + file);
        }
        return file;
    }

    private static TaskProvider<DefaultTask> registerTransformTask(Project project, Provider<File> from) {
        Provider<File> outputFile = generatedAccessTransformer(project);

        TaskProvider<DefaultTask> transformAWTask = project.getTasks().register(
                TRANSFORM_TASK,
                DefaultTask.class,
                task -> {
                    task.getInputs().file(from);
                    task.getOutputs().file(outputFile);

                    task.doLast(t -> {
                        File out = ensureParentDirsCreated(outputFile.get());
                        AccessWidener aw = AccessWidenerParser.parseAccessWidener(from.get());
                        String transformed = AccessWidenerConverter.toAccessTransformer(aw);
                        try {
                            Files.writeString(out.toPath(), transformed, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
        );

        // Automatically make any task that consumes this file depend on the transform
        project.getTasks()
                .matching(t ->
                        t.getName().contains("createMinecraftArtifacts"))
                .configureEach(t -> t.dependsOn(transformAWTask));
        // Also wire cross-project dependency if :neoforge exists
        Project neoforgeProject = project.findProject(":neoforge");
        if (neoforgeProject != null) {
            neoforgeProject.getTasks()
                    .matching(t -> t.getName().equals("createMinecraftArtifacts"))
                    .configureEach(t -> t.dependsOn(transformAWTask));
        }

        // After the transform task is registered
        project.getTasks()
                .withType(ProcessResources.class)
                .configureEach(task -> {
                    task.dependsOn(transformAWTask);
                    task.from(transformAWTask, spec -> {
                        spec.rename(name -> "accesstransformer.cfg"); // force correct name
                        spec.into("META-INF");                        // copy into META-INF
                    });
                });

        return transformAWTask;
    }
}