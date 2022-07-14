package net.zoda.api.command.manager.containers;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.zoda.api.command.ACommand;
import net.zoda.api.command.Command;
import net.zoda.api.command.argument.Argument;
import net.zoda.api.command.argument.CompleterType;
import net.zoda.api.command.manager.CommandManager;
import net.zoda.api.command.subcommand.Subcommand;
import net.zoda.api.command.subcommand.group.SubcommandGroup;
import net.zoda.api.command.subcommand.group.SubcommandGroups;
import net.zoda.api.command.subcommand.group.info.SubcommandGroupMeta;
import net.zoda.api.command.subcommand.group.info.SubcommandGroupsMeta;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static net.zoda.api.command.manager.CommandManager.getArguments;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
public class SubcommandsContainer {


    @Getter
    private final Class<? extends ACommand> clazz;
    @Getter
    private final Command base;
    private final Logger logger;
    @Getter
    private final Map<String, ResolvedSubcommand> subcommandMap;
    @Getter
    private final Map<String, ResolvedSubcommandGroupMeta> groupsMetaMap;


    public SubcommandsContainer(Class<? extends ACommand> clazz, Command base, Logger logger,ACommand command) {
        this.clazz = clazz;
        this.logger = logger;
        this.base = base;

        this.groupsMetaMap = findGroupsMeta();
        this.subcommandMap = findSubcommands(groupsMetaMap,command);
    }

    public static Member findCompleter(Argument argument, Class<? extends ACommand> command) {
        if (argument.completer().isBlank() || argument.completer().isEmpty()) {
            return attemptFindCompleter(command, argument.name());
        } else {
            Member field = attemptFindCompleter(command, argument.completer());

            if (field == null && argument.completerType().equals(CompleterType.FIELD)) {
                try {
                    return command.getDeclaredField(argument.completer());
                } catch (NoSuchFieldException ignored) {
                }
            } else if (field == null && argument.completerType().equals(CompleterType.METHOD)) {
                try {
                    return command.getDeclaredMethod(argument.completer());
                } catch (NoSuchMethodException ignored) {
                }
            }
            return null;
        }
    }

    private Map<String, ResolvedSubcommand> findSubcommands(Map<String, ResolvedSubcommandGroupMeta> groupsMetaMap,ACommand command) {
        Map<String, ResolvedSubcommand> commands = new HashMap<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Subcommand.class)) continue;

            Subcommand subcommand = method.getAnnotation(Subcommand.class);


            if (!CommandManager.verifyArguments(subcommand.arguments(), clazz, subcommand.name(), base.playerOnly(),logger)) continue;
            if (!CommandManager.verifySignature(subcommand.arguments(), method, subcommand.name(), base.playerOnly(),logger,command)) continue;

            Argument[] orderedArguments = orderArguments(subcommand.arguments());

            if (!method.isAnnotationPresent(SubcommandGroups.class)) {
                System.out.println("Its not present!");
                if (commands.containsKey(subcommand.name())) {
                    logger.severe("Duplicate subcommand names! (" + subcommand.name() + ")");
                    break;
                }

                commands.put(subcommand.name(), new ResolvedSubcommand(subcommand, orderedArguments,method));
            } else {
                SubcommandGroups groups = method.getAnnotation(SubcommandGroups.class);

                String fullName = getHierarchy(groups)+" "+subcommand.name();

                if (commands.containsKey(fullName)) {
                    logger.severe("Duplicate subcommand names! (" + fullName + ")");
                    break;
                }

                if (groups.value().length > 1) {
                    for (SubcommandGroup group : groups.value()) {

                        if(!groupsMetaMap.containsKey(group.value())) {
                            groupsMetaMap.put(group.value(), new ResolvedSubcommandGroupMeta(group.value(), new String[0]));

                        }
                        //Skip last group
                        if (groups.value()[groups.value().length-1] == group) continue;

                        for (ResolvedSubcommand resolvedSubcommand : commands.values()) {
                            if (!(resolvedSubcommand instanceof GroupedResolvedSubcommand groupedResolvedSubcommand))
                                continue;

                            if (group.value().equals(groupedResolvedSubcommand.group.name)) {
                                logger.severe("A multi-level subcommand group cannot have subcommands! (Argument parsing limitation)");
                                commands.clear();
                                break;
                            }
                        }
                    }
                }

                String hierarchyName = getHierarchy(groups);

                if (!groupsMetaMap.containsKey(hierarchyName)) {
                    groupsMetaMap.put(hierarchyName, new ResolvedSubcommandGroupMeta(hierarchyName, new String[0]));
                    logger.info("Created new fake subcommand group!");
                }

                commands.put(fullName, new GroupedResolvedSubcommand(groupsMetaMap.get(hierarchyName), subcommand, orderedArguments,method));
            }
        }

        return commands;
    }

    private String getHierarchy(SubcommandGroups groups) {
        StringBuilder builder = new StringBuilder();

        int index = 0;
        for (SubcommandGroup group : groups.value()) {
            System.out.println(group.value());
            builder.append(index == 0 ? "" : " ").append(group.value());
            index++;
        }

        return builder.toString();
    }

    private Map<String, ResolvedSubcommandGroupMeta> findGroupsMeta() {
        Map<String, ResolvedSubcommandGroupMeta> map = new HashMap<>();
        if (!clazz.isAnnotationPresent(SubcommandGroupsMeta.class)) return map;


        for (SubcommandGroupMeta meta : clazz.getAnnotation(SubcommandGroupsMeta.class).value()) {
            if (meta.value().split(" ").length > 1) {
                String[] split = meta.value().split(" ");

                List<String> permissions = new ArrayList<>();

                for (String s : split) {
                    if(s.equals(split[split.length-1])) continue;

                    if(map.containsKey(s)) {
                        permissions.addAll(List.of(map.get(s).permissions));
                    }else{
                        map.put(s,new ResolvedSubcommandGroupMeta(s,new String[0]));
                    }


                }
                permissions.addAll(List.of(meta.permissions()));

                map.put(meta.value(),new ResolvedSubcommandGroupMeta(meta.value(),permissions.toArray(new String[0])));
            } else {
                map.put(meta.value(), new ResolvedSubcommandGroupMeta(meta));
            }
        }

        return map;
    }


    public int size() {
        return subcommandMap.size();
    }

    @RequiredArgsConstructor
    public static class ResolvedSubcommand {

        @Getter
        private final Subcommand subcommand;
        @Getter
        private final Argument[] orderedArguments;
        @Getter
        private final Method method;

    }

    public static class GroupedResolvedSubcommand extends ResolvedSubcommand {


        @Getter private final ResolvedSubcommandGroupMeta group;

        public GroupedResolvedSubcommand(ResolvedSubcommandGroupMeta groups, Subcommand subcommand, Argument[] orderedArguments,Method method) {
            super(subcommand, orderedArguments,method);
            this.group = groups;
        }
    }

    public static Argument[] orderArguments(Argument[] arguments) {
        return getArguments(arguments);
    }

    public static String getInvalidSignature(String name, String reason) {
        return "Invalid signature of: " + name + " (" + reason + ")";
    }


    public static Member attemptFindCompleter(Class<? extends ACommand> command, String search) {
        try {
            return command.getDeclaredField(search);
        } catch (NoSuchFieldException e) {
            try {
                return command.getDeclaredMethod(search);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    public static class ResolvedSubcommandGroupMeta {

        @Getter
        private final String name;
        @Getter
        private final String[] permissions;

        public ResolvedSubcommandGroupMeta(SubcommandGroupMeta groupMeta) {
            this(groupMeta.value(), groupMeta.permissions());
        }

        public ResolvedSubcommandGroupMeta(String name, String[] permissions) {
            this.name = name;
            this.permissions = permissions;
        }

    }
}
