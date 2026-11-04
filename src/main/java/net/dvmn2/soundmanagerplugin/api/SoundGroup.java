package net.dvmn2.soundmanagerplugin.api;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Sound category of the client (the volume slider that controls the sound).
 * Not to be confused with {@link org.bukkit.SoundCategory}, which has no UI category.
 */
public enum SoundGroup {
    MASTER("master"),
    MUSIC("music"),
    RECORD("record"),
    WEATHER("weather"),
    BLOCK("block"),
    HOSTILE("hostile"),
    NEUTRAL("neutral"),
    PLAYER("player"),
    AMBIENT("ambient"),
    VOICE("voice"),
    UI("ui");

    private final String id;

    SoundGroup(String id) {
        this.id = id;
    }

    /** Name of the category in the mod protocol and in commands. */
    public String getId() {
        return id;
    }

    /** Finds a category by its id, ignoring case; returns null if there is none. */
    @Nullable
    public static SoundGroup fromId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        for (SoundGroup group : values()) {
            if (group.id.equals(normalized)) {
                return group;
            }
        }
        return null;
    }
}
