package net.mehvahdjukaar.candlelight.api;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BeanAlias {
    String value();
}