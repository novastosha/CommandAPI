package net.zoda.api.command.argument;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Argument {

    String name();
    ArgumentType type();

    /**
     * Provide with name of the field / method that contains argument completions
     * A Completer is searched for automatically based on the argument's name
     *
     */
    String completer() default "";
    CompleterType completerType() default CompleterType.FIELD;

    boolean completerSuggestionsRequired() default true;

    boolean required() default true;

}
