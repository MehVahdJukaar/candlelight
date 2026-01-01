package net.mehvahdjukaar.candlelight;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public class CandleLightPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.doLast(t -> {
                File classesDir = task.getDestinationDirectory().get().getAsFile();

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

    public void transformAll(File classesDir, Project project) throws IOException {

        List<ClassAnnotationProcessor> ap = List.of(
                new GenerateGetterProcessor(project),
                new FlavourProcessor(project));

        CandleLightClassWalker.walkClasses(classesDir, file -> {
            byte[] original = CandleLightClassWalker.readAllBytes(file);
            byte[] modified = original;


            for (ClassAnnotationProcessor processor : ap) {
                modified = processor.transform(original);
            }

            if (!Arrays.equals(original, modified)) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(modified);
                }
            }
        });
    }
}
