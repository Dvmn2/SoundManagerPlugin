package net.dvmn2.soundmanagerplugin.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.soundmanagerplugin.api.SoundGroup;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.dvmn2.soundmanagerplugin.model.Playlist;
import net.dvmn2.soundmanagerplugin.model.PlaylistTrack;
import net.dvmn2.soundmanagerplugin.storage.dto.PlaylistDto;
import net.dvmn2.soundmanagerplugin.storage.dto.PlaylistFileDto;
import net.dvmn2.soundmanagerplugin.storage.dto.TrackDto;
import net.dvmn2.soundmanagerplugin.util.SoundIds;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Reads and writes {@code playlists.json}. Stateless.
 * <p>
 * Reading is tolerant: a broken playlist or track is reported to the log and skipped, a file that cannot be parsed
 * is moved aside to {@code playlists.json.broken} so that it is not overwritten. Serialising only copies the
 * state, so it is cheap enough for the main thread; the bytes are written off the main thread with
 * {@link #writeAtomically(Path, byte[])}.
 */
public final class PlaylistStore {

    /** Name of the data file in the plugin data folder. */
    public static final String FILE_NAME = "playlists.json";

    private static final String BROKEN_SUFFIX = ".broken";
    /** Largest file that is read, bytes; protects the memory of the server from a damaged file. */
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final String MODE_POSITION = "position";
    private static final String MODE_GLOBAL = "global";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlaylistStore() {
    }

    /**
     * Reads the playlists from the file; a missing file gives an empty list.
     *
     * @param maxPlaylists playlists beyond this number are ignored
     */
    public static List<Playlist> load(Path file, int maxPlaylists, Logger log) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        PlaylistFileDto dto = readDto(file, log);
        if (dto == null || dto.playlists == null) {
            return List.of();
        }
        if (dto.version > PlaylistFileDto.CURRENT_VERSION) {
            log.warning(FILE_NAME + " has version " + dto.version + ", newer than supported "
                    + PlaylistFileDto.CURRENT_VERSION + "; unknown data is ignored");
        }
        List<Playlist> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (PlaylistDto entry : dto.playlists) {
            if (result.size() >= maxPlaylists) {
                log.warning(FILE_NAME + " has more than " + maxPlaylists + " playlists; the rest is ignored");
                break;
            }
            Playlist playlist = toPlaylist(entry, names, log);
            if (playlist != null) {
                result.add(playlist);
            }
        }
        return result;
    }

    /** Serialises a snapshot of the playlists to the bytes of the file. Main thread. */
    public static byte[] toBytes(Collection<Playlist> playlists) {
        PlaylistFileDto dto = new PlaylistFileDto();
        for (Playlist playlist : playlists) {
            PlaylistDto entry = new PlaylistDto();
            entry.name = playlist.getName();
            entry.loop = playlist.isLoop();
            entry.shuffle = playlist.isShuffle();
            for (PlaylistTrack track : playlist.getTracks()) {
                entry.tracks.add(toDto(track));
            }
            dto.playlists.add(entry);
        }
        return GSON.toJson(dto).getBytes(StandardCharsets.UTF_8);
    }

    /** Writes the bytes to a temporary file next to the target, then moves it over the target. */
    public static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------- Reading ----------

    @Nullable
    private static PlaylistFileDto readDto(Path file, Logger log) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                log.warning(FILE_NAME + " is larger than " + MAX_FILE_BYTES + " bytes; it was not read");
                moveAside(file, log);
                return null;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return GSON.fromJson(reader, PlaylistFileDto.class);
            }
        } catch (IOException | JsonParseException ex) {
            log.warning("Could not read " + FILE_NAME + ": " + ex.getMessage());
            moveAside(file, log);
            return null;
        }
    }

    private static void moveAside(Path file, Logger log) {
        Path broken = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX);
        try {
            Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            log.warning("The unreadable file was moved to " + broken.getFileName());
        } catch (IOException ex) {
            log.warning("Could not move " + FILE_NAME + " aside: " + ex.getMessage());
        }
    }

    @Nullable
    private static Playlist toPlaylist(PlaylistDto entry, Set<String> names, Logger log) {
        String name = entry.name == null ? "" : entry.name;
        if (!Playlist.isValidName(name)) {
            log.warning(FILE_NAME + ": a playlist has an invalid name '" + name + "'; skipped");
            return null;
        }
        if (!names.add(name.toLowerCase(Locale.ROOT))) {
            log.warning(FILE_NAME + ": playlist '" + name + "' is a duplicate; skipped");
            return null;
        }
        Playlist playlist = new Playlist(name);
        playlist.setLoop(entry.loop);
        playlist.setShuffle(entry.shuffle);
        List<TrackDto> tracks = entry.tracks == null ? List.of() : entry.tracks;
        for (int i = 0; i < tracks.size(); i++) {
            PlaylistTrack track = toTrack(tracks.get(i));
            if (track == null) {
                log.warning(FILE_NAME + ": playlist '" + name + "', track " + (i + 1) + " is invalid; skipped");
            } else if (!playlist.addTrack(track)) {
                log.warning(FILE_NAME + ": playlist '" + name + "' has more than " + Playlist.MAX_TRACKS
                        + " tracks; the rest is ignored");
                break;
            }
        }
        return playlist;
    }

    @Nullable
    private static PlaylistTrack toTrack(@Nullable TrackDto dto) {
        if (dto == null) {
            return null;
        }
        String sound = SoundIds.normalize(dto.sound);
        SoundGroup category = SoundGroup.fromId(dto.category == null ? "" : dto.category);
        if (sound == null || category == null) {
            return null;
        }
        PlaylistTrack.Mode mode = MODE_POSITION.equals(dto.mode)
                ? PlaylistTrack.Mode.POSITION : PlaylistTrack.Mode.GLOBAL;
        try {
            SoundOptions options = new SoundOptions(category, dto.volume, dto.pitch,
                    dto.startMs, dto.endMs, dto.fadeInMs, dto.fadeOutMs);
            return new PlaylistTrack(mode, sound, dto.x, dto.y, dto.z, options, dto.lengthMs);
        } catch (IllegalArgumentException ex) {
            return null; // an invalid range or coordinate; the caller reports the track
        }
    }

    private static TrackDto toDto(PlaylistTrack track) {
        SoundOptions options = track.options();
        TrackDto dto = new TrackDto();
        dto.mode = track.mode() == PlaylistTrack.Mode.POSITION ? MODE_POSITION : MODE_GLOBAL;
        dto.sound = track.sound();
        dto.x = track.x();
        dto.y = track.y();
        dto.z = track.z();
        dto.category = options.category().getId();
        dto.volume = options.volume();
        dto.pitch = options.pitch();
        dto.startMs = options.startMs();
        dto.endMs = options.endMs();
        dto.fadeInMs = options.fadeInMs();
        dto.fadeOutMs = options.fadeOutMs();
        dto.lengthMs = track.lengthMs();
        return dto;
    }
}
