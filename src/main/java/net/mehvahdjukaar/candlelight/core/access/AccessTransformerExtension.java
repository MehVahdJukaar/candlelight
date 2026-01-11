package net.mehvahdjukaar.candlelight.core.access;

import org.gradle.api.file.RegularFileProperty;

public interface AccessTransformerExtension {
    RegularFileProperty getFrom();
}