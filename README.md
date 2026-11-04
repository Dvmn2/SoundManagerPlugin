# SoundManagerPlugin

Paper plugin that plays and controls sounds on players' clients through the SoundManager mod: start offset, end point, fades, pause/resume, seek, smooth volume and pitch changes. Companion client mod: `SoundManager`.

## Requirements

- Minecraft 1.21.11, Paper, Java 21
- Companion mod `SoundManager` >= 2.0.0 on the clients (players without it are skipped)

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
├── Wire.java                    // VarInt and string writers
├── api/                         // public API
└── command/                     // Brigadier tree
```

| Class | Responsibility |
|---|---|
| `SoundManagerPlugin` | composition root: wiring, channel and service registration, reload |
| `SoundManagerSettings` | immutable snapshot of `config.yml`, invalid values fall back to defaults with a warning |
| `Lang` | all player-visible texts; RU and EN variants of every key are verified at class load |
| `SoundNetwork` | encodes `dvmn2:sm_play` and `dvmn2:sm_ctl`, detects the mod through listened channels |
| `SoundManagerApiImpl` | implements `SoundManagerApi` on top of `SoundNetwork` |
| `SoundManagerCommand` | `/soundmanager` tree, tree documented in its Javadoc |

Threading: all Bukkit API calls and all API methods run on the server main thread. The plugin has no asynchronous code and no runtime state besides the current language in `Lang`.

Lifecycle: `onEnable` saves the default config, parses settings, registers the outgoing channels, registers the API in `ServicesManager`, registers the command, logs one line. There is nothing to stop in `onDisable`; Bukkit unregisters the service and channels. `/soundmanager reload` re-reads the config and updates the language without restarting the plugin.

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

Changes since 1.x: `SoundOptions.category` is now the `SoundGroup` enum instead of a string; sound ids longer than 256 bytes are rejected.

## Protocol

| Channel | Direction | Protocol | Format |
|---|---|---|---|
| `dvmn2:sm_play` | S2C | 1 | `SoundNetwork` Javadoc |
| `dvmn2:sm_ctl` | S2C | 1 | `SoundNetwork` Javadoc |

The plugin registers only outgoing channels and never receives data from the mod. A player has the mod when the client listens to both channels (`Player#getListeningPluginChannels`). Nothing is sent to players without the mod. The first field of every packet is the protocol version; the mod ignores packets with another version. The protocol version is independent of the artifact version.

## Data and files

| File | Content |
|---|---|
| `plugins/SoundManagerPlugin/config.yml` | `settings.language` |

The plugin stores no other data.

## Conventions and constraints

- Commands are Brigadier only; times in commands are seconds, everywhere else milliseconds.
- Packet fields must stay byte-identical to `PlayPayload` and `ControlPayload` of the mod; any format change requires bumping `SoundNetwork.PROTOCOL` on both sides.
- Sound ids are validated against `[a-z0-9_.-]+:[a-z0-9_./-]+` and 256 bytes before sending, because the client rejects longer strings while decoding.
- The `soundmanager.admin` permission contains `play`, `control`, `check` and `reload` as children; the root command is visible when any of them is granted.

## License

All Rights Reserved.
