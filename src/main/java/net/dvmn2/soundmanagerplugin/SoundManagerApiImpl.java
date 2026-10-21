package net.dvmn2.soundmanagerplugin;

import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

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
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.MODE_GLOBAL, 0, 0, 0, 0);
    }

    @Override
    public boolean playAt(Player player, String sound, Location location, SoundOptions options) {
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.MODE_POSITION,
                location.getX(), location.getY(), location.getZ(), 0);
    }

    @Override
    public boolean playOnEntity(Player player, String sound, Entity entity, SoundOptions options) {
        Location l = entity.getLocation();
        return network.sendPlay(player, requireSound(sound), options, SoundNetwork.MODE_ENTITY,
                l.getX(), l.getY(), l.getZ(), entity.getEntityId());
    }

    @Override
    public boolean stop(Player player, String sound, long fadeMs) {
        return network.sendControl(player, SoundNetwork.Action.STOP, optionalSound(sound), 0, 0, fadeMs);
    }

    @Override
    public boolean pause(Player player, String sound) {
        return network.sendControl(player, SoundNetwork.Action.PAUSE, optionalSound(sound), 0, 0, 0);
    }

    @Override
    public boolean resume(Player player, String sound) {
        return network.sendControl(player, SoundNetwork.Action.RESUME, optionalSound(sound), 0, 0, 0);
    }

    @Override
    public boolean seek(Player player, String sound, long positionMs) {
        return network.sendControl(player, SoundNetwork.Action.SEEK, optionalSound(sound),
                Math.max(0L, positionMs), 0, 0);
    }

    @Override
    public boolean setVolume(Player player, String sound, float volume, long rampMs) {
        return network.sendControl(player, SoundNetwork.Action.VOLUME, optionalSound(sound), 0,
                Math.max(0f, Math.min(10f, volume)), rampMs);
    }

    @Override
    public boolean setPitch(Player player, String sound, float pitch, long rampMs) {
        return network.sendControl(player, SoundNetwork.Action.PITCH, optionalSound(sound), 0,
                Math.max(0.5f, Math.min(2f, pitch)), rampMs);
    }

    private static String requireSound(String sound) {
        String s = SoundNetwork.normalizeSound(sound);
        if (s == null) {
            throw new IllegalArgumentException("Invalid sound id: " + sound);
        }
        return s;
    }

    private static String optionalSound(String sound) {
        return sound == null ? null : requireSound(sound);
    }
}
