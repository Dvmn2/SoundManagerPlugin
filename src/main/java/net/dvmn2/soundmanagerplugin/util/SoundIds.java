package net.dvmn2.soundmanagerplugin.util;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/** Validation of sound ids shared by the commands, the network layer and the data files. Stateless. */
public final class SoundIds {

    /** Longest sound id the mod protocol accepts, characters (ids are ASCII, so also bytes). */
    public static final int MAX_LENGTH = 256;

    private static final Pattern SOUND_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private SoundIds() {
    }

    /** Normalises a sound id to {@code namespace:path}; returns null if the format is invalid or too long. */
    @Nullable
    public static String normalize(@Nullable String sound) {
        if (sound == null || sound.isBlank()) {
            return null;
        }
        String id = sound.contains(":") ? sound : "minecraft:" + sound;
        return id.length() <= MAX_LENGTH && SOUND_ID.matcher(id).matches() ? id : null;
    }
}
