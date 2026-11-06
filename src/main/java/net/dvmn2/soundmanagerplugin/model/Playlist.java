package net.dvmn2.soundmanagerplugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of tracks with the loop and shuffle switches.
 * <p>
 * Mutated by {@code PlaylistManager} only, on the server main thread. The name is fixed for the lifetime of the
 * object.
 */
public final class Playlist {

    /** Longest playlist name, characters. */
    public static final int MAX_NAME_LENGTH = 40;
    /** Most tracks in one playlist; the whole playlist must fit in one plugin message (32 766 bytes). */
    public static final int MAX_TRACKS = 90;

    private final String name;
    private final List<PlaylistTrack> tracks = new ArrayList<>();
    private boolean loop = true;
    private boolean shuffle;

    public Playlist(String name) {
        this.name = name;
    }

    /** True if the name is not blank, is not longer than {@link #MAX_NAME_LENGTH} and has no control characters. */
    public static boolean isValidName(String name) {
        if (name.isBlank() || name.length() > MAX_NAME_LENGTH || !name.equals(name.strip())) {
            return false;
        }
        return name.chars().noneMatch(Character::isISOControl);
    }

    public String getName() {
        return name;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
    }

    /** The tracks in stored order; a read-only view. */
    public List<PlaylistTrack> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    /** Appends a track; returns false if the playlist already has {@link #MAX_TRACKS} tracks. */
    public boolean addTrack(PlaylistTrack track) {
        if (tracks.size() >= MAX_TRACKS) {
            return false;
        }
        tracks.add(track);
        return true;
    }

    /** Removes the track at the zero-based index; returns false if there is no such track. */
    public boolean removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) {
            return false;
        }
        tracks.remove(index);
        return true;
    }

    /** Moves a track between zero-based indexes; returns false if either index is out of range. */
    public boolean moveTrack(int from, int to) {
        if (from < 0 || from >= tracks.size() || to < 0 || to >= tracks.size()) {
            return false;
        }
        tracks.add(to, tracks.remove(from));
        return true;
    }
}
