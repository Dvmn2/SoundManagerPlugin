package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.model.Playlist;
import net.dvmn2.soundmanagerplugin.model.PlaylistTrack;
import net.dvmn2.soundmanagerplugin.model.RunState;
import net.dvmn2.soundmanagerplugin.util.PlayOrder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * One playback of a playlist for a fixed set of players: plays the tracks one after another by timer.
 * <p>
 * Main thread only. The playlist is not edited while it runs (the manager refuses), so the track indexes stay
 * valid; {@code loop} and {@code shuffle} are read from the playlist when a cycle ends, so a change applies to the
 * next cycle at the latest. Sounds are controlled by id, so a control packet also reaches another playing sound
 * with the same id on the client.
 */
final class PlaylistRun {

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;
    private final SoundManagerApi api;
    private final Playlist playlist;
    private final Consumer<PlaylistRun> onChange;
    private final Consumer<PlaylistRun> onFinished;
    private final Set<UUID> players = new LinkedHashSet<>();

    private int[] order = new int[0];
    private int position;
    private int current = -1;
    private RunState state = RunState.STOPPED;
    @Nullable
    private BukkitTask timer;
    /** {@link System#nanoTime()} at which the current track ends while playing. */
    private long deadlineNanos;
    /** Time left of the current track while paused, nanoseconds. */
    private long remainingNanos;

    /**
     * @param onChange   called after the state or the current track changed
     * @param onFinished called once when the run ended, after {@code onChange}
     */
    PlaylistRun(Plugin plugin, SoundManagerApi api, Playlist playlist,
                Consumer<PlaylistRun> onChange, Consumer<PlaylistRun> onFinished) {
        this.plugin = plugin;
        this.api = api;
        this.playlist = playlist;
        this.onChange = onChange;
        this.onFinished = onFinished;
    }

    Playlist getPlaylist() {
        return playlist;
    }

    RunState getState() {
        return state;
    }

    /** Index of the current track in the stored order, -1 if the run is stopped. */
    int getCurrent() {
        return current;
    }

    // ---------- Control ----------

    /** Starts from the first track of a new cycle for the given players. The playlist must have tracks. */
    void start(Collection<UUID> targets) {
        players.addAll(targets);
        order = buildOrder(-1);
        position = 0;
        playCurrent();
    }

    /** Stops the current sound for the players and ends the run. */
    void stop() {
        if (state == RunState.STOPPED) {
            return;
        }
        cancelTimer();
        stopSoundForPlayers();
        finish();
    }

    /** Skips to the next track (the first track of the next cycle after the last one). */
    void next() {
        if (state == RunState.STOPPED) {
            return;
        }
        cancelTimer();
        stopSoundForPlayers();
        advance();
    }

    /** Pauses the current track; returns false if the run is not playing. */
    boolean pause() {
        if (state != RunState.PLAYING) {
            return false;
        }
        remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
        cancelTimer();
        String sound = currentSound();
        forEachPlayer(player -> api.pause(player, sound));
        state = RunState.PAUSED;
        onChange.accept(this);
        return true;
    }

    /** Continues a paused track; returns false if the run is not paused. */
    boolean resume() {
        if (state != RunState.PAUSED) {
            return false;
        }
        String sound = currentSound();
        forEachPlayer(player -> api.resume(player, sound));
        state = RunState.PLAYING;
        schedule(remainingNanos / NANOS_PER_MILLI);
        onChange.accept(this);
        return true;
    }

    /** Removes a player who left; the run ends when nobody is left. */
    void removePlayer(UUID id) {
        if (players.remove(id) && players.isEmpty()) {
            stop();
        }
    }

    // ---------- Playback ----------

    private void playCurrent() {
        current = order[position];
        PlaylistTrack track = playlist.getTracks().get(current);
        forEachPlayer(player -> deliver(player, track));
        if (players.isEmpty()) {
            finish();
            return;
        }
        state = RunState.PLAYING;
        schedule(track.durationMs());
        onChange.accept(this);
    }

    private void deliver(Player player, PlaylistTrack track) {
        if (track.mode() == PlaylistTrack.Mode.POSITION) {
            Location location = new Location(player.getWorld(), track.x(), track.y(), track.z());
            api.playAt(player, track.sound(), location, track.options());
        } else {
            api.playGlobal(player, track.sound(), track.options());
        }
    }

    private void advance() {
        position++;
        if (position >= order.length) {
            if (!playlist.isLoop()) {
                finish();
                return;
            }
            order = buildOrder(current);
            position = 0;
        }
        playCurrent();
    }

    private void schedule(long durationMs) {
        long ticks = Math.max(1L, (durationMs + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK);
        deadlineNanos = System.nanoTime() + durationMs * NANOS_PER_MILLI;
        timer = plugin.getServer().getScheduler().runTaskLater(plugin, this::onTrackEnd, ticks);
    }

    private void onTrackEnd() {
        timer = null;
        advance();
    }

    private void finish() {
        cancelTimer();
        state = RunState.STOPPED;
        current = -1;
        players.clear();
        onChange.accept(this);
        onFinished.accept(this);
    }

    private int[] buildOrder(int lastPlayed) {
        int size = playlist.getTracks().size();
        return playlist.isShuffle()
                ? PlayOrder.shuffled(size, lastPlayed, ThreadLocalRandom.current())
                : PlayOrder.sequential(size);
    }

    // ---------- Helpers ----------

    private void cancelTimer() {
        BukkitTask task = timer;
        timer = null;
        if (task != null) {
            task.cancel();
        }
    }

    private String currentSound() {
        return playlist.getTracks().get(current).sound();
    }

    private void stopSoundForPlayers() {
        if (current < 0) {
            return;
        }
        String sound = currentSound();
        forEachPlayer(player -> api.stop(player, sound, 0L));
    }

    /** Runs the action for every player who is still online; forgets the others. */
    private void forEachPlayer(Consumer<Player> action) {
        List<UUID> gone = new ArrayList<>();
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !api.hasMod(player)) {
                gone.add(id);
            } else {
                action.accept(player);
            }
        }
        gone.forEach(players::remove);
    }
}
