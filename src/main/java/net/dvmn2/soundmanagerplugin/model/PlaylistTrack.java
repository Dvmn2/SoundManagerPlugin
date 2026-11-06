package net.dvmn2.soundmanagerplugin.model;

import net.dvmn2.soundmanagerplugin.api.SoundOptions;

import java.util.Objects;

/**
 * One track of a playlist: a sound with its play options, immutable.
 * <p>
 * The plugin cannot read sound files, so it plays a playlist by timer. The duration of a track comes from the
 * options: {@code end - start} when an end is set, otherwise {@code length - start}, both divided by the pitch.
 *
 * @param mode     where the sound is heard
 * @param sound    sound id, {@code namespace:path}
 * @param x        position, blocks (only for {@link Mode#POSITION}; otherwise 0)
 * @param y        position, blocks
 * @param z        position, blocks
 * @param options  play options
 * @param lengthMs length of the whole sound file, milliseconds; {@link #UNKNOWN_LENGTH} if not given
 * @throws IllegalArgumentException if a coordinate is not finite, or the end is not set and the length does not
 *                                  exceed the start
 */
public record PlaylistTrack(Mode mode, String sound, double x, double y, double z,
                            SoundOptions options, long lengthMs) {

    /** Marks a track whose file length is not known. */
    public static final long UNKNOWN_LENGTH = -1L;
    /** Shortest duration of a track, milliseconds; protects the timer from a busy loop. */
    public static final long MIN_DURATION_MS = 100L;
    /** Largest coordinate that fits in the world, blocks. */
    public static final double MAX_COORDINATE = 30_000_000.0;

    /** Where the track is heard. */
    public enum Mode { GLOBAL, POSITION }

    public PlaylistTrack {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(options, "options");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Coordinates must be finite");
        }
        x = Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, x));
        y = Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, y));
        z = Math.max(-MAX_COORDINATE, Math.min(MAX_COORDINATE, z));
        lengthMs = lengthMs < 0 ? UNKNOWN_LENGTH : Math.min(lengthMs, SoundOptions.MAX_TIME_MS);
        if (options.endMs() < 0 && lengthMs <= options.startMs()) {
            throw new IllegalArgumentException("A track without an end needs a length greater than its start");
        }
    }

    /** How long the track sounds on a client, milliseconds, at least {@link #MIN_DURATION_MS}. */
    public long durationMs() {
        long end = options.endMs() >= 0 ? options.endMs() : lengthMs;
        long span = end - options.startMs();
        return Math.max(MIN_DURATION_MS, Math.round(span / (double) options.pitch()));
    }
}
