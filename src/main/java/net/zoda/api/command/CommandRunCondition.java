package net.zoda.api.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * This was added to evade boilerplate in multiple subcommands
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface CommandRunCondition {

    /**
     *
     * Use "*" for all (sub)commands and "default" to target the default run
     */
    String[] value();
}
