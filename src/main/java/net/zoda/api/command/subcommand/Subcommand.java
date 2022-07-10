package net.zoda.api.command.subcommand;

import net.zoda.api.command.argument.Argument;

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
@Target(ElementType.METHOD)
public @interface Subcommand {

    String name();
    Argument[] arguments() default {};

}
