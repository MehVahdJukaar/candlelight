package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Marks a method whose body is replaced at build time by a call to its platform-specific
 * implementation.
 * <p>
 * The original body is discarded and replaced with a delegating call to a {@code static} method of
 * the same name and descriptor located in a sibling class named {@code <ClassName>Impl} within a
 * {@code platform} subpackage. For example a method in {@code com.example.Foo} delegates to
 * {@code com.example.platform.FooImpl}. This provides a single common declaration backed by
 * per-platform implementations without hand-written boilerplate.
 *
 * @see net.mehvahdjukaar.candlelight.core.processors.PlatImplProcessor
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface PlatformImpl { }
