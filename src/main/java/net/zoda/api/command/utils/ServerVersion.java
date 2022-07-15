package net.zoda.api.command.utils;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;

import java.lang.reflect.Field;
import java.util.logging.Logger;


/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
public enum ServerVersion {
    LEGACY,
    MODERN;

    private static final Logger versionLogger = Logger.getLogger("CommandAPI Version Checker");
    private static ServerVersion setVersion = null;

    public static ServerVersion getVersion() {
        if (setVersion == null) {

            String packageName = Bukkit.getServer().getClass().getName();

            packageName = packageName.split("\\.")[3];
            packageName = packageName.substring(0, packageName.length() - 3); // removes the _R1 for example
            packageName = packageName.substring(2);

            try {
                int parsed = Integer.parseInt(packageName);

                if (parsed <= 12) {
                    setVersion = LEGACY;
                } else {
                    setVersion = MODERN;
                }
            } catch (NumberFormatException ignored) {
                Server server = Bukkit.getServer();
                try {
                    Field field = server.getClass().getDeclaredField("commandMap");
                    field.setAccessible(true);
                    CommandMap commandMap = (CommandMap) field.get(server);
                    try {
                        commandMap.getClass().getDeclaredField("knownCommands");
                        setVersion = LEGACY;
                    } catch (NoSuchFieldException e) {
                        commandMap.getClass().getDeclaredMethod("getKnownCommands");
                        setVersion = MODERN;
                    }
                } catch (Exception e) {
                    versionLogger.severe("Couldn't determine version using reflection! Defaulting to LEGACY");
                    setVersion = LEGACY;
                }
            }
        }
        return setVersion;
    }
}
