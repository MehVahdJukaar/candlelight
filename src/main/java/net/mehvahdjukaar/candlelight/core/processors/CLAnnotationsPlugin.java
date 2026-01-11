package net.mehvahdjukaar.candlelight.core.processors;

import net.mehvahdjukaar.candlelight.core.ClassUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@ApiStatus.Internal
public class CLAnnotationsPlugin {

    public static void apply(Project project) {
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.doLast(t -> {
                File classesDir = task.getDestinationDirectory().get().getAsFile();

                project.getLogger().lifecycle("[Candlelight] Scanning classes in: " + classesDir);

                try {
                    if (!classesDir.exists()) {
                        project.getLogger().lifecycle("[Candlelight] Classes directory does not exist: " + classesDir);
                        return;
                    }
                    transformAll(classesDir, project);
                } catch (IOException e) {
                    throw new GradleException("Candlelight I/O failure", e);
                }
            });
        });
    }

    public static void transformAll(File classesDir, Project project) throws IOException {

        List<ClassProcessor> ap = List.of(
                new BeanGettersProcessor(project),
                new FlavourProcessor(project));

        ClassUtils.walkClasses(classesDir, file -> {
            //project.getLogger().lifecycle("[Candlelight] Scanning file: " + file.getAbsolutePath());

            byte[] original = ClassUtils.readAllBytes(file);
            byte[] modified = original;

            for (ClassProcessor processor : ap) {
                modified = processor.transform(modified);
            }

            if (original != modified) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(modified);
                }
                project.getLogger().lifecycle("[Candlelight] Class written after modifications: " + file.getAbsolutePath());
            }
        });
    }
}