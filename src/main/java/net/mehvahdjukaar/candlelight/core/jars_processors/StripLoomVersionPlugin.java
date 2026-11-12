package net.mehvahdjukaar.candlelight.core.jars_processors;

import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import net.mehvahdjukaar.candlelight.core.CandleLightPlugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

//hack
public class StripLoomVersionPlugin {

    private static final Attributes.Name LOOM_VERSION = new Attributes.Name("Fabric-Loom-Version");

    public static void apply(Project project, CandleLightExtension ext) {
        project.getTasks().withType(Jar.class).configureEach(jar -> {
            if (!jar.getName().equals("remapJar")) return;

            jar.doLast(task -> {
                if (!ext.getStripLoomVersion().get()) return;

                File archive = jar.getArchiveFile().get().getAsFile();
                if (!archive.exists()) return;

                try {
                    if (stripLoomVersion(archive.toPath())) {
                        CandleLightPlugin.log(project,
                                "[LoomCompat] Removed Fabric-Loom-Version from " + archive.getName()
                                        + " (older-Loom projects can now depend on it)");
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to strip Loom version from " + archive, e);
                }
            });
        });
    }

    private static boolean stripLoomVersion(Path jarFile) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(jarFile, (ClassLoader) null)) {
            Path manifestPath = fs.getPath("META-INF", "MANIFEST.MF");
            if (!Files.exists(manifestPath)) return false;

            Manifest manifest;
            try (InputStream in = Files.newInputStream(manifestPath)) {
                manifest = new Manifest(in);
            }

            if (manifest.getMainAttributes().remove(LOOM_VERSION) == null) {
                return false;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            manifest.write(out);
            Files.write(manifestPath, out.toByteArray());
            return true;
        }
    }
}
