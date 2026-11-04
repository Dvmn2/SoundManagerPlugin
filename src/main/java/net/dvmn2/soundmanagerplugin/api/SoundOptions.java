package net.dvmn2.soundmanagerplugin.api;

import java.util.Objects;

/**
 * Immutable parameters of a sound start. All times are in milliseconds.
 * Numeric values are clamped to the allowed ranges.
 *
 * @param category  the volume slider that controls the sound
 * @param volume    0..10 (above 1 it also increases the audible range of positional sounds)
 * @param pitch     0.5..2.0
 * @param startMs   offset to start from (0 = from the beginning)
 * @param endMs     offset to stop at (-1 = play to the end)
 * @param fadeInMs  volume rise from the moment of start
 * @param fadeOutMs fade that ends exactly at {@code endMs}
 * @throws IllegalArgumentException if {@code endMs >= 0 && endMs <= startMs}
 * @throws NullPointerException     if {@code category} is null
 */
public record SoundOptions(SoundGroup category, float volume, float pitch,
                           long startMs, long endMs, long fadeInMs, long fadeOutMs) {

    public static final float MAX_VOLUME = 10f;
    public static final float MIN_PITCH = 0.5f;
    public static final float MAX_PITCH = 2f;
    /** Upper bound for offsets: 24 hours, milliseconds. */
    public static final long MAX_TIME_MS = 24L * 60 * 60 * 1000;
    /** Upper bound for fades and transitions: 10 minutes, milliseconds. */
    public static final long MAX_FADE_MS = 10L * 60 * 1000;

    public SoundOptions {
        Objects.requireNonNull(category, "category");
        volume = Float.isNaN(volume) ? 1f : Math.max(0f, Math.min(MAX_VOLUME, volume));
        pitch = Float.isNaN(pitch) ? 1f : Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        startMs = Math.max(0L, Math.min(MAX_TIME_MS, startMs));
        endMs = endMs < 0 ? -1L : Math.min(MAX_TIME_MS, endMs);
        fadeInMs = Math.max(0L, Math.min(MAX_FADE_MS, fadeInMs));
        fadeOutMs = Math.max(0L, Math.min(MAX_FADE_MS, fadeOutMs));
        if (endMs >= 0 && endMs <= startMs) {
            throw new IllegalArgumentException("endMs (" + endMs + ") must be greater than startMs (" + startMs + ")");
        }
    }

    /** Category {@link SoundGroup#MASTER}, volume 1, pitch 1, from start to end, no fades. */
    public static SoundOptions defaults() {
        return new SoundOptions(SoundGroup.MASTER, 1f, 1f, 0L, -1L, 0L, 0L);
    }

    public SoundOptions withCategory(SoundGroup newCategory) {
        return new SoundOptions(newCategory, volume, pitch, startMs, endMs, fadeInMs, fadeOutMs);
    }

    public SoundOptions withVolume(float newVolume) {
        return new SoundOptions(category, newVolume, pitch, startMs, endMs, fadeInMs, fadeOutMs);
    }

    public SoundOptions withPitch(float newPitch) {
        return new SoundOptions(category, volume, newPitch, startMs, endMs, fadeInMs, fadeOutMs);
    }

    /** Starts from the offset (ms). If {@code endMs} is already set, it must be greater. */
    public SoundOptions withStart(long ms) {
        return new SoundOptions(category, volume, pitch, ms, endMs, fadeInMs, fadeOutMs);
    }

    /** Stops at the offset (ms); -1 = play to the end of the sound. */
    public SoundOptions withEnd(long ms) {
        return new SoundOptions(category, volume, pitch, startMs, ms, fadeInMs, fadeOutMs);
    }

    public SoundOptions withFadeIn(long ms) {
        return new SoundOptions(category, volume, pitch, startMs, endMs, ms, fadeOutMs);
    }

    public SoundOptions withFadeOut(long ms) {
        return new SoundOptions(category, volume, pitch, startMs, endMs, fadeInMs, ms);
    }
}
