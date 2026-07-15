package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.*;

/**
 * Makes the annotated class implement an additional interface at build time.
 * <p>
 * During class transformation the given interface is appended to the class's implemented
 * interface list (unless it is already present). This is useful for "soft" implementations
 * where the class must conform to an interface that may not exist at compile time or that
 * you do not want to reference directly in source.
 * <p>
 * Retained in the {@code .class} file so the build-time processor can read it, but it is not
 * required at runtime.
 *
 * @see net.mehvahdjukaar.candlelight.core.processors.OptionalInterfaceProcessor
 */
@Retention(RetentionPolicy.CLASS) // important: visible in .class, not runtime required
@Target(ElementType.TYPE)
public @interface OptionalInterface {
    /**
     * The fully-qualified name of the interface to implement, e.g. {@code "com.example.MyInterface"}.
     */
    String value();
}
