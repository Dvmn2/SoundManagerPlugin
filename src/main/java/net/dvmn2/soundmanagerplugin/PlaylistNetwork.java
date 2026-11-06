package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.dvmn2.soundmanagerplugin.model.Playlist;
import net.dvmn2.soundmanagerplugin.model.PlaylistTrack;
import net.dvmn2.soundmanagerplugin.model.RunState;
import net.dvmn2.soundmanagerplugin.util.SoundIds;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encodes and sends the playlist state to the mod, so that its window can show the playlists of the server;
 * main thread only. The format matches {@code PlaylistPayload} of the mod byte for byte. The mod never answers:
 * playlists are edited with commands.
 * <p>
 * S2C {@code dvmn2:sm_plist}:
 * <pre>
 * VarInt  protocol
 * VarInt  action        (see {@link Action})
 * String  name          (playlist name, max 160 bytes; empty for RESET)
 * </pre>
 * SET adds or replaces the playlist with this name:
 * <pre>
 * Boolean loop
 * Boolean shuffle
 * VarInt  state         (0 = stopped, 1 = playing, 2 = paused)
 * VarInt  current       (index of the playing track in the stored order; -1 if none)
 * VarInt  trackCount    (at most {@link Playlist#MAX_TRACKS})
 * per track:
 *   VarInt  mode        (0 = global, 1 = at a position)
 *   String  sound       (max 256 bytes)
 *   String  category    (max 32 bytes)
 *   Float   volume
 *   Float   pitch
 *   Double  x, y, z
 *   Long    startMs
 *   Long    endMs       (-1 = to the end)
 *   Long    lengthMs    (length of the sound file; -1 = unknown)
 *   VarInt  fadeInMs
 *   VarInt  fadeOutMs
 * </pre>
 * STATE changes the run state of a playlist without resending its tracks:
 * <pre>
 * VarInt  state
 * VarInt  current
 * </pre>
 * RESET and REMOVE carry no further fields. RESET drops every playlist the mod knows; it precedes the SET packets
 * that follow a join, so the new state replaces the old one entirely.
 */
public final class PlaylistNetwork {

    public static final String CHANNEL = "dvmn2:sm_plist";
    /** Protocol version of the playlist packets; bumped on any format change. */
    public static final int PROTOCOL = 1;

    private static final int MAX_NAME_BYTES = Playlist.MAX_NAME_LENGTH * 4;
    private static final int MAX_CATEGORY_BYTES = 32;
    /** Paper refuses plugin messages larger than this, bytes (32 766 with a safety margin). */
    private static final int MAX_PACKET_BYTES = 32_700;

    /** The kind of a playlist packet. */
    private enum Action {
        RESET(0), SET(1), REMOVE(2), STATE(3);

        private final int id;

        Action(int id) {
            this.id = id;
        }
    }

    private final Plugin plugin;
    private final AtomicBoolean oversizeLogged = new AtomicBoolean();

    public PlaylistNetwork(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the outgoing channel. */
    public void registerChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    /** True if the player may see playlists and the client listens to the channel (has a mod that supports it). */
    public static boolean canReceive(Player player) {
        return player.hasPermission(Permissions.PLAYLIST) && player.getListeningPluginChannels().contains(CHANNEL);
    }

    /** Tells the mod to forget every playlist. */
    public void sendReset(Player player) {
        send(player, Action.RESET, "", null);
    }

    /** Sends a playlist with its tracks and run state, replacing the mod's copy. */
    public void sendPlaylist(Player player, Playlist playlist, RunState state, int current) {
        send(player, Action.SET, playlist.getName(), out -> {
            out.writeBoolean(playlist.isLoop());
            out.writeBoolean(playlist.isShuffle());
            Wire.writeVarInt(out, state.getId());
            Wire.writeVarInt(out, current);
            Wire.writeVarInt(out, playlist.getTracks().size());
            for (PlaylistTrack track : playlist.getTracks()) {
                writeTrack(out, track);
            }
        });
    }

    /** Tells the mod that a playlist no longer exists. */
    public void sendRemoved(Player player, String name) {
        send(player, Action.REMOVE, name, null);
    }

    /** Sends only the run state of a playlist. */
    public void sendState(Player player, String name, RunState state, int current) {
        send(player, Action.STATE, name, out -> {
            Wire.writeVarInt(out, state.getId());
            Wire.writeVarInt(out, current);
        });
    }

    private void send(Player player, Action action, String name, PacketBody body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            Wire.writeVarInt(out, PROTOCOL);
            Wire.writeVarInt(out, action.id);
            Wire.writeString(out, name, MAX_NAME_BYTES);
            if (body != null) {
                body.write(out);
            }
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Could not encode a playlist packet for " + player.getName() + ": "
                    + ex.getMessage());
            return;
        }
        if (bytes.size() > MAX_PACKET_BYTES) {
            if (oversizeLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("The playlist '" + name + "' does not fit in one packet ("
                        + bytes.size() + " bytes); it is not shown in the mod window");
            }
            return;
        }
        player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
    }

    private static void writeTrack(DataOutputStream out, PlaylistTrack track) throws IOException {
        SoundOptions options = track.options();
        Wire.writeVarInt(out, track.mode() == PlaylistTrack.Mode.POSITION ? 1 : 0);
        Wire.writeString(out, track.sound(), SoundIds.MAX_LENGTH);
        Wire.writeString(out, options.category().getId(), MAX_CATEGORY_BYTES);
        out.writeFloat(options.volume());
        out.writeFloat(options.pitch());
        out.writeDouble(track.x());
        out.writeDouble(track.y());
        out.writeDouble(track.z());
        out.writeLong(options.startMs());
        out.writeLong(options.endMs());
        out.writeLong(track.lengthMs());
        Wire.writeVarInt(out, (int) options.fadeInMs());
        Wire.writeVarInt(out, (int) options.fadeOutMs());
    }

    /** The action-specific part of a packet. */
    private interface PacketBody {
        void write(DataOutputStream out) throws IOException;
    }
}
