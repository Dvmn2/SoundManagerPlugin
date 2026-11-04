package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link SoundManagerApi}; main thread only. */
public final class SoundManagerApiImpl implements SoundManagerApi {

    private final SoundNetwork network;

    public SoundManagerApiImpl(SoundNetwork network) {
        this.network = network;
    }

    @Override
    public boolean hasMod(Player player) {
        return SoundNetwork.hasMod(player);
    }

    @Override
    public boolean playGlobal(Player player, String sound, SoundOptions options) {
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.PlayMode.GLOBAL, 0, 0, 0, 0);
    }

    @Override
    public boolean playAt(Player player, String sound, Location location, SoundOptions options) {
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.PlayMode.POSITION,
                location.getX(), location.getY(), location.getZ(), 0);
    }

    @Override
    public boolean playOnEntity(Player player, String sound, Entity entity, SoundOptions options) {
        Location location = entity.getLocation();
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.PlayMode.ENTITY,
                location.getX(), location.getY(), location.getZ(), entity.getEntityId());
    }

    @Override
    public boolean stop(Player player, @Nullable String sound, long fadeMs) {
        return network.sendControl(player, SoundNetwork.Action.STOP, optionalSound(sound), 0, 0, fadeMs);
    }

    @Override
    public boolean pause(Player player, @Nullable String sound) {
        return network.sendControl(player, SoundNetwork.Action.PAUSE, optionalSound(sound), 0, 0, 0);
    }

    @Override
    public boolean resume(Player player, @Nullable String sound) {
        return network.sendControl(player, SoundNetwork.Action.RESUME, optionalSound(sound), 0, 0, 0);
    }

    @Override
    public boolean seek(Player player, @Nullable String sound, long positionMs) {
        return network.sendControl(player, SoundNetwork.Action.SEEK, optionalSound(sound),
                Math.max(0L, positionMs), 0, 0);
    }

    @Override
    public boolean setVolume(Player player, @Nullable String sound, float volume, long rampMs) {
        float clamped = Float.isNaN(volume) ? 1f : Math.max(0f, Math.min(SoundOptions.MAX_VOLUME, volume));
        return network.sendControl(player, SoundNetwork.Action.VOLUME, optionalSound(sound), 0, clamped, rampMs);
    }

    @Override
    public boolean setPitch(Player player, @Nullable String sound, float pitch, long rampMs) {
        float clamped = Float.isNaN(pitch) ? 1f
                : Math.max(SoundOptions.MIN_PITCH, Math.min(SoundOptions.MAX_PITCH, pitch));
        return network.sendControl(player, SoundNetwork.Action.PITCH, optionalSound(sound), 0, clamped, rampMs);
    }

    private static String requireSound(String sound) {
        String normalized = SoundNetwork.normalizeSound(sound);
        if (normalized == null) {
            throw new IllegalArgumentException("Invalid sound id: " + sound);
        }
        return normalized;
    }

    @Nullable
    private static String optionalSound(@Nullable String sound) {
        return sound == null ? null : requireSound(sound);
    }
}
