package me.jumper251.replay.commands.replay;

import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.ReplayAPI;
import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.Messages;
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
        super(parent, "gui", "Starts a replay by asking for its name and duration in chat", "gui [-force]", true);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 2) return false;

        boolean force = args.length == 2 && args[1].equalsIgnoreCase("-force");
        if (args.length == 2 && !force) return false;

        begin((Player) sender, force);
        return true;
    }

    @Override
    public List<String> onTab(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 2 ? Arrays.asList("-force") : null;
    }

    public static void begin(Player player) {
        begin(player, false);
    }

    private static void begin(Player player, boolean force) {
        PENDING.put(player.getUniqueId(), new PendingReplay(force));
        Messages.REPLAY_GUI_ENTER_NAME.send(player);
        if (force) {
            Messages.REPLAY_GUI_FORCE.send(player);
        }
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
            Messages.REPLAY_GUI_CANCELLED.send(player);
            return true;
        }

        if (pending.name == null) {
            if (answer.length() > 40 || !DefaultReplaySaver.isValidName(answer)) {
                Messages.REPLAY_GUI_INVALID_NAME.send(player);
                return true;
            }

            pending.name = answer;
            Messages.REPLAY_GUI_ENTER_DURATION.send(player);
            return true;
        }

        Long durationSeconds = parseDuration(answer);
        if (durationSeconds == null || durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20L) {
            Messages.REPLAY_GUI_INVALID_DURATION.send(player);
            return true;
        }

        PENDING.remove(player.getUniqueId());
        String name = pending.name;
        int durationTicks = (int) (durationSeconds * 20L);

        Bukkit.getScheduler().runTask(ReplaySystem.getInstance(), () -> startReplay(player, name, durationTicks, durationSeconds, pending.force));
        return true;
    }

    public static void cancel(Player player) {
        PENDING.remove(player.getUniqueId());
    }

    private static void startReplay(Player player, String name, int durationTicks, long durationSeconds, boolean force) {
        if (!player.isOnline()) return;

        if (ReplayHelper.replaySessions.containsKey(player.getName())) {
            Messages.REPLAY_GUI_ALREADY_WATCHING.send(player);
            return;
        }
        if (ReplayManager.activeReplays.containsKey(name)) {
            Messages.REPLAY_GUI_ACTIVE_EXISTS.send(player);
            return;
        }
        if (ReplaySaver.exists(name) && !force) {
            Messages.REPLAY_GUI_SAVED_EXISTS.send(player);
            return;
        }

        if (force) {
            ReplaySaver.delete(name);
        }

        // Cast explicitly to select the CommandSender overload; otherwise
        // Java selects recordReplay(String, Player...) and the creator becomes null.
        ReplayAPI.getInstance().recordReplay(name, (CommandSender) player, player);
        Messages.REPLAY_GUI_STARTED.arg("replay", name).arg("duration", durationSeconds).send(player);

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
		private final boolean force;

		private PendingReplay(boolean force) {
			this.force = force;
		}
    }
}
