package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables generation of bean-convention getter/setter aliases for the annotated class.
 * <p>
 * When present, the build-time processor scans every public, non-synthetic method whose name
 * starts with a lowercase letter and, for zero-argument methods (getters) and single-argument
 * {@code void} methods (setters), generates a delegating alias following JavaBean naming
 * conventions (e.g. {@code name()} &rarr; {@code getName()}, {@code active()} &rarr;
 * {@code isActive()}, {@code name(String)} &rarr; {@code setName(String)}). Methods that already
 * follow the {@code get}/{@code is}/{@code has}/{@code can} convention, or that would collide with
 * an existing method, are skipped.
 * <p>
 * Individual methods can be excluded with {@link NoBeanAlias} or given a custom prefix with
 * {@link BeanAlias}.
 *
 * @see BeanAlias
 * @see NoBeanAlias
 * @see net.mehvahdjukaar.candlelight.core.processors.BeanConventionProcessor
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface BeanAliases {}
