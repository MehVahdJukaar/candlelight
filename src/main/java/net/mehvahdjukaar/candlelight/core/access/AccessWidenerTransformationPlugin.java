package net.mehvahdjukaar.candlelight.core.access;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;

@SuppressWarnings("unused")
public final class AccessWidenerTransformationPlugin
        implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        AccessTransformerExtension extension =
                project.getExtensions()
                        .create("access", AccessTransformerExtension.class);

        AccessWidenerGradle.generateAccessTransformer(
                project,
                extension.getFrom().map(RegularFile::getAsFile)
        );
    }
}
