package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.jars_processors.ClientOnlyTransformPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nullable;

public class CandleLightPlugin implements Plugin<Project> {

    private static final String PREFIX = "[CANDLELIGHT] ";

    private static final String TASK_NAME = "candleLightTransform";

    public static void log(Project project, String s) {
        project.getLogger().lifecycle(PREFIX + s);
    }

    @Override
    public void apply(Project project) {

        CandleLightExtension clExtension = project.getExtensions()
                .create("candlelight", CandleLightExtension.class);

        clExtension.getLogging().convention(true);
        clExtension.getClientOnly().convention(true);


        ClientOnlyTransformPlugin.apply(project, clExtension);

        project.getPlugins().withId("java", plugin -> {
            transformCompileTask(
                    project,
                    (JavaCompile) project.getTasks().getByName("compileJava"),
                    null
            );

            transformCompileTask(
                    project,
                    (JavaCompile) project.getTasks().getByName("compileTestJava"),
                    "test"
            );

            project.getTasks().configureEach(task -> {
                String taskName = task.getName();
                if (taskName.contains("remapSourcesJar") || taskName.contains("remapJar")) {
                    task.dependsOn(TASK_NAME);
                    project.getRootProject().getChildProjects().values().forEach(p -> {
                        if (p.getName().equals("common")) {
                            task.dependsOn(TASK_NAME);
                        }
                    });
                }

                if (taskName.equals("curseforge")) {
                    task.dependsOn("jar");
                }
            });
        });
    }

    private void transformCompileTask(Project project, JavaCompile compileTask, @Nullable String key) {
        var clExtension = project.getExtensions().getByType(CandleLightExtension.class);

        // =====================================
        // 1. MOVE compile output FIRST (safe)
        // =====================================
        Provider<Directory> rawDir =
                project.getLayout().getBuildDirectory().dir("raw/" + compileTask.getName() + "/classes");

        var outputFolder = key == null ? "main" : key;
        Provider<Directory> finalDir =
                project.getLayout().getBuildDirectory().dir("classes/java/" + outputFolder);

        compileTask.getDestinationDirectory().set(rawDir);

        // =====================================
        // 2. TRANSFORM TASK (no cycle)
        // =====================================
        var suffix = key == null ? "" : capitalize(key);
        var transformTaskName = TASK_NAME + suffix;
        var transformTask = project.getTasks().register(
                transformTaskName,
                TransformClassesTask.class,
                t -> {

                    // read compiled raw output
                    t.getSourceDir().set(rawDir);

                    // write FINAL runtime output
                    t.getOutputDir().set(finalDir);

                    t.getExtensionProperty().set(clExtension);

                    t.dependsOn(compileTask);

                    t.onlyIf($ -> compileTask.getEnabled());
                }
        );

        // =====================================
        // 3. ensure ordering
        // =====================================
        var classesTask = key == null ? "classes" : (key + "Classes");
        project.getTasks().named(classesTask, task -> {
            task.dependsOn(transformTask);
        });

        /*
         * This only works for one specific project setup:
         * - root
         *   - :common
         *   - :fabric (depends on :common)
         *   - :neoforge (depends on :common)
         */
        if (project.getName().equals("fabric")) {

            project.getTasks().named(compileTask.getName(), t -> {
                t.dependsOn(
                        project.project(":common")
                                .getTasks()
                                .named(transformTaskName)
                );
            });
        }

        if (project.getName().equals("neoforge")) {

            project.getTasks().named(compileTask.getName(), t -> {
                t.dependsOn(
                        project.project(":common")
                                .getTasks()
                                .named(transformTaskName)
                );
            });
        }
    }

    private static String capitalize(String input) {
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

}