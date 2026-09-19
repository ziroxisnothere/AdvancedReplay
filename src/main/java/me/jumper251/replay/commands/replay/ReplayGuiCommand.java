package me.jumper251.replay.commands.replay;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ReplayGuiCommand extends SubCommand {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([sm])$", Pattern.CASE_INSENSITIVE);
    private static final ConcurrentMap<UUID, PendingReplay> PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, UUID> RESERVED_BLOCKS = new ConcurrentHashMap<>();

    public ReplayGuiCommand(AbstractCommand parent) {
        super(parent, "gui", "Starts a replay by asking for its name and duration in a sign", "gui [-force]", true);
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
        cancel(player);
        PendingReplay pending = new PendingReplay(force, player.getUniqueId());
        PENDING.put(player.getUniqueId(), pending);
        openSign(player, pending);
    }

    /** Handles a submitted temporary sign without sending anything to chat. */
    public static void handleSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        PendingReplay pending = PENDING.get(player.getUniqueId());
        if (pending == null || !event.getBlock().equals(pending.block)) return;

        event.setCancelled(true);
        String answer = event.getLine(1) == null ? "" : event.getLine(1).trim();
        restoreBlock(pending);

        if (answer.equalsIgnoreCase("cancel")) {
            PENDING.remove(player.getUniqueId());
            restoreSign(pending);
            return;
        }

        if (pending.name == null) {
            if (answer.length() > 40 || !DefaultReplaySaver.isValidName(answer)) {
                showActionBar(player, Messages.REPLAY_GUI_INVALID_NAME.getMessage());
                reopen(player, pending);
                return;
            }

            pending.name = answer;
            pending.phase = Phase.DURATION;
            openSign(player, pending);
            return;
        }

        Long durationSeconds = parseDuration(answer);
        if (durationSeconds == null || durationSeconds <= 0 || durationSeconds > Integer.MAX_VALUE / 20L) {
            showActionBar(player, Messages.REPLAY_GUI_INVALID_DURATION.getMessage());
            reopen(player, pending);
            return;
        }

        PENDING.remove(player.getUniqueId());
        cancelCleanup(pending);
        restoreSign(pending);
        int durationTicks = (int) (durationSeconds * 20L);
        String name = pending.name;
        Bukkit.getScheduler().runTask(ReplaySystem.getInstance(),
                () -> startReplay(player, name, durationTicks, durationSeconds, pending.force));
    }

    public static void cancel(Player player) {
        PendingReplay pending = PENDING.remove(player.getUniqueId());
        if (pending != null) restoreSign(pending);
    }

    private static void openSign(Player player, PendingReplay pending) {
        Bukkit.getScheduler().runTask(ReplaySystem.getInstance(), () -> {
            if (!player.isOnline() || PENDING.get(player.getUniqueId()) != pending) return;

            if (pending.block == null) {
                pending.block = findTemporaryBlock(player, player.getUniqueId(), pending);
                if (pending.block == null) {
                    PENDING.remove(player.getUniqueId(), pending);
                    showActionBar(player, "Could not open the Replay input right now. Try again.");
                    return;
                }
                pending.originalData = pending.block.getBlockData().clone();
            } else {
                pending.block.setBlockData(pending.originalData, false);
            }

            pending.block.setType(Material.OAK_SIGN, false);
            Sign sign = (Sign) pending.block.getState();
            sign.setLine(0, ChatColor.translateAlternateColorCodes('&', pending.phase == Phase.NAME
                    ? Messages.REPLAY_GUI_ENTER_NAME.getMessage()
                    : Messages.REPLAY_GUI_ENTER_DURATION.getMessage()));
            sign.setLine(1, "");
            sign.setLine(2, "");
            sign.setLine(3, "");
            sign.update(false, false);
            player.openSign(sign);
            scheduleCleanup(player, pending);
        });
    }

    private static void reopen(Player player, PendingReplay pending) {
        Bukkit.getScheduler().runTaskLater(ReplaySystem.getInstance(), () -> openSign(player, pending), 1L);
    }

    private static Block findTemporaryBlock(Player player, UUID playerId, PendingReplay pending) {
        Location base = player.getLocation().getBlock().getLocation();
        int[][] offsets = {
                {0, -1, 0}, {0, 0, 0}, {1, -1, 0}, {-1, -1, 0},
                {0, -1, 1}, {0, -1, -1}, {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1}
        };

        for (int[] offset : offsets) {
            Block candidate = base.clone().add(offset[0], offset[1], offset[2]).getBlock();
            if (!candidate.getType().isAir() && !candidate.isPassable()) continue;

            String key = blockKey(candidate);
            if (RESERVED_BLOCKS.putIfAbsent(key, playerId) == null) {
                pending.blockKey = key;
                return candidate;
            }
        }

        return null;
    }

    private static void restoreSign(PendingReplay pending) {
        cancelCleanup(pending);
        restoreBlock(pending);
        if (pending.blockKey != null) {
            RESERVED_BLOCKS.remove(pending.blockKey, pending.owner);
            pending.blockKey = null;
        }
    }

    private static void restoreBlock(PendingReplay pending) {
        if (pending.block != null && pending.originalData != null
                && pending.block.getType() == Material.OAK_SIGN) {
            pending.block.setBlockData(pending.originalData, false);
        }
    }

    private static String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static void scheduleCleanup(Player player, PendingReplay pending) {
        cancelCleanup(pending);
        pending.cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (PENDING.remove(player.getUniqueId(), pending)) restoreSign(pending);
            }
        }.runTaskLater(ReplaySystem.getInstance(), 20L * 120L);
    }

    private static void cancelCleanup(PendingReplay pending) {
        if (pending.cleanupTask != null) {
            pending.cleanupTask.cancel();
            pending.cleanupTask = null;
        }
    }

    private static void showActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message)));
    }

    private static void startReplay(Player player, String name, int durationTicks, long durationSeconds, boolean force) {
        if (!player.isOnline()) return;

        if (ReplayHelper.replaySessions.containsKey(player.getName())) {
            showActionBar(player, Messages.REPLAY_GUI_ALREADY_WATCHING.getMessage());
            return;
        }
        if (ReplayManager.activeReplays.containsKey(name)) {
            showActionBar(player, Messages.REPLAY_GUI_ACTIVE_EXISTS.getMessage());
            return;
        }
        if (ReplaySaver.exists(name) && !force) {
            showActionBar(player, Messages.REPLAY_GUI_SAVED_EXISTS.getMessage());
            return;
        }

        if (force) ReplaySaver.delete(name);

        // Cast explicitly to select the CommandSender overload and preserve the player as creator.
        ReplayAPI.getInstance().recordReplay(name, (CommandSender) player, player);
        showActionBar(player, Messages.REPLAY_GUI_STARTED
                .arg("replay", name)
                .arg("duration", durationSeconds)
                .build());

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

    private enum Phase {
        NAME,
        DURATION
    }

    private static final class PendingReplay {
        private final boolean force;
        private final UUID owner;
        private final Phase initialPhase = Phase.NAME;
        private Phase phase = initialPhase;
        private String name;
        private Block block;
        private BlockData originalData;
        private BukkitTask cleanupTask;
        private String blockKey;

        private PendingReplay(boolean force, UUID owner) {
            this.force = force;
            this.owner = owner;
        }
    }
}
