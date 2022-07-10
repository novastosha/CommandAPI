package net.zoda.api.command.subcommand.group;

import java.lang.annotation.*;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@Repeatable(SubcommandGroups.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubcommandGroup {

    String value();
}
