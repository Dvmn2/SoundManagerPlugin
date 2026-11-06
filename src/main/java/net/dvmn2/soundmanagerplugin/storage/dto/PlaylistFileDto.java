package net.dvmn2.soundmanagerplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** The root of {@code playlists.json}. Public fields without logic. */
public final class PlaylistFileDto {

    /** Format version written by this plugin version. */
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public List<PlaylistDto> playlists = new ArrayList<>();
}
