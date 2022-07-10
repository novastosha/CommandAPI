package net.zoda.api.command.subcommand.group.info;

import java.lang.annotation.*;

/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(SubcommandGroupsMeta.class)
public @interface SubcommandGroupMeta {

    String value();
    String[] permissions();

}
