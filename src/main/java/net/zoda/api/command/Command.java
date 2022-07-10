package net.zoda.api.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {

    String name();
    boolean playerOnly() default false;
    String[] aliases() default {};
    String[] permissions() default {};

    String usage() default "";

    String description() default "";
}
