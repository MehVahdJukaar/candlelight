package net.mehvahdjukaar.candlelight.core.access;

import java.util.List;

public record AccessWidener(List<Entry> entries) {

    public interface Entry {
        Target getTarget();

        Modifier modifier();

        String className();
    }

    public record ClassEntry(Modifier modifier, String className) implements Entry {

        @Override
        public Target getTarget() {
            return Target.CLASS;
        }
    }

    public record MethodEntry(Modifier modifier, String className, String name, String descriptor) implements Entry {

        @Override
        public Target getTarget() {
            return Target.METHOD;
        }
    }

    public record FieldEntry(Modifier modifier, String className, String name, String descriptor) implements Entry {

        @Override
        public Target getTarget() {
            return Target.FIELD;
        }
    }

    public enum Modifier {
        ACCESSIBLE,
        MUTABLE,
        EXTENDABLE
    }

    public enum Target {
        CLASS,
        METHOD,
        FIELD
    }
}
