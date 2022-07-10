package net.zoda.api.command.subcommand.group.info;

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
public @interface SubcommandGroupsMeta {
    SubcommandGroupMeta[] value();
}
