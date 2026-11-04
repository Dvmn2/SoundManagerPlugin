package net.dvmn2.soundmanagerplugin.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * API for other plugins. Obtain with:
 * <pre>
 * SoundManagerApi api = Bukkit.getServicesManager().load(SoundManagerApi.class);
 * api.playGlobal(player, "mypack:boss.theme", SoundOptions.defaults()
 *         .withCategory(SoundGroup.MUSIC).withStart(12_500).withEnd(30_000).withFadeOut(1500));
 * </pre>
 * All methods MUST be called from the server main thread.
 * <p>
 * Every method returns {@code false} if the player does not have the SoundManager client mod; nothing is sent then
 * and the vanilla sound is NOT played. The {@code sound} parameter is an id from sounds.json
 * ({@code namespace:path}; without a namespace {@code minecraft} is assumed). An invalid id or an id longer than
 * 256 bytes throws {@link IllegalArgumentException}. Control methods act on every instance of the sound with that id;
 * {@code sound = null} means all managed sounds of the player.
 */
public interface SoundManagerApi {

    /** Returns true if the player has the SoundManager client mod. */
    boolean hasMod(Player player);

    /**
     * Plays a global sound: heard the same everywhere, independent of position.
     *
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean playGlobal(Player player, String sound, SoundOptions options);

    /**
     * Plays a sound at a point of the world (fades with distance).
     *
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean playAt(Player player, String sound, Location location, SoundOptions options);

    /**
     * Plays a sound attached to an entity: follows it and stops if the entity disappears on the client.
     *
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean playOnEntity(Player player, String sound, Entity entity, SoundOptions options);

    /**
     * Stops sounds with a fade-out.
     *
     * @param fadeMs fade length in milliseconds (0 = immediately)
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean stop(Player player, @Nullable String sound, long fadeMs);

    /**
     * Pauses sounds; the position is remembered.
     *
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean pause(Player player, @Nullable String sound);

    /**
     * Resumes paused sounds from the remembered position.
     *
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean resume(Player player, @Nullable String sound);

    /**
     * Seeks to a position; for paused sounds it changes the position to resume from.
     *
     * @param positionMs position in milliseconds
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean seek(Player player, @Nullable String sound, long positionMs);

    /**
     * Changes the volume.
     *
     * @param volume 0..10
     * @param rampMs transition length in milliseconds (0 = immediately)
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean setVolume(Player player, @Nullable String sound, float volume, long rampMs);

    /**
     * Changes the pitch.
     *
     * @param pitch  0.5..2.0
     * @param rampMs transition length in milliseconds (0 = immediately)
     * @return true if the packet was sent
     * @throws IllegalArgumentException if the sound id is invalid
     */
    boolean setPitch(Player player, @Nullable String sound, float pitch, long rampMs);
}
