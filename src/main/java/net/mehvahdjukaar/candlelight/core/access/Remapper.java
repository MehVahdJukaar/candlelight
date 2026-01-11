package net.mehvahdjukaar.candlelight.core.access;

import org.gradle.api.Project;
import org.gradle.api.Task;

/**
 * A modern Remapper interface for NeoForge.
 * SRG / MCP is no longer used; mapping is typically identity or Tiny/Mojang mappings.
 */
public interface Remapper {

    String remapClass(String value);
    String remapField(String className, String field);
    String remapMethod(String className, String method, String descriptor);
    Task getTask();

    /**
     * Identity remapper (no mapping applied)
     */
    static Remapper empty(Task task) {
        return new Remapper() {
            @Override
            public String remapClass(String value) {
                return value;
            }

            @Override
            public String remapField(String className, String field) {
                return field;
            }

            @Override
            public String remapMethod(String className, String method, String descriptor) {
                return method + descriptor;
            }

            @Override
            public Task getTask() {
                return task;
            }
        };
    }

    /**
     * Detect NeoForge presence. No mapping tasks exist, so always returns identity.
     */
    static Remapper detectMappings(Project project) {
        // NeoForge plugin is applied, but there is no SRG / MCP task
        if (project.getPlugins().findPlugin("net.neoforged.gradle") != null) {
            project.getLogger().info("NeoForge detected — SRG remapping disabled.");
        }
        return empty(null);
    }
}
