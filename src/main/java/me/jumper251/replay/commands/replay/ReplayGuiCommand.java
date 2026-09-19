package me.jumper251.replay.commands.replay;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.api.ReplayAPI;
import me.jumper251.replay.commands.AbstractCommand;
import me.jumper251.replay.commands.SubCommand;
import me.jumper251.replay.filesystem.Messages;
import me.jumper251.replay.filesystem.saving.DatabaseReplaySaver;
import me.jumper251.replay.filesystem.saving.DefaultReplaySaver;
import me.jumper251.replay.filesystem.saving.ReplaySaver;
import me.jumper251.replay.replaysystem.replaying.ReplayHelper;
import me.jumper251.replay.utils.ReplayManager;
import me.jumper251.replay.utils.ReplayVisibility;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

public class ReplayGuiCommand extends SubCommand {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([sm])$", Pattern.CASE_INSENSITIVE);
    private static final String LIST_TITLE = "§8Your Replays";
    private static final String DELETE_TITLE = "§8Delete Replay?";
    private static final String VISIBILITY_TITLE = "§8Replay Visibility";
    private static final int CREATE_SLOT = 49;
    private static final int CANCEL_DELETE_SLOT = 11;
    private static final int CONFIRM_DELETE_SLOT = 15;
    private static final int PRIVATE_SLOT = 11;
    private static final int PUBLIC_SLOT = 15;

    private static final ConcurrentMap<UUID, PendingReplay> PENDING = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Map<Integer, String>> LIST_ITEMS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> DELETE_ITEMS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, String> VISIBILITY_ITEMS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, Boolean> FORCE_MODE = new ConcurrentHashMap<>();

    public ReplayGuiCommand(AbstractCommand parent) {
        super(parent, "gui", "Shows your Replays", "gui", true);
    }

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 2) return false;
        boolean force = args.length == 2 && args[1].equalsIgnoreCase("-force");
        if (args.length == 2 && !force) return false;
        FORCE_MODE.put(((Player) sender).getUniqueId(), force);
        openList((Player) sender);
        return true;
    }

    @Override
    public List<String> onTab(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }

    public static void openList(Player player) {
        Map<String, String> replays = getVisibleReplays(player.getName());
        Inventory inventory = Bukkit.createInventory(null, 54, LIST_TITLE);
        Map<Integer, String> items = new HashMap<>();

        int slot = 0;
        for (Map.Entry<String, String> entry : replays.entrySet()) {
            if (slot >= 45) break;
            String key = entry.getKey();
            String replay = entry.getValue();
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + replay);
            meta.setLore(Arrays.asList("§7Left click: §aPlay", "§7Right click: §bVisibility", "§7Press Q: §cRemove"));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            items.put(slot, key);
            slot++;
        }

        ItemStack create = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = create.getItemMeta();
        createMeta.setDisplayName("§aCreate Replay");
        createMeta.setLore(Collections.singletonList("§7Click to record a new Replay"));
        create.setItemMeta(createMeta);
        inventory.setItem(CREATE_SLOT, create);

        LIST_ITEMS.put(player.getUniqueId(), items);
        player.openInventory(inventory);
    }

    public static boolean handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return false;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        if (LIST_TITLE.equals(title)) {
            event.setCancelled(true);
            if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return true;

            if (event.getRawSlot() == CREATE_SLOT) {
                player.closeInventory();
                begin(player, FORCE_MODE.getOrDefault(player.getUniqueId(), false));
                return true;
            }

            String replay = LIST_ITEMS.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(event.getRawSlot());
            if (replay == null) return true;

            if (event.getClick() == ClickType.DROP) {
                openDeleteConfirmation(player, replay);
            } else if (event.getClick() == ClickType.RIGHT) {
                openVisibilityMenu(player, replay);
            } else if (event.getClick() == ClickType.LEFT) {
                player.closeInventory();
                play(player, replay);
            }
            return true;
        }

        if (DELETE_TITLE.equals(title)) {
            event.setCancelled(true);
            if (event.getRawSlot() == CANCEL_DELETE_SLOT) {
                DELETE_ITEMS.remove(player.getUniqueId());
                openList(player);
            } else if (event.getRawSlot() == CONFIRM_DELETE_SLOT) {
                String replay = DELETE_ITEMS.remove(player.getUniqueId());
                if (replay != null) {
                    ReplaySaver.delete(replay);
                    ReplayVisibility.remove(replay);
                    openList(player);
                }
            }
            return true;
        }

        if (VISIBILITY_TITLE.equals(title)) {
            event.setCancelled(true);
            String key = VISIBILITY_ITEMS.get(player.getUniqueId());
            if (key != null && (event.getRawSlot() == PRIVATE_SLOT || event.getRawSlot() == PUBLIC_SLOT)) {
                ReplayVisibility.setPublic(key, event.getRawSlot() == PUBLIC_SLOT);
                VISIBILITY_ITEMS.remove(player.getUniqueId());
                openList(player);
            }
            return true;
        }

        return false;
    }

    private static void openDeleteConfirmation(Player player, String replay) {
        Inventory inventory = Bukkit.createInventory(null, 27, DELETE_TITLE);
        inventory.setItem(CANCEL_DELETE_SLOT, button(Material.RED_STAINED_GLASS_PANE, "§cCancel"));
        inventory.setItem(13, button(Material.PAPER, "§e" + replay));
        inventory.setItem(CONFIRM_DELETE_SLOT, button(Material.GREEN_STAINED_GLASS_PANE, "§aDelete"));
        DELETE_ITEMS.put(player.getUniqueId(), replay);
        player.openInventory(inventory);
    }

    private static void openVisibilityMenu(Player player, String key) {
        Inventory inventory = Bukkit.createInventory(null, 27, VISIBILITY_TITLE);
        inventory.setItem(PRIVATE_SLOT, button(Material.RED_CONCRETE, "§cPrivate"));
        inventory.setItem(13, button(Material.PAPER, "§e" + displayName(key)));
        inventory.setItem(PUBLIC_SLOT, button(Material.GREEN_CONCRETE, "§aPublic"));
        VISIBILITY_ITEMS.put(player.getUniqueId(), key);
        player.openInventory(inventory);
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static Map<String, String> getVisibleReplays(String playerName) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (ReplaySaver.replaySaver instanceof DefaultReplaySaver) {
            DefaultReplaySaver saver = (DefaultReplaySaver) ReplaySaver.replaySaver;
            for (String key : saver.getReplayKeysForCreator(playerName)) result.put(key, displayName(key));
            for (String key : saver.getPublicReplayKeys(playerName)) result.put(key, displayName(key));
            return result;
        }

        if (ReplaySaver.replaySaver instanceof DatabaseReplaySaver) {
            Map<String, String> databaseResult = new java.util.LinkedHashMap<>();
            DatabaseReplaySaver.replayCache.values().stream()
                    .filter(info -> playerName.equals(info.getCreator()) || ReplayVisibility.isPublic(info.getID()))
                    .forEach(info -> databaseResult.put(info.getID(), info.getID()));
            return databaseResult;
        }

        for (String key : ReplaySaver.getReplays()) result.put(key, key);
        return result;
    }

    private static void play(Player player, String name) {
        if (ReplayHelper.replaySessions.containsKey(player.getName())) return;
        ReplayAPI.getInstance().playReplay(name, player);
    }

    private static String displayName(String key) {
        int separator = key.indexOf('-');
        return separator >= 0 ? key.substring(separator + 1) : key;
    }

    public static void begin(Player player) {
        begin(player, false);
    }

    private static void begin(Player player, boolean force) {
        PENDING.put(player.getUniqueId(), new PendingReplay(force));
        Messages.REPLAY_GUI_ENTER_NAME.send(player);
    }

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
        int durationTicks = (int) (durationSeconds * 20L);
        String name = pending.name;
        Bukkit.getScheduler().runTask(ReplaySystem.getInstance(),
                () -> startReplay(player, name, durationTicks, durationSeconds, pending.force));
        return true;
    }

    public static void cancel(Player player) {
        PENDING.remove(player.getUniqueId());
        LIST_ITEMS.remove(player.getUniqueId());
        DELETE_ITEMS.remove(player.getUniqueId());
        VISIBILITY_ITEMS.remove(player.getUniqueId());
        FORCE_MODE.remove(player.getUniqueId());
    }

    private static void startReplay(Player player, String name, int durationTicks, long durationSeconds, boolean force) {
        if (!player.isOnline()) return;
        if (ReplayHelper.replaySessions.containsKey(player.getName())) return;
        String storageKey = ReplayVisibility.key(player.getName(), name);
        if (ReplayManager.activeReplays.containsKey(storageKey)) {
            Messages.REPLAY_GUI_ACTIVE_EXISTS.send(player);
            return;
        }
        if (ReplaySaver.exists(storageKey) && !force) {
            Messages.REPLAY_GUI_SAVED_EXISTS.send(player);
            return;
        }
        if (force) ReplaySaver.delete(storageKey);

        ReplayAPI.getInstance().recordReplay(storageKey, (CommandSender) player, player);
        Messages.REPLAY_GUI_STARTED.arg("replay", name).arg("duration", durationSeconds).send(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                ReplayAPI.getInstance().stopReplay(storageKey, true, true);
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
        private final boolean force;
        private String name;

        private PendingReplay(boolean force) {
            this.force = force;
        }
    }
}
