package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.model.Playlist;
import net.dvmn2.soundmanagerplugin.model.PlaylistTrack;
import net.dvmn2.soundmanagerplugin.model.RunState;
import net.dvmn2.soundmanagerplugin.storage.PlaylistStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Owns the playlists of the server, their data file and the running playlists.
 * <p>
 * Main thread only. Playlists are stored in {@code playlists.json}: changes only mark the data dirty, a periodic
 * task serialises a snapshot on the main thread and writes it asynchronously, and {@link #shutdown()} writes the
 * final state synchronously. A playlist cannot be edited or deleted while it runs. Players who may see playlists
 * and have a mod that supports them receive every change through {@link PlaylistNetwork}.
 */
public final class PlaylistManager {

    /** Most playlists on the server. */
    public static final int MAX_PLAYLISTS = 50;

    /** Save period, ticks (30 seconds). */
    private static final long SAVE_INTERVAL_TICKS = 600L;

    /** Outcome of creating, deleting and editing a playlist. */
    public enum EditResult { OK, NOT_FOUND, EXISTS, BAD_NAME, LIMIT, TRACK_LIMIT, RUNNING, BAD_INDEX }

    /** Outcome of starting a playlist. */
    public enum StartResult { STARTED, NOT_FOUND, EMPTY, NO_PLAYERS }

    /** Outcome of stop, next, pause and resume. */
    public enum RunResult { OK, NOT_FOUND, NOT_RUNNING, WRONG_STATE }

    /**
     * Result of {@link #start(String, Collection)}.
     *
     * @param players players who hear the playlist
     * @param skipped targets skipped because they have no mod
     */
    public record StartOutcome(StartResult result, int players, int skipped) {
    }

    private final SoundManagerPlugin plugin;
    private final SoundManagerApi api;
    private final PlaylistNetwork network;
    private final Path file;
    private final Map<String, Playlist> playlists = new LinkedHashMap<>();
    private final Map<String, PlaylistRun> runs = new HashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean writing = new AtomicBoolean();

    @Nullable
    private BukkitTask saveTask;

    public PlaylistManager(SoundManagerPlugin plugin, SoundManagerApi api, PlaylistNetwork network) {
        this.plugin = plugin;
        this.api = api;
        this.network = network;
        this.file = plugin.getDataFolder().toPath().resolve(PlaylistStore.FILE_NAME);
    }

    // ---------- Lifecycle ----------

    /** Reads the data file. Call once, before {@link #start()}. */
    public void load() {
        playlists.clear();
        for (Playlist playlist : PlaylistStore.load(file, MAX_PLAYLISTS, plugin.getLogger())) {
            playlists.put(key(playlist.getName()), playlist);
        }
    }

    /** Starts the periodic save. */
    public void start() {
        saveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::saveAsyncIfDirty, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
    }

    /** Stops every run, cancels the periodic save and writes the data synchronously. */
    public void shutdown() {
        BukkitTask task = saveTask;
        saveTask = null;
        if (task != null) {
            task.cancel();
        }
        for (PlaylistRun run : new ArrayList<>(runs.values())) {
            run.stop();
        }
        runs.clear();
        if (dirty.getAndSet(false)) {
            try {
                PlaylistStore.writeAtomically(file, PlaylistStore.toBytes(playlists.values()));
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save " + PlaylistStore.FILE_NAME + ": " + ex.getMessage());
            }
        }
    }

    // ---------- Queries ----------

    /** The playlists in creation order; a copy. */
    public List<Playlist> list() {
        return new ArrayList<>(playlists.values());
    }

    @Nullable
    public Playlist find(String name) {
        return playlists.get(key(name));
    }

    public RunState getState(Playlist playlist) {
        PlaylistRun run = runs.get(key(playlist.getName()));
        return run == null ? RunState.STOPPED : run.getState();
    }

    public int getCurrent(Playlist playlist) {
        PlaylistRun run = runs.get(key(playlist.getName()));
        return run == null ? -1 : run.getCurrent();
    }

    // ---------- Editing ----------

    public EditResult create(String name) {
        if (!Playlist.isValidName(name)) {
            return EditResult.BAD_NAME;
        }
        if (playlists.containsKey(key(name))) {
            return EditResult.EXISTS;
        }
        if (playlists.size() >= MAX_PLAYLISTS) {
            return EditResult.LIMIT;
        }
        Playlist playlist = new Playlist(name);
        playlists.put(key(name), playlist);
        changed(playlist);
        return EditResult.OK;
    }

    public EditResult delete(String name) {
        Playlist playlist = find(name);
        if (playlist == null) {
            return EditResult.NOT_FOUND;
        }
        if (runs.containsKey(key(name))) {
            return EditResult.RUNNING;
        }
        playlists.remove(key(name));
        dirty.set(true);
        recipients().forEach(player -> network.sendRemoved(player, playlist.getName()));
        return EditResult.OK;
    }

    public EditResult addTrack(String name, PlaylistTrack track) {
        EditResult check = editable(name);
        if (check != EditResult.OK) {
            return check;
        }
        Playlist playlist = playlists.get(key(name));
        if (!playlist.addTrack(track)) {
            return EditResult.TRACK_LIMIT;
        }
        changed(playlist);
        return EditResult.OK;
    }

    /** Removes a track by its one-based number. */
    public EditResult removeTrack(String name, int number) {
        EditResult check = editable(name);
        if (check != EditResult.OK) {
            return check;
        }
        Playlist playlist = playlists.get(key(name));
        if (!playlist.removeTrack(number - 1)) {
            return EditResult.BAD_INDEX;
        }
        changed(playlist);
        return EditResult.OK;
    }

    /** Moves a track between one-based numbers. */
    public EditResult moveTrack(String name, int from, int to) {
        EditResult check = editable(name);
        if (check != EditResult.OK) {
            return check;
        }
        Playlist playlist = playlists.get(key(name));
        if (!playlist.moveTrack(from - 1, to - 1)) {
            return EditResult.BAD_INDEX;
        }
        changed(playlist);
        return EditResult.OK;
    }

    /** Changes the loop switch; allowed while the playlist runs. */
    public EditResult setLoop(String name, boolean loop) {
        Playlist playlist = find(name);
        if (playlist == null) {
            return EditResult.NOT_FOUND;
        }
        playlist.setLoop(loop);
        changed(playlist);
        return EditResult.OK;
    }

    /** Changes the shuffle switch; allowed while the playlist runs, applies from the next cycle. */
    public EditResult setShuffle(String name, boolean shuffle) {
        Playlist playlist = find(name);
        if (playlist == null) {
            return EditResult.NOT_FOUND;
        }
        playlist.setShuffle(shuffle);
        changed(playlist);
        return EditResult.OK;
    }

    // ---------- Running ----------

    /**
     * Starts a playlist for the players who have the mod, replacing a run of the same playlist. The set of
     * players is fixed: players who leave drop out, nobody joins.
     */
    public StartOutcome start(String name, Collection<Player> targets) {
        Playlist playlist = find(name);
        if (playlist == null) {
            return new StartOutcome(StartResult.NOT_FOUND, 0, 0);
        }
        if (playlist.getTracks().isEmpty()) {
            return new StartOutcome(StartResult.EMPTY, 0, 0);
        }
        List<UUID> listeners = new ArrayList<>();
        for (Player player : targets) {
            if (api.hasMod(player)) {
                listeners.add(player.getUniqueId());
            }
        }
        int skipped = targets.size() - listeners.size();
        if (listeners.isEmpty()) {
            return new StartOutcome(StartResult.NO_PLAYERS, 0, skipped);
        }
        PlaylistRun previous = runs.remove(key(name));
        if (previous != null) {
            previous.stop();
        }
        PlaylistRun run = new PlaylistRun(plugin, api, playlist, this::pushState, this::onRunFinished);
        runs.put(key(name), run);
        run.start(listeners);
        return new StartOutcome(StartResult.STARTED, listeners.size(), skipped);
    }

    public RunResult stop(String name) {
        return control(name, PlaylistRun::stop);
    }

    public RunResult next(String name) {
        return control(name, PlaylistRun::next);
    }

    public RunResult pause(String name) {
        return controlChecked(name, PlaylistRun::pause);
    }

    public RunResult resume(String name) {
        return controlChecked(name, PlaylistRun::resume);
    }

    /** Removes a player who left from every run. */
    public void onQuit(UUID id) {
        for (PlaylistRun run : new ArrayList<>(runs.values())) {
            run.removePlayer(id);
        }
    }

    // ---------- Sync with the mod ----------

    /** Sends the complete state to one player, replacing what the mod knows. Does nothing without permission. */
    public void sendSnapshot(Player player) {
        if (!PlaylistNetwork.canReceive(player)) {
            return;
        }
        network.sendReset(player);
        for (Playlist playlist : playlists.values()) {
            network.sendPlaylist(player, playlist, getState(playlist), getCurrent(playlist));
        }
    }

    // ---------- Helpers ----------

    private EditResult editable(String name) {
        if (!playlists.containsKey(key(name))) {
            return EditResult.NOT_FOUND;
        }
        return runs.containsKey(key(name)) ? EditResult.RUNNING : EditResult.OK;
    }

    private RunResult control(String name, Consumer<PlaylistRun> action) {
        PlaylistRun run = runs.get(key(name));
        if (run == null) {
            return find(name) == null ? RunResult.NOT_FOUND : RunResult.NOT_RUNNING;
        }
        action.accept(run);
        return RunResult.OK;
    }

    private RunResult controlChecked(String name, Predicate<PlaylistRun> action) {
        PlaylistRun run = runs.get(key(name));
        if (run == null) {
            return find(name) == null ? RunResult.NOT_FOUND : RunResult.NOT_RUNNING;
        }
        return action.test(run) ? RunResult.OK : RunResult.WRONG_STATE;
    }

    /** Marks the data dirty and sends the changed playlist to the players who see playlists. */
    private void changed(Playlist playlist) {
        dirty.set(true);
        RunState state = getState(playlist);
        int current = getCurrent(playlist);
        recipients().forEach(player -> network.sendPlaylist(player, playlist, state, current));
    }

    private void pushState(PlaylistRun run) {
        Playlist playlist = run.getPlaylist();
        RunState state = run.getState();
        int current = run.getCurrent();
        recipients().forEach(player -> network.sendState(player, playlist.getName(), state, current));
    }

    private void onRunFinished(PlaylistRun run) {
        runs.remove(key(run.getPlaylist().getName()), run);
    }

    private List<Player> recipients() {
        List<Player> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (PlaylistNetwork.canReceive(player)) {
                result.add(player);
            }
        }
        return result;
    }

    private void saveAsyncIfDirty() {
        if (!dirty.get() || !writing.compareAndSet(false, true)) {
            return;
        }
        dirty.set(false);
        byte[] bytes = PlaylistStore.toBytes(playlists.values());
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlaylistStore.writeAtomically(file, bytes);
            } catch (IOException ex) {
                dirty.set(true); // try again at the next period
                plugin.getLogger().warning("Could not save " + PlaylistStore.FILE_NAME + ": " + ex.getMessage());
            } finally {
                writing.set(false);
            }
        });
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
