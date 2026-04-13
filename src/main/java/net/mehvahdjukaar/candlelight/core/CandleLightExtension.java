package net.mehvahdjukaar.candlelight.core;

import org.gradle.api.file.RegularFileProperty;

public interface CandleLightExtension {
    default String platformPackage(){
        return "platform";
    }

    default boolean logging(){
        return true;
    }
}