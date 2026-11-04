package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Encodes and sends the packets of the client mod; main thread only. Formats match {@code PlayPayload} and
 * {@code ControlPayload} of the mod byte for byte.
 * <p>
 * S2C {@code dvmn2:sm_play}:
 * <pre>
 * VarInt  protocol
 * String  sound        (max 256 bytes)
 * String  category     (max 32 bytes)
 * Float   volume
 * Float   pitch
 * VarInt  mode         (see {@link PlayMode})
 * Double  x, y, z
 * VarInt  entityId
 * Long    startMs
 * Long    endMs        (-1 = to the end)
 * VarInt  fadeInMs
 * VarInt  fadeOutMs
 * </pre>
 * S2C {@code dvmn2:sm_ctl}:
 * <pre>
 * VarInt  protocol
 * VarInt  action       (see {@link Action})
 * String  sound        (max 256 bytes; empty = all)
 * Long    valueMs
 * Float   value
 * VarInt  durationMs
 * </pre>
 */
public final class SoundNetwork {

    public static final String PLAY_CHANNEL = "dvmn2:sm_play";
    public static final String CONTROL_CHANNEL = "dvmn2:sm_ctl";
    /** Protocol version of the plugin-mod packets; bumped on any format change. */
    public static final int PROTOCOL = 1;

    private static final int MAX_SOUND_BYTES = 256;
    private static final int MAX_CATEGORY_BYTES = 32;
    private static final Pattern SOUND_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public enum PlayMode {
        GLOBAL(0), POSITION(1), ENTITY(2);

        private final int id;

        PlayMode(int id) {
            this.id = id;
        }
    }

    public enum Action {
        STOP(0), PAUSE(1), RESUME(2), SEEK(3), VOLUME(4), PITCH(5);

        private final int id;

        Action(int id) {
            this.id = id;
        }
    }

    private final Plugin plugin;

    public SoundNetwork(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the outgoing channels. The plugin only commands the mod; it never receives replies. */
    public void registerChannels() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PLAY_CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CONTROL_CHANNEL);
    }

    /** True if the player's client listens to both channels, i.e. has the mod. */
    public static boolean hasMod(Player player) {
        Set<String> channels = player.getListeningPluginChannels();
        return channels.contains(PLAY_CHANNEL) && channels.contains(CONTROL_CHANNEL);
    }

    /** Sends a play packet; returns false if the player has no mod or the packet could not be encoded. */
    public boolean sendPlay(Player player, String sound, SoundOptions options, PlayMode mode,
                            double x, double y, double z, int entityId) {
        if (!hasMod(player)) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            Wire.writeVarInt(out, PROTOCOL);
            Wire.writeString(out, sound, MAX_SOUND_BYTES);
            Wire.writeString(out, options.category().getId(), MAX_CATEGORY_BYTES);
            out.writeFloat(options.volume());
            out.writeFloat(options.pitch());
            Wire.writeVarInt(out, mode.id);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            Wire.writeVarInt(out, entityId);
            out.writeLong(options.startMs());
            out.writeLong(options.endMs());
            Wire.writeVarInt(out, (int) options.fadeInMs());
            Wire.writeVarInt(out, (int) options.fadeOutMs());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not encode a play packet for " + player.getName() + ": "
                    + ex.getMessage());
            return false;
        }
        player.sendPluginMessage(plugin, PLAY_CHANNEL, bytes.toByteArray());
        return true;
    }

    /** Sends a control packet; returns false if the player has no mod or the packet could not be encoded. */
    public boolean sendControl(Player player, Action action, @Nullable String sound,
                               long valueMs, float value, long durationMs) {
        if (!hasMod(player)) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            Wire.writeVarInt(out, PROTOCOL);
            Wire.writeVarInt(out, action.id);
            Wire.writeString(out, sound == null ? "" : sound, MAX_SOUND_BYTES);
            out.writeLong(valueMs);
            out.writeFloat(value);
            Wire.writeVarInt(out, (int) Math.max(0L, Math.min(durationMs, SoundOptions.MAX_FADE_MS)));
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not encode a control packet for " + player.getName() + ": "
                    + ex.getMessage());
            return false;
        }
        player.sendPluginMessage(plugin, CONTROL_CHANNEL, bytes.toByteArray());
        return true;
    }

    /** Normalises a sound id to {@code namespace:path}; returns null if the format is invalid or too long. */
    @Nullable
    public static String normalizeSound(@Nullable String sound) {
        if (sound == null || sound.isBlank()) {
            return null;
        }
        String id = sound.contains(":") ? sound : "minecraft:" + sound;
        return id.length() <= MAX_SOUND_BYTES && SOUND_ID.matcher(id).matches() ? id : null;
    }
}
