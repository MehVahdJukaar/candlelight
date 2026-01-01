package net.mehvahdjukaar.candlelight.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface PlatformImpl { }