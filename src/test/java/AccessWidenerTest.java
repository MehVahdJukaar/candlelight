import net.mehvahdjukaar.candlelight.core.access.AccessWidener;
import net.mehvahdjukaar.candlelight.core.access.AccessWidenerParser;
import net.mehvahdjukaar.candlelight.core.access.AccessWidenerUtils;
import net.mehvahdjukaar.candlelight.core.access.Remapper;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AccessWidenerTest {

    @Test
    public void parsesCorrectly() {
        File projectDir = new File("src/test/resources");
        Project project = TestProjectUtil.createProject(projectDir);

        File awFile = new File(project.getProjectDir(), "aw/simple.accesswidener");
        AccessWidener aw = AccessWidenerParser.parseAccessWidener(awFile);

        assertNotNull(aw, "Parsed AccessWidener should not be null");
    }

    @Test
    public void transformsCorrectly() {
        File projectDir = new File("src/test/resources");
        Project project = TestProjectUtil.createProject(projectDir);

        File awFile = new File(project.getProjectDir(), "aw/simple.accesswidener");
        AccessWidener aw = AccessWidenerParser.parseAccessWidener(awFile);

        String transformed = AccessWidenerUtils.toAccessTransformer(aw, Remapper.empty(null));

        assertNotNull(transformed, "Transformed output should not be null");
        System.out.println(transformed); // optional: inspect output
    }
}
