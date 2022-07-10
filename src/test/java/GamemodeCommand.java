import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.zoda.api.command.ACommand;
import net.zoda.api.command.Command;
import net.zoda.api.command.CommandShortcut;
import net.zoda.api.command.DefaultRun;
import net.zoda.api.command.argument.Argument;
import net.zoda.api.command.argument.ArgumentType;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Map;

@Command(name = "gamemode",playerOnly = true,permissions = "bukkit.gamemode",aliases = "gm")
public class GamemodeCommand implements ACommand {

    @CommandShortcut(value = "default",preRunName = "gms")
    private final Map<String,Object> survivalPreRun = Map.of("gamemode",GameMode.SURVIVAL);

    @CommandShortcut(value = "default",preRunName = "gmc")
    private final Map<String,Object> creativePreRun = Map.of("gamemode",GameMode.CREATIVE);

    @CommandShortcut(value = "default",preRunName = "gma")
    private final Map<String,Object> adventurePreRun = Map.of("gamemode",GameMode.ADVENTURE);

    @CommandShortcut(value = "default",preRunName = "gmsp")
    private final Map<String,Object> spectatorPreRun = Map.of("gamemode",GameMode.SPECTATOR);

    private final Class<GameMode> gamemode = GameMode.class;

    @DefaultRun(arguments = {
            @Argument(name = "gamemode",type = ArgumentType.ENUM),
            @Argument(name = "target",type=ArgumentType.PLAYER,required = false)
    })
    public void run(Player player, GameMode gameMode,Player target) {
        Player p = target == null ? player : target;
        
        p.setGameMode(gameMode);
        p.sendMessage(Component.text("Your game mode has been set to: "+gameMode.name())
                                    .color(NamedTextColor.GREEN));
    }
}
