package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documentation-only marker indicating that the annotated method conceptually overrides a method
 * that is not visible to the Java compiler (for example one injected by another build-time
 * transform or mixin).
 * <p>
 * It carries no runtime or build-time behaviour; it exists purely to document the relationship in
 * source. Retained only in source ({@link RetentionPolicy#SOURCE}).
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface VirtualOverride {
    /**
     * The name (or signature) of the method being virtually overridden.
     */
    String value();
}
