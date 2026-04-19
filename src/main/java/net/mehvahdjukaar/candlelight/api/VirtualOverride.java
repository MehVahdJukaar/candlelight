package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Documentation only
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface VirtualOverride {
    String value();
}
