package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes the annotated method from bean-convention alias generation.
 * <p>
 * Use this to opt a single method out of the aliasing performed for classes marked with
 * {@link BeanAliases}. Only meaningful within such classes.
 *
 * @see BeanAliases
 * @see BeanAlias
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface NoBeanAlias {}
