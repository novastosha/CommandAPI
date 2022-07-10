package net.zoda.api.command.wrapper;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */

public class BukkitCommandWrapper extends Command {
    private final TabCompleter completer;
    private final CommandExecutor executor;

    public BukkitCommandWrapper(net.zoda.api.command.Command base, CommandExecutor executor, TabCompleter completer) {
        super(base.name(), base.description(), base.usage(), List.of(base.aliases()));
        this.executor = executor;
        this.completer = completer;
    }

    @Override
    public boolean execute(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) {
        return executor.onCommand(commandSender,this,s,strings);
    }


    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        List<String> list = completer.onTabComplete(sender,this,alias,args);
        return list == null ? new ArrayList<>() : list;
    }

    public static class ShortcutWrapper extends Command {
        private final TabCompleter completer;
        private final CommandExecutor executor;

        public ShortcutWrapper(String name,  CommandExecutor executor, TabCompleter completer) {
            super(name,"","",List.of());
            this.executor = executor;
            this.completer = completer;
        }

        @Override
        public boolean execute(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) {
            return  executor.onCommand(commandSender,this,s,strings);
        }

        @Override
        public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
            List<String> list = completer.onTabComplete(sender,this,alias,args);
            return list == null ? new ArrayList<>() : list;
        }
    }
}
