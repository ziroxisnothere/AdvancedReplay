package me.jumper251.replay.filesystem.saving;


import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.replaysystem.Replay;
import me.jumper251.replay.replaysystem.data.ReplayData;
import me.jumper251.replay.utils.LogUtils;
import me.jumper251.replay.utils.fetcher.Acceptor;
import me.jumper251.replay.utils.fetcher.Consumer;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class DefaultReplaySaver implements IReplaySaver {

    public final static File DIR = new File(ReplaySystem.getInstance().getDataFolder() + "/replays/");

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private boolean reformatting;

    private ExecutorService pool = Executors.newCachedThreadPool();

    public static boolean isValidName(String replayName) {
        return NAME_PATTERN.matcher(replayName).matches();
    }

    @Override
    public void saveReplay(Replay replay) {

        if (!DIR.exists()) DIR.mkdirs();

        File file = new File(DIR, storageName(replay.getData().getCreator(), replay.getId()) + ".replay");


        try {
            if (!file.exists()) file.createNewFile();

            try (FileOutputStream fileOut = new FileOutputStream(file);
                 GZIPOutputStream gOut = new GZIPOutputStream(fileOut);
                 ObjectOutputStream objectOut = new ObjectOutputStream(gOut)) {

                objectOut.writeObject(replay.getData());
                objectOut.flush();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void loadReplay(String replayName, Consumer<Replay> consumer) {

        this.pool.execute(new Acceptor<Replay>(consumer) {

            @Override
            public Replay getValue() {

                File file = findReplayFile(replayName);

                try (FileInputStream fileIn = new FileInputStream(file);
                     GZIPInputStream gIn = new GZIPInputStream(fileIn);
                     ObjectInputStream objectIn = new ObjectInputStream(gIn)) {

                    ReplayData data = (ReplayData) objectIn.readObject();

                    return new Replay(replayName, data);

                } catch (ClassNotFoundException | IOException e) {
                    if (!reformatting) e.printStackTrace();
                }

                return null;
            }
        });
    }

    @Override
    public boolean replayExists(String replayName) {
        if (!isValidName(replayName)) return false;

        return findReplayFile(replayName) != null;
    }

    @Override
    public void deleteReplay(String replayName) {
        File file = findReplayFile(replayName);

        if (file != null) file.delete();
    }

    public void reformatAll() {
        this.reformatting = true;
        if (DIR.exists()) {
            Arrays.stream(DIR.listFiles())
                    .filter(file -> (file.isFile() && file.getName().endsWith(".replay")))
                    .map(File::getName)
                    .collect(Collectors.toList())
                    .forEach(file -> reformat(file.replaceAll("\\.replay", "")));
        }

        this.reformatting = false;
    }

    private void reformat(String replayName) {
        loadReplay(replayName, old -> {

            if (old == null) {
                LogUtils.log("Reformatting: " + replayName);

                try {
                    File file = findReplayFile(replayName);

                    FileInputStream fileIn = new FileInputStream(file);
                    ObjectInputStream objectIn = new ObjectInputStream(fileIn);

                    ReplayData data = (ReplayData) objectIn.readObject();

                    objectIn.close();
                    fileIn.close();

                    deleteReplay(replayName);
                    saveReplay(new Replay(replayName, data));

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
    }


    @Override
    public List<String> getReplays() {
        List<String> files = new ArrayList<>();

        if (DIR.exists()) {
            for (File file : DIR.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".replay")) {
                    String replayName = file.getName().replaceAll("\\.replay$", "");
                    int separator = replayName.indexOf('-');
                    files.add(separator >= 0 ? replayName.substring(separator + 1) : replayName);
                }
            }
        }
        return files;
    }

    public List<String> getReplaysForCreator(String creator) {
        List<String> files = new ArrayList<>();
        if (creator == null || !DIR.exists()) return files;

        String prefix = creator + "-";
        File[] replayFiles = DIR.listFiles((dir, fileName) ->
                fileName.startsWith(prefix) && fileName.endsWith(".replay"));
        if (replayFiles == null) return files;

        for (File file : replayFiles) {
            String storedName = file.getName().replaceAll("\\.replay$", "");
            files.add(storedName.substring(prefix.length()));
        }
        return files;
    }

    private static String storageName(String creator, String replayName) {
        String safeCreator = creator == null || creator.isBlank() ? "CONSOLE" : creator;
        return safeCreator + "-" + replayName;
    }

    private File findReplayFile(String replayName) {
        if (!isValidName(replayName) || !DIR.exists()) return null;

        File legacyFile = new File(DIR, replayName + ".replay");
        if (legacyFile.isFile()) return legacyFile;

        File[] files = DIR.listFiles((dir, fileName) ->
                fileName.endsWith("-" + replayName + ".replay"));
        return files != null && files.length > 0 ? files[0] : null;
    }

}
