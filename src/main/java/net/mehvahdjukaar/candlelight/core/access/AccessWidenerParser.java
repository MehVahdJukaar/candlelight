package net.mehvahdjukaar.candlelight.core.access;

import net.mehvahdjukaar.candlelight.core.access.AccessWidener;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public final class AccessWidenerParser {

    private AccessWidenerParser() {}

    static String trimComments(String value) {
        int index = value.indexOf('#');
        return index == -1 ? value : value.substring(0, index);
    }

    static AccessWidener.Entry parseEntry(List<String> statements) {
        AccessWidener.Modifier modifier =
                AccessWidener.Modifier.valueOf(statements.get(0).toUpperCase());
        AccessWidener.Target target =
                AccessWidener.Target.valueOf(statements.get(1).toUpperCase());
        String className = statements.get(2);

        return switch (target) {
            case CLASS -> new AccessWidener.ClassEntry(modifier, className);
            case METHOD -> new AccessWidener.MethodEntry(
                    modifier, className, statements.get(3), statements.get(4)
            );
            case FIELD -> new AccessWidener.FieldEntry(
                    modifier, className, statements.get(3), statements.get(4)
            );
        };
    }

    @VisibleForTesting
    public static AccessWidener parseAccessWidener(File file) {
        if (!file.exists()) {
            throw new IllegalStateException(
                    "unable to find access widener file '" + file + "'"
            );
        }

        List<List<String>> lines;
        try {
            lines = Files.readAllLines(file.toPath()).stream()
                    .map(AccessWidenerParser::trimComments)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> List.of(s.split("\\s+")))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> headerLine = lines.getFirst();
        String header = headerLine.get(0);
        String version = headerLine.get(1);

        if (!"accessWidener".equals(header)) {
            throw new IllegalStateException("invalid header '" + header + "'");
        }
        if (!"v1".equals(version)) {
            throw new IllegalStateException("access widener transformation does only support v1");
        }

        List<AccessWidener.Entry> entries = lines.subList(1, lines.size())
                .stream()
                .map(AccessWidenerParser::parseEntry)
                .collect(Collectors.toList());

        return new AccessWidener(entries);
    }
}
