package com.virjar.sekiro.api.databind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by virjar on 2019/1/24.<br>
 * auto bind/transform  to a give actionRequestHandler instance
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface AutoBind {
    String value() default "";

    boolean require() default false;

    String defaultStringValue() default "";

    int defaultIntValue() default 0;

    boolean defaultBooleanValue() default false;

    long defaultLongValue() default 0L;

    double defaultDoubleValue() default 0D;
}
