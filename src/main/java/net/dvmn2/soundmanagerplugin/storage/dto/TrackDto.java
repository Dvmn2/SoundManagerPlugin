package net.dvmn2.soundmanagerplugin.storage.dto;

/**
 * One track in {@code playlists.json}. Public fields without logic; unknown fields are ignored and missing ones
 * keep the defaults below. Times are in milliseconds; {@code endMs = -1} means "to the end".
 */
public final class TrackDto {

    /** {@code global} or {@code position}. */
    public String mode = "global";
    public String sound = "";
    public double x;
    public double y;
    public double z;
    /** Category id as in the {@code category} command argument. */
    public String category = "master";
    public float volume = 1.0f;
    public float pitch = 1.0f;
    public long startMs;
    public long endMs = -1L;
    public long fadeInMs;
    public long fadeOutMs;
    /** Length of the whole sound file; -1 if unknown. */
    public long lengthMs = -1L;
}
