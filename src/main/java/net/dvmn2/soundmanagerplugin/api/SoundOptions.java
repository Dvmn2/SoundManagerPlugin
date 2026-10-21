package net.dvmn2.soundmanagerplugin.api;

import java.util.Locale;

/**
 * Параметры запуска звука. Все времена — в миллисекундах.
 * Значения приводятся к допустимым диапазонам; при {@code endMs >= 0 && endMs <= startMs}
 * бросается {@link IllegalArgumentException}.
 *
 * @param category категория (ползунок громкости): master, music, record, weather, block, hostile,
 *                 neutral, player, ambient, voice, ui
 * @param volume   0..10 (больше 1 — увеличивает радиус слышимости позиционных звуков)
 * @param pitch    0.5..2.0
 * @param startMs  с какого места начать (0 = с начала)
 * @param endMs    на каком месте закончить (-1 = играть до конца)
 * @param fadeInMs нарастание громкости с момента старта
 * @param fadeOutMs затухание, которое заканчивается ровно в endMs
 */
public record SoundOptions(String category, float volume, float pitch,
                           long startMs, long endMs, long fadeInMs, long fadeOutMs) {

    public static final long MAX_TIME_MS = 24L * 60 * 60 * 1000;
    public static final long MAX_FADE_MS = 10L * 60 * 1000;

    public SoundOptions {
        category = category == null || category.isBlank() ? "master" : category.toLowerCase(Locale.ROOT);
        volume = Float.isNaN(volume) ? 1f : Math.max(0f, Math.min(10f, volume));
        pitch = Float.isNaN(pitch) ? 1f : Math.max(0.5f, Math.min(2f, pitch));
        startMs = Math.max(0L, Math.min(MAX_TIME_MS, startMs));
        endMs = endMs < 0 ? -1L : Math.min(MAX_TIME_MS, endMs);
        fadeInMs = Math.max(0L, Math.min(MAX_FADE_MS, fadeInMs));
        fadeOutMs = Math.max(0L, Math.min(MAX_FADE_MS, fadeOutMs));
        if (endMs >= 0 && endMs <= startMs) {
            throw new IllegalArgumentException("endMs (" + endMs + ") must be greater than startMs (" + startMs + ")");
        }
    }

    /** master, громкость 1, высота 1, с начала до конца, без fade'ов. */
    public static SoundOptions defaults() {
        return new SoundOptions("master", 1f, 1f, 0L, -1L, 0L, 0L);
    }

    public SoundOptions withCategory(String c) {
        return new SoundOptions(c, volume, pitch, startMs, endMs, fadeInMs, fadeOutMs);
    }

    public SoundOptions withVolume(float v) {
        return new SoundOptions(category, v, pitch, startMs, endMs, fadeInMs, fadeOutMs);
    }

    public SoundOptions withPitch(float p) {
        return new SoundOptions(category, volume, p, startMs, endMs, fadeInMs, fadeOutMs);
    }

    /** Начать с позиции (мс). Если endMs уже задан — он должен быть больше. */
    public SoundOptions withStart(long ms) {
        return new SoundOptions(category, volume, pitch, ms, endMs, fadeInMs, fadeOutMs);
    }

    /** Закончить на позиции (мс); -1 = до конца звука. */
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
