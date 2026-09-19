package me.jumper251.replay.commands.replay;

import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class ReplayVersionCommand extends SubCommand {

    public ReplayVersionCommand(AbstractCommand parent) {
        super(parent, "version", "Shows the plugin version", "version", false);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return false;
        sender.sendMessage("§bMoonXReplay by §eZirox");
        return true;
    }
}
