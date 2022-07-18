package net.zoda.api.command.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.zoda.api.command.ACommand;

import net.zoda.api.command.Command;
import net.zoda.api.command.CommandShortcut;
import net.zoda.api.command.DefaultRun;
import net.zoda.api.command.argument.Argument;
import net.zoda.api.command.argument.ArgumentType;
import net.zoda.api.command.manager.containers.SubcommandsContainer;
import net.zoda.api.command.utils.Pair;
import net.zoda.api.command.utils.ServerVersion;
import net.zoda.api.command.wrapper.BukkitCommandWrapper;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;


/**
 * MIT License
 * <p>
 * Copyright (c) 2022 S. S.
 */
@SuppressWarnings("unchecked")
public final class CommandManager {

    private final Logger logger = Logger.getLogger("CommandAPI");

    private CommandManager() {
    }

    private static CommandManager instance;

    public static CommandManager getInstance() {
        if (instance == null) instance = new CommandManager();
        return instance;
    }

    public void registerCommand(ACommand command, JavaPlugin plugin) {
        Class<? extends ACommand> clazz = command.getClass();

        if (!clazz.isAnnotationPresent(Command.class)) {
            return;
        }

        Command base = clazz.getAnnotation(Command.class);

        SubcommandsContainer subcommandsContainer = new SubcommandsContainer(clazz, base, logger, command);

        DefaultRun defaultRun = null;
        Method defaultRunMethod = null;
        Argument[] orderedDefaultRunArguments = new Argument[0];

        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(DefaultRun.class)) continue;

            if (defaultRun != null) {
                logger.severe("Multiple default run methods found on command: " + base.name());
                return;
            }

            defaultRun = method.getAnnotation(DefaultRun.class);

            if (!verifyArguments(defaultRun.arguments(), clazz, "default", base.playerOnly(), logger)) {
                defaultRun = null;
                break;
            }

            if (!verifySignature(defaultRun.arguments(), method, "default", base.playerOnly(), logger, command)) {
                defaultRun = null;
                break;
            }

            defaultRunMethod = method;
            orderedDefaultRunArguments = orderArguments(defaultRun.arguments());
        }

        if (defaultRun == null) {
            logger.severe("Couldn't find default run method for command: " + base.name());
            return;
        }

        if (orderedDefaultRunArguments.length != 0 && subcommandsContainer.size() != 0) {
            logger.severe(getInvalidSignature(base.name(), "a default run method cannot have any arguments if any subcommand is present"));
        }

        try {

            ServerVersion version = ServerVersion.getVersion();

            Map<String, org.bukkit.command.Command> map = new HashMap<>();

            Server server = Bukkit.getServer();
            Field field = server.getClass().getDeclaredField("commandMap");
            field.setAccessible(true);

            CommandMap commandMap = (CommandMap) field.get(server);
            org.bukkit.command.Command cmd = commandMap.getCommand(base.name());

            if (version.equals(ServerVersion.MODERN)) {
                map = (Map<String, org.bukkit.command.Command>) commandMap.getClass().getDeclaredMethod("getKnownCommands").invoke(commandMap);
            } else {
                Field commandField = commandMap.getClass().getDeclaredField("knownCommands");
                commandField.setAccessible(true);
                map = (Map<String, org.bukkit.command.Command>) commandField.get(commandMap);
            }

            if (cmd != null) {
                cmd.unregister(commandMap);
                map.remove(base.name());
                Arrays.stream(base.aliases()).forEach(map::remove);
            }

            CommandExecutor executor = buildLogic(base, orderedDefaultRunArguments, defaultRunMethod, subcommandsContainer, command);
            TabCompleter tabCompleter = buildTabCompletion(base, orderedDefaultRunArguments, subcommandsContainer, command);

            BukkitCommandWrapper bukkitCmd = new BukkitCommandWrapper(base, executor, tabCompleter);
            commandMap.register(plugin.getName(), bukkitCmd);
            bukkitCmd.register(commandMap);

            logger.info("Command: " + base.name() + " has successfully been registered!");

            loop:
            for (Field searchField : clazz.getDeclaredFields()) {
                if (!searchField.isAnnotationPresent(CommandShortcut.class)) continue;
                CommandShortcut shortcut = searchField.getAnnotation(CommandShortcut.class);

                Argument[] arguments;
                Method method;
                String[] permissions = new String[0];

                if (shortcut.value().equalsIgnoreCase("default")) {
                    arguments = orderedDefaultRunArguments;
                    method = defaultRunMethod;
                } else {
                    SubcommandsContainer.ResolvedSubcommand subcommand = subcommandsContainer.getSubcommandMap().get(shortcut.value());

                    if (subcommand == null) {
                        logger.severe("Unknown subcommand of shortcut: " + shortcut.shortcutName() + " (" + shortcut.value() + ")");
                        continue;
                    }

                    arguments = subcommand.getOrderedArguments();
                    method = subcommand.getMethod();
                    if (subcommand instanceof SubcommandsContainer.GroupedResolvedSubcommand groupedResolvedSubcommand) {
                        permissions = groupedResolvedSubcommand.getGroup().getPermissions();
                    }
                }


                if (!searchField.getType().equals(Map.class)) {
                    logger.severe("Shortcut field type of: " + shortcut.shortcutName() + " is not a map!");
                    continue;
                }

                ParameterizedType type = (ParameterizedType) searchField.getGenericType();
                Class<?> firstType = (Class<?>) type.getActualTypeArguments()[0];
                Class<?> secondType = (Class<?>) type.getActualTypeArguments()[1];


                if (!firstType.equals(String.class)) {
                    logger.severe("Shortcut map of: " + shortcut.shortcutName() + " Key generic is not String");
                    continue;
                }


                if (!secondType.equals(Object.class)) {
                    logger.severe("Shortcut map of: " + shortcut.shortcutName() + " Key generic is not Object");
                    continue;
                }

                searchField.setAccessible(true);
                Map<String, Object> argumentsMap = (Map<String, Object>) searchField.get(command);
                ArrayList<Argument> reducedNeededArgumentsArray = new ArrayList<>(List.of(arguments));


                for (Argument argument : arguments) {
                    if (argumentsMap.containsKey(argument.name())) {

                        Object obj = argumentsMap.get(argument.name());

                        if (argument.type().equals(ArgumentType.ENUM)) {
                            if (!obj.getClass().isEnum()) {
                                logger.severe("Shortcut argument type mismatch: " + shortcut.shortcutName() + " (" + obj.getClass().getCanonicalName() + " is not an enum)");
                                continue loop;
                            }
                        } else if (!obj.getClass().isAssignableFrom(argument.type().clazz)) {
                            logger.severe("Shortcut argument type mismatch: " + shortcut.shortcutName() + " (expected: " + argument.type().clazz.getCanonicalName() + " got: " + obj.getClass().getCanonicalName() + ")");
                            continue loop;
                        }

                        reducedNeededArgumentsArray.remove(argument);
                    }
                }

                Argument[] reducedNeededArguments = orderArguments(reducedNeededArgumentsArray.toArray(new Argument[0]));

                try {
                    CommandExecutor commandExecutor = buildShortcutLogic(base, arguments, argumentsMap, reducedNeededArguments, command, method, permissions);
                    TabCompleter tabCompletion = buildShortcutTabCompletion(base, subcommandsContainer, reducedNeededArguments, command);

                    BukkitCommandWrapper.ShortcutWrapper shortCutBukkitCmd = new BukkitCommandWrapper.ShortcutWrapper(shortcut.shortcutName(), commandExecutor, tabCompletion);
                    commandMap.register(plugin.getName(), shortCutBukkitCmd);
                    shortCutBukkitCmd.register(commandMap);
                } catch (Exception ignored) {
                    logger.severe("Couldn't build shortcut logic: " + shortcut.shortcutName());
                }
            }
        } catch (Exception e) {
            logger.severe("Couldn't build logic of command: " + base.name());
            e.printStackTrace();
        }

    }

    private TabCompleter buildShortcutTabCompletion(Command base, SubcommandsContainer subcommandsContainer, Argument[] reducedNeededArguments, ACommand command) {
        return ((sender, cmd, label, args) -> {
            if (!(sender instanceof Player) && base.playerOnly()) {
                return new ArrayList<>();
            }
            return getCompletions(args, reducedNeededArguments, command, sender);
        });
    }

    private CommandExecutor buildShortcutLogic(Command base, Argument[] arguments, Map<String, Object> argumentsMap, Argument[] reducedNeededArguments, ACommand command, Method method, String[] permissions) {
        return ((sender, cmd, label, args) -> {
            if (!(sender instanceof Player) && base.playerOnly()) {
                sender.sendMessage(ChatColor.RED + "Only players can execute this command!");
                return true;
            }

            for (String permission : base.permissions()) {
                if (sender.hasPermission(permission)) continue;

                sender.sendMessage(ChatColor.RED + "Not enough permissions");
                return true;
            }

            for (String permission : permissions) {
                if (sender.hasPermission(permission)) continue;

                sender.sendMessage(ChatColor.RED + "Not enough permissions");
                return true;
            }

            Map<Argument, Object> mappedArguments = new HashMap<>();

            for (Argument argument : arguments) {
                if (argumentsMap.containsKey(argument.name())) {
                    mappedArguments.put(argument, argumentsMap.get(argument.name()));
                }
            }

            return attemptResolveAndRun(sender, arguments, args, method, command, mappedArguments, reducedNeededArguments);
        });
    }


    private CommandExecutor buildLogic(Command base, Argument[] orderedDefaultRunArguments, Method defaultMethod, SubcommandsContainer subcommandsContainer, ACommand aCommand) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player) && base.playerOnly()) {
                sender.sendMessage(ChatColor.RED + "Only players can execute this command!");
                return true;
            }

            for (String permission : base.permissions()) {
                if (sender.hasPermission(permission)) continue;

                sender.sendMessage(ChatColor.RED + "Not enough permissions");
                return true;
            }

            if (subcommandsContainer.size() == 0 || args.length == 0) {
                return attemptResolveAndRun(sender, orderedDefaultRunArguments, args, defaultMethod, aCommand);
            } else {

                SubcommandsContainer.ResolvedSubcommand resolvedSubcommand = null;
                String subName = "";

                SubcommandsContainer.ResolvedSubcommandGroupMeta resolvedSubcommandGroupMeta = null;

                StringBuilder builder = new StringBuilder();
                int index = 0;

                for (String arg : args) {
                    builder.append(index == 0 ? "" : " ").append(arg);

                    subName = arg;

                    if (!subcommandsContainer.getSubcommandMap().containsKey(builder.toString())) {

                        if (subcommandsContainer.getGroupsMetaMap().containsKey(builder.toString())) {
                            resolvedSubcommandGroupMeta = subcommandsContainer.getGroupsMetaMap().get(builder.toString());
                            index++;
                            continue;
                        }

                        if (resolvedSubcommandGroupMeta != null) {
                            resolvedSubcommand = subcommandsContainer.getSubcommandMap().get(arg);
                            break;
                        }
                    } else {
                        resolvedSubcommand = subcommandsContainer.getSubcommandMap().get(builder.toString());
                        break;
                    }

                    index++;
                }

                if (resolvedSubcommand == null) {
                    sender.sendMessage(ChatColor.RED + "Couldn't find subcommand: " + subName);
                    return attemptResolveAndRun(sender, orderedDefaultRunArguments, args, defaultMethod, aCommand);
                }

                if (resolvedSubcommandGroupMeta != null) {
                    for (String permission : resolvedSubcommandGroupMeta.getPermissions()) {
                        if (sender.hasPermission(permission)) continue;

                        sender.sendMessage(ChatColor.RED + "Not enough permissions");
                        return true;
                    }
                }
                String[] newArgs = new String[0];
                try {
                    newArgs = new String[(args.length - index) - 1];
                    System.arraycopy(args, index + 1, newArgs, 0, (args.length - index) - 1);
                } catch (Exception e) {
                    e.printStackTrace();
                    sender.sendMessage(ChatColor.RED + "Missing arguments!");
                    return true;
                }

                return attemptResolveAndRun(sender, resolvedSubcommand.getOrderedArguments(), newArgs, resolvedSubcommand.getMethod(), aCommand);
            }
        };
    }

    private TabCompleter buildTabCompletion(Command base, Argument[] orderedDefaultRunArguments, SubcommandsContainer subcommandsContainer, ACommand command) {
        return (sender, command1, s, args) -> {

            if (!(sender instanceof Player) && base.playerOnly()) {
                return new ArrayList<>();
            }

            int deepest = 0;
            if (subcommandsContainer.size() == 0) {
                return getCompletions(args, orderedDefaultRunArguments, command, sender);
            } else {

                Map<Integer, List<Pair<String, SubcommandsContainer.ResolvedSubcommandGroupMeta>>> subcommandsMap = new HashMap<>();

                for (String key : subcommandsContainer.getSubcommandMap().keySet()) {
                    String[] splitName = key.split(" ");

                    if (splitName.length > deepest) {
                        deepest = splitName.length;
                    }

                    int i = 0;
                    SubcommandsContainer.ResolvedSubcommand resolvedSubcommand = subcommandsContainer.getSubcommandMap().get(key);

                    for (String split : splitName) {
                        List<Pair<String, SubcommandsContainer.ResolvedSubcommandGroupMeta>> list = subcommandsMap.getOrDefault(i, new ArrayList<>());
                        list.add(new Pair<>(split, (resolvedSubcommand instanceof SubcommandsContainer.GroupedResolvedSubcommand groupedResolvedSubcommand) ? groupedResolvedSubcommand.getGroup() : null));

                        subcommandsMap.put(i, list);
                        i++;
                    }
                }

                int j = 0;
                SubcommandsContainer.ResolvedSubcommandGroupMeta group = null;
                SubcommandsContainer.ResolvedSubcommand subcommand = null;

                StringBuilder groupFinder = new StringBuilder();

                for (int i = 0; i < deepest; i++) {
                    if (args.length <= i) break;

                    SubcommandsContainer.ResolvedSubcommandGroupMeta searchGroup = subcommandsContainer.getGroupsMetaMap().get(groupFinder.toString());
                    if (searchGroup != null) group = searchGroup;

                    groupFinder.append(i == 0 ? "" : " ").append(args[i]);
                    j = i;
                }

                StringBuilder cmdFinder = new StringBuilder();
                String cmdFoundOn = "";

                for (int i = 0; i <= deepest; i++) {
                    if (args.length <= i) break;

                    SubcommandsContainer.ResolvedSubcommand searchCommand = subcommandsContainer.getSubcommandMap().get(cmdFinder.toString());
                    cmdFinder.append(i == 0 ? "" : " ").append(args[i]);

                    if (searchCommand != null) {
                        subcommand = searchCommand;
                        cmdFoundOn = cmdFinder.toString();
                    }

                    j = i;
                }

                if (subcommand == null) {
                    List<String> list = new ArrayList<>();

                    if (subcommandsMap.containsKey(args.length - 1)) {
                        for (Pair<String, SubcommandsContainer.ResolvedSubcommandGroupMeta> pair : subcommandsMap.get(args.length - 1)) {
                            if (pair.getB() == null || args.length == 1) {
                                list.add(pair.getA());
                            } else {
                                SubcommandsContainer.ResolvedSubcommandGroupMeta groupSub = pair.getB();

                                if (group == null) continue;

                                boolean nameEquals = groupSub.getName().equals(group.getName());
                                if (!nameEquals) {
                                    nameEquals = groupSub.getName().split(" ")[j - 1].equals(group.getName());
                                }

                                if (!nameEquals) continue;
                                list.add(pair.getA());
                            }
                        }
                    }

                    return list;
                }

                j = cmdFoundOn.split(" ").length;
                if(j % 2 == 0) j--;

                String[] newArgs = new String[(args.length - j)];
                System.arraycopy(args, j, newArgs, 0, args.length - j);

                return getCompletions(newArgs, subcommand.getOrderedArguments(), command, sender);
            }
        };
    }

    private List<String> getCompletions(String[] args, Argument[] arguments, ACommand command, CommandSender sender) {
        List<String> list = new ArrayList<>();

        Pair<Boolean, String[]> processed = process(args, arguments, sender);

        String[] processedArgs = processed.getB();
        try {
            String last = processedArgs[processedArgs.length - 1];


            Argument argument = arguments[processedArgs.length - 1];

            if(argument.disableCompletions()) return new ArrayList<>();

            if (processed.getA() && argument.type().equals(ArgumentType.STRING)
                    && !(last.equals(" ") || last.equals(""))) {
                return list;
            }


            int spaces = 0;

            for (String space : last.replaceFirst(" ", "").split("")) {
                if (!space.equalsIgnoreCase(" ")) continue;
                spaces++;
            }

            String[] suggestions = generateArgumentInfo(argument, command, sender, last.equals(" ") || last.equals("") ? -1 : spaces, last.split(" "));

            if (argument.type().equals(ArgumentType.TIMESTAMP) && last.equals(" ")) {
                list.addAll(List.of(suggestions));
            }
            if (suggestions.length != 0 && !argument.type().equals(ArgumentType.ROTATION) && !argument.type().equals(ArgumentType.TIMESTAMP)) {
                list.addAll(List.of(suggestions));
            }

            DecimalFormat numberFormat = new DecimalFormat("#.00");

            if (argument.type().equals(ArgumentType.ROTATION)) {
                if (spaces == 0) {

                    for (String sug : suggestions) {
                        String[] split = sug.split(" ", 2);

                        list.add(split[0]);
                    }


                    if (checkAddDefault(suggestions, argument)) {

                        list.add("north");
                        list.add("east");
                        list.add("west");
                        list.add("south");
                        if (sender instanceof Player player) {
                            list.add(numberFormat.format(player.getLocation().getYaw()));

                        }
                    }
                }

                if (spaces == 1) {

                    for (String sug : suggestions) {
                        String[] split = sug.split(" ", 2);

                        list.add(split[1]);
                    }


                    if (checkAddDefault(suggestions, argument)) {
                        list.add("up");
                        list.add("down");
                        if (sender instanceof Player player) {
                            list.add(numberFormat.format(player.getLocation().getPitch()));
                        }
                    }
                }
                return list;
            }

            if (checkAddDefault(suggestions, argument)) {

                if (argument.type().equals(ArgumentType.TIMESTAMP)) {
                    if (!last.equals(" ") && !argument.completerSuggestionsRequired()) {
                        list.addAll(generateTimestampCompletions(last.split(" "), spaces));
                    } else {
                        list.addAll(List.of(suggestions));
                        return list;
                    }
                } else if (argument.type().equals(ArgumentType.LOCATION)) {

                    List<String> custom = new ArrayList<>();

                    if (spaces == 3 && !(sender instanceof Player)) {
                        for (World world : Bukkit.getWorlds()) {
                            list.add(world.getName());
                        }
                        return list;
                    }

                    if (sender instanceof Player player) {
                        custom.add("~ ~ ~");
                        Location location = player.getLocation();
                        custom.add(numberFormat.format(location.getX()) + " " + numberFormat.format(location.getY()) + " " + numberFormat.format(location.getZ()));
                    }

                    list.addAll(getAppropriateCompletion(spaces, custom));
                } else if (argument.type().equals(ArgumentType.PLAYER) || argument.type().equals(ArgumentType.ANY_PLAYER)) {
                    for (Player player : sender.getServer().getOnlinePlayers()) {
                        list.add(player.getName());
                    }
                } else if (argument.type().equals(ArgumentType.BOOLEAN)) {
                    list.addAll(List.of("true", "false"));
                }
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return list;
        }

        return list;
    }

    private List<String> getAppropriateCompletion(int spaces, List<String> custom) {
        if (spaces == 0) {
            return custom;
        }

        List<String> list = new ArrayList<>();

        for (String c : custom) {
            try {
                list.add(c.split(" ")[spaces]);
            } catch (ArrayIndexOutOfBoundsException e) {
                return new ArrayList<>();
            }
        }
        return list;
    }

    private Pair<Boolean, String[]> process(String[] args, Argument[] arguments, CommandSender sender) {
        boolean found_endA = false;
        List<String> strings = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            StringBuilder builder = new StringBuilder();

            if (args[i].startsWith("\"") && !args[i].startsWith("\"\"")) {
                boolean found_end = false;
                for (int string_index = i; string_index < args.length; string_index++) {

                    boolean appendSelf = true;

                    if (string_index == i) {
                        builder.append(args[i].replaceFirst("\"", ""));
                        appendSelf = false;
                    }

                    if (appendSelf) {
                        builder.append(args[string_index]);
                    }

                    builder.append(" ");

                    if (args[string_index].endsWith("\"") && !args[string_index].equals("\"")) {
                        found_end = true;
                        found_endA = true;
                        i = string_index;
                        break;
                    }
                }
                if (!found_end) {
                    strings.add(builder.toString().replaceFirst("\"", ""));
                    return new Pair<>(false, strings.toArray(new String[0]));
                }
            } else {
                if (args[i].startsWith("\"\"")) {
                    builder.append(args[i].replaceFirst("\"", ""));
                } else {
                    try {
                        Argument argument = arguments[strings.size()];

                        if (!argument.type().equals(ArgumentType.TIMESTAMP)) {
                            for (int j = i; j < argument.type().maxArgs + i + 1 + (argument.type().equals(ArgumentType.LOCATION) && !(sender instanceof Player) ? 1 : 0); j++) {
                                try {
                                    String arg = args[j];
                                    builder.append(" ").append(arg);
                                } catch (ArrayIndexOutOfBoundsException ignored) {
                                    strings.add(builder.toString().replaceFirst("\"", ""));
                                    return new Pair<>(false, strings.toArray(new String[0]));
                                }
                            }
                            i += argument.type().maxArgs + (argument.type().equals(ArgumentType.LOCATION) && !(sender instanceof Player) ? 1 : 0) - 1;
                        } else {
                            builder.append(args[i]);
                        }
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                        continue;
                    }
                }
            }
            strings.add(builder.toString().replaceFirst("\"", ""));
        }

        return new Pair<>(found_endA, strings.toArray(new String[0]));
    }

    private boolean checkAddDefault(String[] suggestions, Argument argument) {
        return (suggestions.length == 0 || !argument.completerSuggestionsRequired());
    }

    private List<String> generateTimestampCompletions(String[] args, int length) {

        List<String> list = new ArrayList<>();


        if (length % 2 != 0) {
            try {
                String before = args[length - 1].replaceFirst("\"", "");

                int parsed = Integer.parseInt(before);
                return TimeStampType.getAppropriate(parsed);
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException ignored) {
                return list;
            }
        }
        try {
            String raw = args[length].replaceFirst("\"", "");

            try {
                int parsed = Integer.parseInt(raw);

                for (String rawTimestamp : TimeStampType.getAppropriate(parsed)) {
                    list.add((length == 0 ? "\"" : "") + parsed + " " + rawTimestamp);
                }

            } catch (NumberFormatException ignored) {

                String every = raw.replaceAll("[^\\d]", "");

                try {
                    int parsed = Integer.parseInt(every);

                    for (String rawTimestamp : TimeStampType.getAppropriate(parsed)) {
                        list.add((length == 0 ? "\"" : "") + parsed + " " + rawTimestamp);
                    }
                } catch (NumberFormatException ignored1) {
                    return list;
                }

            }

        } catch (ArrayIndexOutOfBoundsException ignored) {
            return list;
        }
        return list;
    }

    private Map<Integer, Integer> mapArgumentLength(Argument[] arguments, CommandSender sender) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            for (int j = 0; j < arguments[i].type().maxArgs + (!(sender instanceof Player) && arguments[i].type().equals(ArgumentType.LOCATION) ? 1 : 0) + 1; j++) {
                map.put(j + (map.size() + 1), i);


            }
        }
        return map;
    }


    private <T extends CommandSender> String[] generateArgumentInfo(Argument argument, ACommand aCommand, T sender, int use, String[] args) {
        List<String> list = new ArrayList<>();

        List<?> rawCompletions = getRawCompletions(argument, aCommand, sender);


        for (Object obj : rawCompletions) {
            String converted = argument.type().stringify(sender, obj);

            try {


                if (use == -1) {
                    list.add(converted);
                } else {

                    try {
                        String[] convertedSplit = converted.split(" ");


                        String str = converted.split(" ")[use];

                        if (use != 0) {
                            String prev = args[use - 1];
                            boolean found_equal = false;

                            int search_index = 0;

                            for (String search : convertedSplit) {

                                if (search.startsWith("\"")) search = search.replaceFirst("\"", "");

                                if (search.equals(prev) && search_index == use - 1) {
                                    found_equal = true;
                                    break;
                                }
                                search_index++;
                            }

                            if (!found_equal) continue;

                        }

                        list.add(str);
                    } catch (ArrayIndexOutOfBoundsException ignored) {
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }

        Collections.sort(list);
        return list.toArray(new String[0]);
    }


    @SuppressWarnings("unchecked")
    private <T extends CommandSender> List<?> getRawCompletions(Argument argument, ACommand aCommand, T sender) {
        List<?> rawCompletions = new ArrayList<>();

        Member field = SubcommandsContainer.findCompleter(argument, aCommand.getClass());

        if (field == null) {
            return rawCompletions;
        }

        if (argument.type().equals(ArgumentType.ENUM)) {
            Class<?> enumClass = extractClazz(aCommand, field);

            if (enumClass == null) {
                logger.severe("Got a null enum class from argument: " + argument.name());
                return rawCompletions;
            }

            return List.of(enumClass.getEnumConstants());
        } else {
            if (field instanceof Method method) {
                try {
                    method.setAccessible(true);
                    rawCompletions = ((Function<T, List<?>>) method.invoke(aCommand)).apply((sender));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            } else if (field instanceof Field field1) {
                try {
                    field1.setAccessible(true);
                    rawCompletions = ((Function<T, List<?>>) field1.get(aCommand)).apply((sender));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return rawCompletions;

    }

    @RequiredArgsConstructor
    public enum TimeStampType {


        MONTH(new String[]{"m", "month", "months"}, 2628000),
        DAY(new String[]{"d", "day", "days"}, 86400),
        HOUR(new String[]{"h", "hour", "hours"}, 3600),
        MINUTE(new String[]{"min", "minute", "minutes"}, 60),
        SECOND(new String[]{"s", "second", "seconds"}, 1);

        static final String[] TYPES = new String[]{"month", "day", "hour", "minute", "second"};

        private final String[] aliases;
        @Getter
        private final long multiply;

        public static Map<String, TimeStampType> getMapped() {
            Map<String, TimeStampType> map = new HashMap<>();
            for (TimeStampType type : values()) {
                for (String s : type.aliases) {
                    map.put(s, type);
                }
            }
            return map;
        }

        public static List<String> getAppropriate(int parsed) {
            List<String> list = new ArrayList<>();

            for (String type : TYPES) {
                list.add(type + (parsed == 1 ? "" : "s"));
            }
            return list;
        }
    }

    private boolean attemptResolveAndRun(CommandSender sender, Argument[] arguments, String[] args, Method method, ACommand command) {
        return attemptResolveAndRun(sender, arguments, args, method, command, new HashMap<>(), new Argument[0]);
    }

    private boolean attemptResolveAndRun(CommandSender sender, Argument[] arguments, String[] args, Method method, ACommand command, Map<Argument, Object> objectMap, Argument[] reducedNeededArguments) {
        int i = 0;

        Map<Integer, Object> objects = new TreeMap<>();

        objects.put(0, sender);

        if (!objectMap.isEmpty()) {
            for (Map.Entry<Argument, Object> entry : objectMap.entrySet()) {
                objects.put(List.of(arguments).indexOf(entry.getKey()) + 1, entry.getValue());
            }
        }

        for (Argument argument : objectMap.isEmpty() ? arguments : reducedNeededArguments) {

            Object object = null;

            try {

                if (argument.type().equals(ArgumentType.ENUM)) {
                    try {
                        List<?> enumValues = getRawCompletions(argument, command, sender);

                        boolean found_en = false;
                        for (Object obj : enumValues) {
                            Enum<?> e = (Enum<?>) obj;

                            if (e.name().equals(args[i].toUpperCase())) {
                                found_en = true;
                                object = obj;
                                break;
                            }
                        }

                        if (!found_en) {
                            throw new IllegalArgumentException();
                        }
                    } catch (Exception ignored) {
                        sender.sendMessage(ChatColor.RED + "Unknown value: " + args[i]);
                        return false;
                    }
                } else if (argument.type().equals(ArgumentType.LOCATION)) {
                    try {
                        int inc = 0;

                        double x = ArgumentType.parseLocationValue("X", args[i], sender);
                        double y = ArgumentType.parseLocationValue("Y", args[i + 1], sender);
                        double z = ArgumentType.parseLocationValue("Z", args[i + 2], sender);

                        inc += 2;

                        World world = null;

                        if (!(sender instanceof Player)) {
                            try {
                                world = sender.getServer().getWorld(args[i + 3]);
                                inc++;
                            } catch (ArrayIndexOutOfBoundsException e) {
                                sender.sendMessage(ChatColor.RED + "Missing value, need: <x> <y> <z> <world>");
                                return true;
                            }

                            if (world == null) {
                                sender.sendMessage(ChatColor.RED + "Unknown world: " + args[i + 3]);
                                return true;
                            }
                        } else {
                            world = ((Player) sender).getWorld();
                        }

                        i += inc;
                        object = new Location(world, x, y, z);
                    } catch (IllegalArgumentException e) {
                        return true;
                    }
                } else if (argument.type().equals(ArgumentType.TIMESTAMP)) {

                    Map<TimeStampType, Integer> timeMap = new HashMap<>();

                    StringBuilder timestampRaw = new StringBuilder();

                    if (!args[i].startsWith("\"")) {
                        sender.sendMessage(ChatColor.RED + "Timestamps must be captured between double quotes (\")");
                        return true;
                    }

                    boolean found_end = false;
                    for (int timestamp_index = i; timestamp_index < args.length; timestamp_index++) {
                        boolean appendSelf = true;

                        if (timestamp_index == i) {
                            timestampRaw.append(args[i].replaceFirst("\"", ""));
                            appendSelf = false;
                        }

                        if (appendSelf) {
                            timestampRaw.append(args[timestamp_index]);
                        }

                        timestampRaw.append(" ");

                        if (args[timestamp_index].endsWith("\"")) {
                            found_end = true;
                            i = timestamp_index;
                            break;
                        }
                    }
                    if (!found_end) {
                        sender.sendMessage(ChatColor.RED + "Argument: " + argument.name() + ", timestamp never ends");
                        return true;
                    }

                    Map<String, TimeStampType> typeMap = TimeStampType.getMapped();
                    String[] split = timestampRaw.toString().replaceFirst("\"", "").split(" ");

                    for (int j = 0; j < split.length; j++) {
                        String raw = split[j];

                        try {
                            int parsed = Integer.parseInt(raw);
                            try {
                                String type = split[j + 1];

                                if (!typeMap.containsKey(type)) {
                                    sender.sendMessage(ChatColor.RED + "Unknown timestamp type: " + type);
                                    return true;
                                }

                                TimeStampType timeStampType = typeMap.get(type);

                                if (timeMap.containsKey(timeStampType)) {
                                    sender.sendMessage(ChatColor.RED + "Duplicate timestamp types: " + type);
                                    return true;
                                }

                                timeMap.put(timeStampType, parsed);
                                j++;
                            } catch (ArrayIndexOutOfBoundsException ignored) {
                                sender.sendMessage(ChatColor.RED + "Argument: " + argument.name() + ", timestamp type not found!");
                                return true;
                            }
                        } catch (NumberFormatException ignored) {
                            String decimalsRemoved = raw.replaceAll("\\d", "");

                            if (!typeMap.containsKey(decimalsRemoved)) {
                                sender.sendMessage(ChatColor.RED + "Unknown timestamp type: " + decimalsRemoved);
                                return true;
                            }

                            TimeStampType type = typeMap.get(decimalsRemoved);
                            String timeRemoved = raw.replaceAll(decimalsRemoved, "");

                            try {
                                int time = Integer.parseInt(timeRemoved);

                                if (timeMap.containsKey(type)) {
                                    sender.sendMessage(ChatColor.RED + "Duplicate timestamp types: " + type);
                                    return true;
                                }

                                timeMap.put(type, time);
                            } catch (NumberFormatException e) {
                                sender.sendMessage(ChatColor.RED + "Couldn't parse timestamp integer: " + timeRemoved);
                                return true;
                            }
                        }
                    }

                    if (!timeMap.isEmpty()) {

                        long totalAdd = 0;

                        for (Map.Entry<TimeStampType, Integer> entry : timeMap.entrySet()) {
                            totalAdd += (entry.getValue() * (entry.getKey().multiply * 1000));
                        }

                        object = totalAdd;
                    } else {
                        sender.sendMessage(ChatColor.RED + "No timestamps found!");
                        return true;
                    }
                } else if (argument.type().equals(ArgumentType.STRING)) {
                    StringBuilder builder = new StringBuilder();

                    if (args[i].startsWith("\"") && !args[i].startsWith("\"\"")) {
                        boolean found_end = false;
                        for (int string_index = i; string_index < args.length; string_index++) {

                            boolean appendSelf = true;

                            if (string_index == i) {
                                builder.append(args[i].replaceFirst("\"", ""));
                                appendSelf = false;
                            }

                            if (appendSelf) {
                                builder.append(args[string_index]);
                            }

                            builder.append(" ");

                            if (args[string_index].endsWith("\"")) {
                                found_end = true;
                                i = string_index;
                                break;
                            }
                        }
                        if (!found_end) {
                            sender.sendMessage(ChatColor.RED + " Argument: " + argument.name() + ", String never ends");
                            return true;
                        }
                    } else {
                        builder.append(args[i].replaceFirst((args[i].startsWith("\"\"") ? "\"" : ""), ""));
                    }
                    object = builder.toString().replaceFirst("\"", "");
                    if (((String) object).contains(" ")) {
                        object = ((String) object).substring(0, ((String) object).length() - 1);
                    }
                } else {
                    if (args.length - i + 1 < argument.type().maxArgs) {
                        sender.sendMessage(ChatColor.RED + "Not enough sub-arguments for: " + argument.name() + " (need: " + argument.type().maxArgs + " got: " + (args.length - i + 1) + ")");
                        return false;
                    }

                    int argsUse = argument.type().maxArgs;

                    try {
                        String[] newArgAs = new String[argsUse];
                        System.arraycopy(args, i, newArgAs, 0, argsUse);

                        object = argument.type().convert(newArgAs, sender);
                    } catch (Exception ignored) {
                    }

                    i += argsUse - 1;
                }
            } catch (ArrayIndexOutOfBoundsException ignored) {
                if (!argument.required()) {

                    continue;
                }
            }

            if (argument.required() && object == null) {
                sender.sendMessage(ChatColor.RED + "Missing argument: " + argument.name());
                return false;
            }

            if (!getRawCompletions(argument, command, sender).isEmpty() && (!getRawCompletions(argument, command, sender).contains(object) && argument.completerSuggestionsRequired())) {
                sender.sendMessage(ChatColor.RED + "Invalid argument: " + argument.name());
                return true;
            }

            objects.put(List.of(arguments).indexOf(argument) + 1, object);
            i++;
        }

        for (int j = 0; j < arguments.length; j++) {
            objects.putIfAbsent(j + 1, null);
        }

        try {
            method.invoke(command, objects.values().toArray(new Object[0]));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }


    private Argument[] orderArguments(Argument[] arguments) {
        return getArguments(arguments);
    }

    @NotNull
    public static Argument[] getArguments(Argument[] arguments) {
        ArrayList<Argument> sorted = new ArrayList<>();
        ArrayList<Argument> sortedLast = new ArrayList<>();

        for (Argument argument : arguments) {
            if (argument.required()) {
                sorted.add(argument);
            } else {
                sortedLast.add(argument);
            }
        }

        sorted.addAll(sortedLast);
        sortedLast.clear();
        return sorted.toArray(new Argument[0]);
    }


    public static boolean verifySignature(Argument[] arguments, Method method, String name, boolean playerOnly, Logger logger, ACommand command) {
        if (method.getReturnType() != void.class) {
            logger.severe(getInvalidSignature(name, "method must return void"));
            return false;
        }

        if (method.getParameterTypes().length < arguments.length + 1) {
            logger.severe(getInvalidSignature(name, "method types must be: " + (arguments.length + 1) + ", got: " + method.getParameterTypes().length));
            return false;
        }

        if (!method.getParameterTypes()[0].equals(Player.class) && !method.getParameterTypes()[0].equals(CommandSender.class)) {
            logger.severe(getInvalidSignature(name, "first parameter is neither a Player or a CommandSender"));
            return false;
        }

        if (method.getParameterTypes()[0].equals(Player.class) && !playerOnly) {
            logger.severe(getInvalidSignature(name, "non-player-only commands cannot supply Player type"));
            return false;
        }

        int index = 1;

        for (Argument argument : arguments) {
            Class<?> clazz = argument.type().clazz;

            if (argument.type().equals(ArgumentType.LOCATION)) {
                clazz = Location.class;
            } else if (argument.type().equals(ArgumentType.ENUM)) {
                Member field = SubcommandsContainer.findCompleter(argument, command.getClass());

                clazz = extractClazz(command, field);
            }

            if (!method.getParameterTypes()[index].equals(clazz)) {
                logger.severe(getInvalidSignature(name, "type mismatch at argument: " + argument.name() + ", expected: " + argument.type().clazz.getSimpleName() + " got: " + method.getParameterTypes()[index].getSimpleName()));
                return false;
            }
            index++;
        }
        return true;
    }

    private static Class<?> extractClazz(ACommand command, Member field) {
        Class<?> enumClass = null;
        if (field instanceof Method methodA) {
            try {
                methodA.setAccessible(true);
                enumClass = (Class<?>) methodA.invoke(command);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } else if (field instanceof Field field1) {
            try {
                field1.setAccessible(true);
                enumClass = (Class<?>) field1.get(command);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return enumClass;
    }

    private static String getInvalidSignature(String name, String reason) {
        return "Invalid signature of: " + name + " (" + reason + ")";
    }

    public static boolean verifyArguments(Argument[] arguments, Class<? extends ACommand> command, String name, boolean playerOnly, Logger logger) {
        for (Argument argument : arguments) {
            Member field = null;
            if (argument.completer().isBlank() || argument.completer().isEmpty()) {
                field = attemptFindCompleter(command, argument.name());
            } else {
                field = attemptFindCompleter(command, argument.completer());

                if (field == null && argument.completer().startsWith("field=")) {
                    try {
                        field = command.getDeclaredField(argument.completer().replaceFirst("field=", ""));
                    } catch (NoSuchFieldException ignored) {
                    }
                } else if (field == null && argument.completer().startsWith("method=")) {
                    try {
                        field = command.getDeclaredMethod(argument.completer().replaceFirst("method=", ""));
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
            if (field == null) {
                if (argument.type().equals(ArgumentType.ENUM)) {
                    logger.severe("Enum arguments must have a class field / method to identify it");
                    return false;
                }
                logger.warning("Couldn't resolve any completer for argument: " + argument.completer() + " from: " + name);
                continue;
            }

            if (field instanceof Method method) {
                if (method.getParameterTypes().length != 0) {
                    logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "no parameters should be added to the completer getter method"));
                    return false;
                }

                if (argument.type().equals(ArgumentType.ENUM)) {
                    try {
                        Class<?> clazz = (Class<?>) ((ParameterizedType) method.getGenericReturnType()).getActualTypeArguments()[0];
                        if (!clazz.isEnum()) {
                            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "enum arguments must set its completer as a class not a function"));
                            return false;
                        }
                    } catch (Exception e) {
                        logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "enum completer is not a class!"));
                        return false;
                    }
                    return true;
                }

                if (!method.getReturnType().equals(Function.class)) {
                    logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "getter method doesn't return a Function"));
                    return false;
                }

                ParameterizedType type = (ParameterizedType) method.getGenericReturnType();

                if (check(name, playerOnly, argument, type, logger)) return false;
            } else if (field instanceof Field field1) {

                if (argument.type().equals(ArgumentType.ENUM)) {
                    try {

                        Class<?> clazz = (Class<?>) ((ParameterizedType) field1.getGenericType()).getActualTypeArguments()[0];

                        if (!clazz.isEnum()) {
                            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "enum arguments must set its completer as a class not a function"));
                            return false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "enum completer is not a class!"));
                        return false;
                    }
                    return true;
                }


                if (!field1.getType().equals(Function.class)) {
                    logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "getter method doesn't return a Function"));
                    return false;
                }

                ParameterizedType type = (ParameterizedType) field1.getGenericType();

                if (check(name, playerOnly, argument, type, logger)) return false;
            }
        }
        return true;
    }

    private static boolean check(String name, boolean playerOnly, Argument argument, ParameterizedType type, Logger logger) {

        if (argument.type().equals(ArgumentType.BOOLEAN)) {
            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "a boolean argument cannot have completions"));
            return true;
        }

        Class<?> firstClass = (Class<?>) type.getActualTypeArguments()[0];
        Class<?> secondClass = ((Class<?>) ((ParameterizedType) type.getActualTypeArguments()[1]).getRawType());

        if (!firstClass.equals(Player.class) && !firstClass.equals(CommandSender.class)) {
            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "first parameter is neither a Player or a CommandSender"));
            return true;
        }

        if (firstClass.equals(Player.class) && !playerOnly) {
            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "non-player-only commands cannot supply Player type"));
            return true;
        }

        if (!secondClass.equals(List.class)) {
            logger.severe(getInvalidSignature("argument: " + argument.name() + " from: " + name, "second parameter is not a list!"));
            return true;
        }

        ParameterizedType type1 = ((ParameterizedType) type.getActualTypeArguments()[1]);

        if (!type1.getActualTypeArguments()[0].equals(argument.type().clazz)) {
            logger.severe("Completer type mismatch on argument: " + argument.name() + "(expected: " + argument.type().clazz.getSimpleName() + ", got: " + ((Class<?>) type1.getActualTypeArguments()[0]).getSimpleName() + ")");
            return true;
        }

        return false;
    }

    private static Member attemptFindCompleter(Class<? extends ACommand> command, String search) {
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
}
