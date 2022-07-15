package net.zoda.api.command.argument;

import lombok.RequiredArgsConstructor;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.IllegalFormatException;
import java.util.List;

/**
 * MIT License
 *
 * Copyright (c) 2022 S. S.
 */
@RequiredArgsConstructor
public enum ArgumentType {

    ENUM(1, Enum.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {
            return null;
        }

        @Override
        public String stringify(CommandSender sender, Object object) {
            Enum<?> e = (Enum<?>) object;

            return e.name();
        }
    },
    BOOLEAN(1, Boolean.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {
            try {
                if(!args[0].equalsIgnoreCase("false") && !args[0].equalsIgnoreCase("true")) {
                    throw new Exception();
                }
                return Boolean.parseBoolean(args[0]);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + args[0] + " is not true or false!");
                return null;
            }
        }
    },
    STRING(1, String.class) {
        @Override
        public String stringify(CommandSender sender, Object object) {
            String str = super.stringify(sender, object);

            return (str.contains(" ") ? '"' : "") + str + (str.contains(" ") ? '"' : "");
        }

        //This has a special converter
        @Override
        public Object convert(String[] args, CommandSender sender) {
            return null;
        }
    },
    INTEGER(1, Integer.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {
            try {
                return Integer.valueOf(args[0]);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + args[0] + " is not an integer!");
                return null;
            }
        }
    },
    FLOAT(1, Float.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {
            try {
                return Double.valueOf(args[0]).floatValue();
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + args[0] + " is not a floating point number!");
                return null;
            }
        }
    },
    DOUBLE(1, Double.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {
            try {
                return Double.valueOf(args[0]);
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + args[0] + " is not a number!");
                return null;
            }
        }
    },
    LOCATION(3, ArgumentLocation.class) {
        //Special converter
        @Override
        public Object convert(String[] args, CommandSender sender) {
            return null;
        }


        @Override
        public String stringify(CommandSender sender, Object object) {

            Location loc = (Location) object;
            boolean append_rotation = loc.getYaw() == 0 && loc.getYaw() == 0;

            return loc.getX() + " " + loc.getY() + " " + loc.getZ() + (append_rotation ? " " + loc.getYaw() + " " + loc.getPitch() : "");
        }
    },
    ROTATION(2, Rotation.class) {
        @Override
        public Object convert(String[] args, CommandSender sender) {

            float yaw, pitch;

            if (args[0].equalsIgnoreCase("north")) {
                yaw = 180;
            } else if (args[0].equalsIgnoreCase("east")) {
                yaw = -90;
            } else if (args[0].equalsIgnoreCase("south")) {
                yaw = 0;
            } else if (args[0].equalsIgnoreCase("west")) {
                yaw = 90;
            } else {
                try {
                    yaw = Double.valueOf(args[0]).floatValue();
                } catch (NumberFormatException e) {
                    try {
                        yaw = Integer.parseInt(args[0]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Invalid Yaw value.");
                        return false;
                    }
                }
            }

            if (args[1].equalsIgnoreCase("up")) {
                pitch = -90;
            } else if (args[1].equalsIgnoreCase("down")) {
                pitch = 90;
            } else {
                try {
                    pitch = Double.valueOf(args[1]).floatValue();
                } catch (NumberFormatException e) {
                    try {
                        pitch = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage(ChatColor.RED + "Invalid Pitch value.");
                        return false;
                    }
                }
            }
            return new Rotation(yaw, pitch);
        }

        @Override
        public String stringify(CommandSender sender, Object object) {
            Rotation rotation = (Rotation) object;
            return rotation.yaw() + " " + rotation.pitch();
        }
    },
    PLAYER(1, Player.class) {
        @Override
        public String stringify(CommandSender sender, Object object) {
            return ((Player) object).getName();
        }

        @Override
        public Object convert(String[] args, CommandSender sender) {
            return sender.getServer().getPlayer(args[0]);
        }
    },
    TIMESTAMP(1, Long.class) {
        @Override
        public String stringify(CommandSender sender, Object object) {

            return '"' + millisToTime((Long) object) + '"';
        }


        public String millisToTime(long time) {
            long seconds = time / 1000;
            long sec = seconds % 60;
            long minutes = seconds % 3600 / 60;
            long hours = seconds % 86400 / 3600;
            long days = seconds / 86400;
            long months = seconds / (86400*30);

            StringBuilder builder = new StringBuilder();

            if (months > 0) {
                String length = months > 1 ? "months" : "month";
                builder.append(months).append(" ").append(length).append(" ");
            }

            if (days > 0) {
                String length = days > 1 ? "days" : "day";
                builder.append(days).append(" ").append(length).append(" ");
            }

            if (hours > 0) {
                String length = hours > 1 ? "hours" : "hour";
                builder.append(hours).append(" ").append(length).append(" ");
            }
            if (minutes > 0) {
                String length = minutes > 1 ? "minutes" : "minute";
                builder.append(minutes).append(" ").append(length).append(" ");
            }
            if (sec > 0) {
                String length = sec > 1 ? "seconds" : "second";
                builder.append(sec).append(" ").append(length).append(" ");
            }

            return builder.substring(0, builder.length() - 1);
        }

        @Override
        public Object convert(String[] args, CommandSender sender) {
            return null;
        }
    };
    public final int maxArgs;
    public final Class<?> clazz;

    public abstract Object convert(String[] args, CommandSender sender);

    public String stringify(CommandSender sender, Object object) {
        return object.toString();
    }

    private static final String[] PLAYER_ONLY_SYMBOLS = new String[]{"~", "^", "@"};

    public static double parseLocationValue(String symbol, String arg, CommandSender sender) throws IllegalArgumentException {

        double val = 0;
        boolean usedSymbols = false;

        if (!(sender instanceof Player) && List.of(PLAYER_ONLY_SYMBOLS).contains(arg)) {
            sender.sendMessage(ChatColor.RED + "Only players can use ~, ^ and @ ");
            throw new IllegalArgumentException();
        }


        if (sender instanceof Player bukkitPlayer) {
            if (arg.equalsIgnoreCase("~")) {
                val = bukkitPlayer.getLocation().getX();
                usedSymbols = true;
            } else if (arg.equalsIgnoreCase("^")) {
                val = bukkitPlayer.getLocation().getBlockX();
                usedSymbols = true;
            } else if (arg.equalsIgnoreCase("@")) {
                val = ((double)bukkitPlayer.getLocation().getBlockX()) + 0.5D;
                usedSymbols = true;
            }
        }

        if (!usedSymbols) {
            try {
                val = Double.parseDouble(arg);
            } catch (NumberFormatException e) {
                try {
                    val = Integer.parseInt(arg);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Invalid " + symbol + " value.");
                    throw new IllegalArgumentException();
                }
            }
        }
        return val;
    }
}
