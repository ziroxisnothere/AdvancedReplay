package me.jumper251.replay.commands.replay;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.ReplayAPI;
import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.saving.DefaultReplaySaver;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.replaysystem.replaying.ReplayHelper;
import me.jumper251.replay.utils.ReplayManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class ReplayGuiCommand extends SubCommand {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([sm])$", Pattern.CASE_INSENSITIVE);
    private static final ConcurrentMap<UUID, PendingReplay> PENDING = new ConcurrentHashMap<>();

    public ReplayGuiCommand(AbstractCommand parent) {
        super(parent, "gui", "Starts a replay by asking for its name and duration in chat", "gui", true);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        begin((Player) sender);
        return true;
    }

    public static void begin(Player player) {
        PENDING.put(player.getUniqueId(), new PendingReplay());
        player.sendMessage(ReplaySystem.PREFIX + "Enter the Replay name in chat (or type cancel):");
    }

    /**
     * Handles a chat message for an active /replay gui flow.
     * This method is called from the asynchronous chat event and schedules all Bukkit/API work back on the main thread.
     */
    public static boolean handleChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingReplay pending = PENDING.get(player.getUniqueId());
        if (pending == null) return false;

        event.setCancelled(true);
        String answer = event.getMessage().trim();

        if (answer.equalsIgnoreCase("cancel")) {
            PENDING.remove(player.getUniqueId());
            player.sendMessage(ReplaySystem.PREFIX + "Replay GUI setup cancelled.");
            return true;
        }

        if (pending.name == null) {
            if (answer.length() > 40 || !DefaultReplaySaver.isValidName(answer)) {
                player.sendMessage(ReplaySystem.PREFIX + "Invalid name. Use only letters, numbers, '.', '-' or '_', max 40 characters.");
                return true;
            }

            pending.name = answer;
            player.sendMessage(ReplaySystem.PREFIX + "Enter the duration (for example: 120m or 60s):");
            return true;
        }

        Long durationSeconds = parseDuration(answer);
        if (durationSeconds == null || durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20L) {
            player.sendMessage(ReplaySystem.PREFIX + "Invalid duration. Use a positive value ending in s or m, for example 60s or 120m.");
            return true;
        }

        PENDING.remove(player.getUniqueId());
        String name = pending.name;
        int durationTicks = (int) (durationSeconds * 20L);

        Bukkit.getScheduler().runTask(ReplaySystem.getInstance(), () -> startReplay(player, name, durationTicks, durationSeconds));
        return true;
    }

    public static void cancel(Player player) {
        PENDING.remove(player.getUniqueId());
    }

    private static void startReplay(Player player, String name, int durationTicks, long durationSeconds) {
        if (!player.isOnline()) return;

        if (ReplayHelper.replaySessions.containsKey(player.getName())) {
            player.sendMessage(ReplaySystem.PREFIX + "You are already watching a Replay.");
            return;
        }
        if (ReplayManager.activeReplays.containsKey(name)) {
            player.sendMessage(ReplaySystem.PREFIX + "A Replay with that name is already being recorded.");
            return;
        }
        if (ReplaySaver.exists(name)) {
            player.sendMessage(ReplaySystem.PREFIX + "A saved Replay with that name already exists.");
            return;
        }

        ReplayAPI.getInstance().recordReplay(name, player, player);
        player.sendMessage(ReplaySystem.PREFIX + "Started recording " + name + " for " + durationSeconds + " seconds.");

        new BukkitRunnable() {
            @Override
            public void run() {
                ReplayAPI.getInstance().stopReplay(name, true, true);
            }
        }.runTaskLater(ReplaySystem.getInstance(), durationTicks);
    }

    private static Long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) return null;

        try {
            long amount = Long.parseLong(matcher.group(1));
            return matcher.group(2).equalsIgnoreCase("m") ? Math.multiplyExact(amount, 60L) : amount;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    private static final class PendingReplay {
        private String name;
    }
}
