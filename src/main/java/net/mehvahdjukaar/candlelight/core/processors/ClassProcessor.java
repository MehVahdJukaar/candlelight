package net.mehvahdjukaar.candlelight.core.processors;


import net.mehvahdjukaar.candlelight.core.CandleLightExtension;
import org.gradle.api.Project;

public interface ClassProcessor {

    byte[] transform(byte[] classBytes, Project project, CandleLightExtension ext);
}
