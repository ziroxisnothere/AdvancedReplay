package me.jumper251.replay.commands.replay;

import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.utils.ReplayVisibility;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReplayVisibilityCommand extends SubCommand {
    public ReplayVisibilityCommand(AbstractCommand parent) {
        super(parent, "visibility", "Changes Replay visibility", "visibility <Name> <Public|Private>", true);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 3) return false;
        Player player = (Player) sender;
        String name = args[1];
        boolean visible;
        if (args[2].equalsIgnoreCase("public")) visible = true;
        else if (args[2].equalsIgnoreCase("private")) visible = false;
        else return false;

        String key = ReplayVisibility.key(player.getName(), name);
        if (!ReplaySaver.exists(key)) {
            player.sendMessage("§bReplay §cReplay not found.");
            return true;
        }
        ReplayVisibility.setPublic(key, visible);
        player.sendMessage("§bReplay §7Replay §e" + name + " §7is now " + (visible ? "§aPublic" : "§cPrivate") + "§7.");
        return true;
    }
}
