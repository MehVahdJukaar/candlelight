package net.mehvahdjukaar.candlelight.api;


import java.lang.annotation.*;

/**
 * Overrides the prefix used when generating a bean-convention alias for the annotated method.
 * <p>
 * By default {@link BeanAliases} derives the prefix automatically ({@code get}, {@code is} or
 * {@code set}). Use this annotation to force a specific prefix instead; the generated alias name
 * is {@code value + CapitalizedMethodName}. Only effective in classes marked with
 * {@link BeanAliases}.
 *
 * @see BeanAliases
 * @see NoBeanAlias
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeanAlias {
    /**
     * The prefix to prepend to the capitalized method name when building the alias.
     */
    String value();
}
