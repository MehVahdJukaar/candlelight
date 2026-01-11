import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;

import java.io.File;

public class TestProjectUtil {

    /**
     * Create a dummy Gradle Project pointing at the given folder.
     * Used for testing purposes only.
     */
    public static Project createProject(File projectDir) {
        return ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build();
    }
}