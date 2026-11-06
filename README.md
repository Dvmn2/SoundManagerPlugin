# SoundManagerPlugin

Paper plugin that plays and controls sounds on players' clients through the SoundManager mod: start offset, end point, fades, pause/resume, seek, smooth volume and pitch changes. Companion client mod: `SoundManager`.

## Requirements

- Minecraft 1.21.11, Paper, Java 21
- Companion mod `SoundManager` >= 2.0.0 on the clients (players without it are skipped); mod >= 2.2.0 to see and edit playlists in its window

## Build and run

```bash
./gradlew build      # build/libs/SoundManagerPlugin-<version>.jar
./gradlew runServer  # Paper 1.21.11 test server
```

## Architecture

```text
net.dvmn2.soundmanagerplugin
├── SoundManagerPlugin.java      // composition root
├── Lang.java                    // localisation (MiniMessage, RU/EN)
├── Permissions.java             // permission constants
├── SoundManagerSettings.java    // config snapshot
├── SoundManagerApiImpl.java     // API implementation
├── SoundNetwork.java            // packet encoding and sending
├── PlaylistNetwork.java         // playlist state packets
├── PlaylistManager.java         // playlists, their file and running playlists
├── PlaylistRun.java             // one running playlist (timer, order, pause)
├── PlaylistListener.java        // channel registration and quit events
├── Wire.java                    // VarInt and string writers
├── api/                         // public API
├── command/                     // Brigadier trees
├── model/                       // Playlist, PlaylistTrack, RunState
├── storage/                     // PlaylistStore and storage.dto
└── util/                        // PlayOrder, SoundIds
```

| Class | Responsibility |
|---|---|
| `SoundManagerPlugin` | composition root: wiring, channel and service registration, reload |
| `SoundManagerSettings` | immutable snapshot of `config.yml`, invalid values fall back to defaults with a warning |
| `Lang` | all player-visible texts; RU and EN variants of every key are verified at class load |
| `SoundNetwork` | encodes `dvmn2:sm_play` and `dvmn2:sm_ctl`, detects the mod through listened channels |
| `SoundManagerApiImpl` | implements `SoundManagerApi` on top of `SoundNetwork` |
| `SoundManagerCommand` | `/soundmanager` tree, tree documented in its Javadoc |
| `PlaylistNode`, `CommandSupport` | the `playlist` subtree; shared argument names and helpers of the trees |
| `PlaylistManager` | owns the playlists and the runs; edits, start/stop/next/pause/resume, periodic save |
| `PlaylistRun` | plays the tracks of one playlist in turn by timer for a fixed set of players |
| `PlaylistNetwork` | encodes `dvmn2:sm_plist` and sends it to players who may see playlists |
| `PlaylistStore` | reads and writes `playlists.json` (tolerant reading, atomic writing) |
| `Playlist`, `PlaylistTrack` | domain objects; a track is a copy of play options plus the length of the sound file |
| `PlayOrder` | order of one cycle: sequential or shuffled without repeating a track at the junction |

Threading: all Bukkit API calls, the API methods, the playlist manager and the runs work on the server main thread. The only asynchronous code writes the bytes of `playlists.json`; the snapshot is serialised on the main thread first.

Lifecycle: `onEnable` saves the default config, parses settings, registers the outgoing channels, registers the API in `ServicesManager`, loads the playlists and starts their periodic save, registers the listener and the command, logs one line. `onDisable` stops all running playlists and saves the playlists synchronously. `/soundmanager reload` re-reads the config and updates the language without restarting the plugin.

## Public API

Obtain the service and call it from the main thread:

```java
SoundManagerApi api = Bukkit.getServicesManager().load(SoundManagerApi.class);
api.playGlobal(player, "mypack:boss.theme", SoundOptions.defaults()
        .withCategory(SoundGroup.MUSIC)
        .withStart(12_500)
        .withEnd(30_000)
        .withFadeOut(1500));
```

| Method | Description |
|---|---|
| `hasMod(player)` | true if the player has the client mod |
| `playGlobal(player, sound, options)` | sound heard the same everywhere |
| `playAt(player, sound, location, options)` | positional sound |
| `playOnEntity(player, sound, entity, options)` | sound that follows an entity |
| `stop(player, sound, fadeMs)` | stop with a fade-out |
| `pause(player, sound)` / `resume(player, sound)` | pause and resume from the remembered position |
| `seek(player, sound, positionMs)` | jump to a position |
| `setVolume(player, sound, volume, rampMs)` | change the volume, optionally smoothly |
| `setPitch(player, sound, pitch, rampMs)` | change the pitch, optionally smoothly |

- Every method returns `false` when the player has no mod; nothing is sent and the vanilla sound is not played.
- `sound` is `namespace:path` (`minecraft` is assumed without a namespace). An invalid id or an id longer than 256 bytes throws `IllegalArgumentException`.
- Control methods act on every instance of the sound; `sound = null` means all managed sounds of the player.
- `SoundOptions` is an immutable `record` that clamps values; `endMs >= 0 && endMs <= startMs` throws `IllegalArgumentException`. `SoundGroup` is the sound category enum.
- The plugin fires no events.

Playlists were added in 2.1.0 (new permission, new channel, new data file); nothing in the API changed.

Changes since 1.x: `SoundOptions.category` is now the `SoundGroup` enum instead of a string; sound ids longer than 256 bytes are rejected.

## Protocol

| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:sm_play` | S2C | 1 | `SoundNetwork` Javadoc |
| `dvmn2:sm_ctl` | S2C | 1 | `SoundNetwork` Javadoc |
| `dvmn2:sm_plist` | S2C | 1 | `PlaylistNetwork` Javadoc |

The plugin registers only outgoing channels and never receives data from the mod. A player has the mod when the client listens to both `sm_play` and `sm_ctl` (`Player#getListeningPluginChannels`). `sm_plist` is sent only to players who also have `soundmanager.playlist` and whose client listens to it; the full state follows `PlayerRegisterChannelEvent`, later changes are pushed to all such players. One playlist always fits one plugin message (limit 32 766 bytes), which is why a playlist has at most 90 tracks. Nothing is sent to players without the mod. The first field of every packet is the protocol version; the mod ignores packets with another version. The protocol version is independent of the artifact version.

## Data and files

| File | Content |
|---|---|
| `plugins/SoundManagerPlugin/config.yml` | `settings.language` |
| `plugins/SoundManagerPlugin/playlists.json` | `version` (`1`) and `playlists`: up to 50, each with `name` (up to 40 characters), `loop`, `shuffle` and up to 90 `tracks` (see `TrackDto`; times in milliseconds, `endMs = -1` means to the end, `lengthMs` is the length of the sound file or `-1`) |

The file is created on the first change and saved every 30 seconds when changed and on shutdown. A damaged file is moved to `playlists.json.broken`; invalid playlists and tracks are skipped with a warning. Running playlists are not saved.

## Conventions and constraints

- Commands are Brigadier only; times in commands are seconds, everywhere else milliseconds.
- Packet fields must stay byte-identical to `PlayPayload` and `ControlPayload` of the mod; any format change requires bumping `SoundNetwork.PROTOCOL` on both sides.
- Sound ids are validated against `[a-z0-9_.-]+:[a-z0-9_./-]+` and 256 bytes before sending, because the client rejects longer strings while decoding.
- A playlist is played by timer: the duration of a track is `(end - start) / pitch`, or `(length - start) / pitch` when no end is set, so a track without an end needs a known `length`. A playlist cannot be edited or deleted while it runs; `loop` and `shuffle` can, `shuffle` applies from the next cycle. The set of players is fixed at start. Control packets address sounds by id, so `stop`, `pause` and `resume` of a track also reach another playing sound with the same id.
- Playlists support `play` and `playat` tracks only; `playon` needs a living entity and is not stored.
- The `soundmanager.admin` permission contains `play`, `control`, `check`, `reload` and `playlist` as children; the root command is visible when any of them is granted.

## License

All Rights Reserved.
