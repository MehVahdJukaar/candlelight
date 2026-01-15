package net.mehvahdjukaar.candlelight.core;

import net.mehvahdjukaar.candlelight.core.access.AccessWidenerTransformationPlugin;
import net.mehvahdjukaar.candlelight.core.processors.CLAnnotationsPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class CandleLightPlugin  implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        CLAnnotationsPlugin.apply(target);
        AccessWidenerTransformationPlugin.apply(target);

    }
}
