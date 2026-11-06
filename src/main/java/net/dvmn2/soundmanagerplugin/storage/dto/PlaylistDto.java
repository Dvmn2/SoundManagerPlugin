package net.dvmn2.soundmanagerplugin.storage.dto;

import java.util.ArrayList;
import java.util.List;

/** One playlist in {@code playlists.json}. Public fields without logic. */
public final class PlaylistDto {

    public String name = "";
    public boolean loop = true;
    public boolean shuffle;
    public List<TrackDto> tracks = new ArrayList<>();
}
