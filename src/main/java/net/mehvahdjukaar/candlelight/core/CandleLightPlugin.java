package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.jars_processors.ClientOnlyTransformPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
        configureNeoForgeModuleMetadata(project);

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

        if (project == project.getRootProject()) {
            registerAggregatorTasks(project);
        } else {
            // Ensure aggregator tasks are registered on the root project even when
            // the plugin is applied only via `subprojects { apply plugin: ... }`.
            // Re-applying the plugin is a no-op thanks to Gradle's de-duplication.
            project.getRootProject().getPlugins().apply(CandleLightPlugin.class);
        }
    }

    private void registerAggregatorTasks(Project root) {
        TaskProvider<Task> cleanAll = root.getTasks().register("cleanAll", t -> {
            t.setGroup("build");
            t.setDescription("Cleans all subprojects atomically.");
            t.dependsOn(subprojectTaskPaths(root, "clean"));
        });

        TaskProvider<Task> buildAll = root.getTasks().register("buildAll", t -> {
            t.setGroup("build");
            t.setDescription("Builds all subprojects. Runs only after cleanAll succeeds when both are scheduled.");
            t.dependsOn(subprojectTaskPaths(root, "build"));
            t.mustRunAfter(cleanAll);
        });

        // Depend on the root-level `:curseforge` and `:modrinth` aggregators created by the
        // helper plugin via addUploadTask(...). We deliberately do NOT depend on `:upload`,
        // because helper's `:upload` also depends on `:publish` — pulling that into our chain
        // creates a cycle once `publish.mustRunAfter(gitTag)` is applied.
        TaskProvider<Task> uploadAll = root.getTasks().register("uploadAll", t -> {
            t.setGroup("publishing");
            t.setDescription("Uploads all subprojects to CurseForge and Modrinth (excludes maven publish).");
            t.dependsOn((Callable<List<Object>>) () -> {
                List<Object> deps = new ArrayList<>();
                Task cf = root.getTasks().findByName("curseforge");
                if (cf != null) deps.add(cf);
                Task mr = root.getTasks().findByName("modrinth");
                if (mr != null) deps.add(mr);
                return deps;
            });
            t.mustRunAfter(buildAll);
        });

        TaskProvider<GitTagTask> gitTag = root.getTasks().register("gitTag", GitTagTask.class, t -> {
            t.setGroup("publishing");
            t.setDescription("Creates and pushes a git tag matching the root project's mod_version.");
            t.getTag().convention(root.provider(() -> {
                Object v = root.findProperty("mod_version");
                if (v == null) {
                    throw new IllegalStateException(
                            "gitTag: root project property 'mod_version' is not set. " +
                                    "Override via gitTag { tag.set(\"...\") } if you use a different property.");
                }
                return v.toString();
            }));
            t.mustRunAfter(uploadAll);
        });

        // Ensure root's `publish` (if present) is ordered after gitTag.
        root.getTasks().configureEach(task -> {
            if (task.getName().equals("publish")) {
                task.mustRunAfter(gitTag);
            }
        });

        root.getTasks().register("buildAndPublishAll", t -> {
            t.setGroup("build");
            t.setDescription("Full release pipeline: cleanAll -> buildAll -> uploadAll -> gitTag -> publish. Each step runs only if the previous succeeded.");
            t.dependsOn(cleanAll, buildAll, uploadAll, gitTag, "publish");
        });
    }

    private static Callable<List<String>> subprojectTaskPaths(Project root, String taskName) {
        return () -> {
            List<String> paths = new ArrayList<>();
            for (Project sp : root.getSubprojects()) {
                paths.add(sp.getPath() + ":" + taskName);
            }
            return paths;
        };
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

    private void configureNeoForgeModuleMetadata(Project project) {
        if (!"neoforge".equals(project.getName())) {
            return;
        }

        project.getPlugins().withId("maven-publish", plugin -> project.afterEvaluate(p -> {
            TaskProvider<EnsureAccessTransformerModuleMetadataTask> ensureTask = p.getTasks().register(
                    "candleEnsureAccessTransformerModuleMetadata",
                    EnsureAccessTransformerModuleMetadataTask.class,
                    task -> task.getAccessTransformerSources().from(
                            p.getLayout().getBuildDirectory().dir("copyAccessTransformersPublications"),
                            p.getLayout().getBuildDirectory().file("generated/accesstransformer.cfg")
                    )
            );

            p.getTasks().withType(GenerateModuleMetadata.class).configureEach(generateTask ->
                    ensureTask.configure(task -> task.getModuleFile().set(generateTask.getOutputFile()))
            );

            Task modifyTask = p.getTasks().findByName("modifyMetadataFile");
            if (modifyTask != null) {
                modifyTask.finalizedBy(ensureTask);
            } else {
                p.getTasks().withType(GenerateModuleMetadata.class).configureEach(generateTask ->
                        generateTask.finalizedBy(ensureTask)
                );
            }
        }));
    }

}