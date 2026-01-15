package net.mehvahdjukaar.candlelight.core.access;

import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public final class AccessWidenerUtils {


    static String transform(AccessWidener.Modifier modifier) {
        return switch (modifier) {
            case ACCESSIBLE -> "public";
            case MUTABLE -> "public-f";
            case EXTENDABLE -> "protected-f";
        };
    }

    static String remapNotation(String value) {
        return value.replace('/', '.');
    }

    @VisibleForTesting
    public static String toAccessTransformer(AccessWidener widener, Remapper remapper) {
        if (remapper == null) {
            remapper = Remapper.empty(null);
        }

        List<String> lines = new ArrayList<>();

        for (AccessWidener.Entry entry : widener.entries()) {
            String modifier = transform(entry.modifier());
            String className = remapNotation(
                    remapper.remapClass(entry.className())
            );

            List<String> parts = new ArrayList<>();
            parts.add(modifier);
            parts.add(className);

            if (entry instanceof AccessWidener.FieldEntry e) {
                parts.add(remapper.remapField(e.className(), e.name()));
                parts.add("# " + e.name());
            } else if (entry instanceof AccessWidener.MethodEntry e) {
                parts.add(remapper.remapMethod(
                        e.className(), e.name(), e.descriptor()
                ));
                parts.add("# " + e.name());
            }

            lines.add(String.join(" ", parts));
        }

        return String.join("\n", lines);
    }
}
