package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Кодирование и отправка пакетов клиентскому моду (формат совпадает с PlayPayload / ControlPayload мода). */
public final class SoundNetwork {

    public static final String PLAY_CHANNEL = "dvmn2:sm_play";
    public static final String CONTROL_CHANNEL = "dvmn2:sm_ctl";
    public static final int PROTOCOL = 1;

    public static final int MODE_GLOBAL = 0;
    public static final int MODE_POSITION = 1;
    public static final int MODE_ENTITY = 2;

    public enum Action {
        STOP(0), PAUSE(1), RESUME(2), SEEK(3), VOLUME(4), PITCH(5);

        final int id;

        Action(int id) {
            this.id = id;
        }
    }

    private final SoundManagerPlugin plugin;

    public SoundNetwork(SoundManagerPlugin plugin) {
        this.plugin = plugin;
    }

    /** Мод слушает оба канала. */
    public static boolean hasMod(Player player) {
        Set<String> channels = player.getListeningPluginChannels();
        return channels.contains(PLAY_CHANNEL) && channels.contains(CONTROL_CHANNEL);
    }

    public boolean sendPlay(Player player, String sound, SoundOptions o, int mode,
                            double x, double y, double z, int entityId) {
        if (!hasMod(player)) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            writeVarInt(out, PROTOCOL);
            writeString(out, sound);
            writeString(out, o.category());
            out.writeFloat(o.volume());
            out.writeFloat(o.pitch());
            writeVarInt(out, mode);
            out.writeDouble(x);
            out.writeDouble(y);
            out.writeDouble(z);
            writeVarInt(out, entityId);
            out.writeLong(o.startMs());
            out.writeLong(o.endMs());
            writeVarInt(out, (int) o.fadeInMs());
            writeVarInt(out, (int) o.fadeOutMs());
            player.sendPluginMessage(plugin, PLAY_CHANNEL, bytes.toByteArray());
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Не удалось отправить sm_play игроку " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    public boolean sendControl(Player player, Action action, String sound, long valueMs, float value, long durationMs) {
        if (!hasMod(player)) {
            return false;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            writeVarInt(out, PROTOCOL);
            writeVarInt(out, action.id);
            writeString(out, sound == null ? "" : sound);
            out.writeLong(valueMs);
            out.writeFloat(value);
            writeVarInt(out, (int) Math.max(0L, Math.min(durationMs, SoundOptions.MAX_FADE_MS)));
            player.sendPluginMessage(plugin, CONTROL_CHANNEL, bytes.toByteArray());
            return true;
        } catch (IOException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Не удалось отправить sm_ctl игроку " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    /** Приводит id к виду namespace:path; null, если формат неверный. */
    public static String normalizeSound(String sound) {
        if (sound == null || sound.isBlank()) {
            return null;
        }
        String s = sound.contains(":") ? sound : "minecraft:" + sound;
        return s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") ? s : null;
    }

    // Формат Minecraft: VarInt, строка = VarInt длина в байтах + UTF-8.

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }
}
