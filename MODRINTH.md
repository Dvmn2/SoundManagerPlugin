# SoundManagerPlugin

Paper plugin that lets you play music and sound effects for players with precise control: start and end points, fades, pause, resume and seeking.

## Features

- Play a sound for chosen players: everywhere, at a position, or attached to an entity
- Start from any moment, stop at any moment, fade in and fade out
- Pause, resume and seek while a sound is playing
- Change volume and pitch smoothly
- Playlists: a list of tracks that plays one after another for chosen players, with loop, shuffle, pause and skip
- Check which players have the client mod
- Public API for other plugins
- Messages in English and Russian

## Requirements

- Paper 1.21.11, Java 21
- Players need the SoundManager client mod; players without it are skipped

## Installation

1. Put `SoundManagerPlugin-<version>.jar` into the `plugins` folder.
2. Start the server; `plugins/SoundManagerPlugin/config.yml` is created.
3. Ask players to install the SoundManager mod.

## Commands

All times are in seconds (fractions allowed). `end = -1` means play to the end.

| Command | Permission | Description |
|---|---|---|
| `/soundmanager play <players> <sound> [start [end [fadein [fadeout [volume [pitch [category]]]]]]]` | `soundmanager.play` | Play a global sound |
| `/soundmanager playat <players> <sound> <x y z> [options]` | `soundmanager.play` | Play a sound at a position |
| `/soundmanager playon <players> <sound> <entity> [options]` | `soundmanager.play` | Play a sound that follows an entity |
| `/soundmanager stop <players> [<sound> [<fade>]]` | `soundmanager.control` | Stop sounds, optionally with a fade-out |
| `/soundmanager pause <players> [<sound>]` | `soundmanager.control` | Pause sounds |
| `/soundmanager resume <players> [<sound>]` | `soundmanager.control` | Resume paused sounds |
| `/soundmanager seek <players> <sound> <seconds>` | `soundmanager.control` | Jump to a position |
| `/soundmanager volume <players> <sound> <volume> [<ramp>]` | `soundmanager.control` | Change the volume (0..10) |
| `/soundmanager pitch <players> <sound> <pitch> [<ramp>]` | `soundmanager.control` | Change the pitch (0.5..2.0) |
| `/soundmanager check <player>` | `soundmanager.check` | Show whether the player has the mod |
| `/soundmanager reload` | `soundmanager.reload` | Reload `config.yml` |
| `/soundmanager playlist list` | `soundmanager.playlist` | List playlists and their state |
| `/soundmanager playlist create <name>` / `delete <name>` | `soundmanager.playlist` | Create or delete a playlist (names with spaces in quotes) |
| `/soundmanager playlist add <name> play <sound> <length> [options]` | `soundmanager.playlist` | Add a track that plays everywhere |
| `/soundmanager playlist add <name> playat <sound> <x y z> <length> [options]` | `soundmanager.playlist` | Add a track that plays at a position |
| `/soundmanager playlist remove <name> <number>` / `move <name> <from> <to>` | `soundmanager.playlist` | Remove or move a track (numbers start at 1) |
| `/soundmanager playlist set <name> loop\|shuffle <true\|false>` | `soundmanager.playlist` | Switch loop or shuffle |
| `/soundmanager playlist start <name> <players>` | `soundmanager.playlist` | Start the playlist for players |
| `/soundmanager playlist stop\|next\|pause\|resume <name>` | `soundmanager.playlist` | Control a running playlist |

`<length>` is the length of the whole sound file in seconds; use `-1` if you give an end in the options. The plugin cannot read sound files, so a track without an end needs its length. The mod window fills it in for you.

Sound categories: `master`, `music`, `record`, `weather`, `block`, `hostile`, `neutral`, `player`, `ambient`, `voice`, `ui`.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `soundmanager.play` | op | Use `play`, `playat` and `playon` |
| `soundmanager.control` | op | Use `stop`, `pause`, `resume`, `seek`, `volume` and `pitch` |
| `soundmanager.check` | op | Use `check` |
| `soundmanager.reload` | op | Use `reload` |
| `soundmanager.playlist` | op | Use `playlist` commands |
| `soundmanager.admin` | op | All of the above |

## Configuration

File: `plugins/SoundManagerPlugin/config.yml`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `settings.language` | string | `auto` | `ru`, `en`, `auto` | Message language; `auto` uses the player's client language and English for the console |

```yaml
settings:
  language: auto
```

## Data

The plugin creates `config.yml` and, after the first playlist is created, `playlists.json`. It stores no player data. Back up `playlists.json` to keep your playlists; delete it to remove them all. Running playlists are not saved: after a restart start them again.

## Companion mod

The SoundManager client mod performs the actual playback. Without it a player hears nothing from this plugin and is skipped by every command. Use `/soundmanager check <player>` to see whether a player has it. Install the mod version that matches the plugin version.

## Troubleshooting

- **"Skipped (no SoundManager mod)".** The player does not have the mod, or has switched it off with the mod's emergency command.
- **"Invalid sound id".** Use `namespace:path`, for example `minecraft:music.creative` or `mypack:boss.theme`. The sound must exist in the game or in the player's resource pack.
- **A sound is quiet or silent.** The player may have lowered the volume of the sound category or set a volume cap in the mod.
- **A playlist does not change.** A playlist that is playing cannot be edited or deleted; stop it first.
- **Tracks overlap or leave gaps.** The plugin counts the time of a track from the options and the given length. A wrong `length`, or a sound event with several files of different length, gives a wrong time; set an explicit end.
- **The command is not visible.** Grant `soundmanager.admin` or one of the specific permissions.

## License

All Rights Reserved.

## Русский

Плагин для Paper, который позволяет проигрывать музыку и звуки игрокам с точным управлением: начало и конец, плавное нарастание и затухание, пауза, продолжение и перемотка.

### Возможности

- Проигрывание звука выбранным игрокам: везде, в точке мира или с привязкой к сущности
- Старт с любого момента, остановка в любой момент, нарастание и затухание
- Пауза, продолжение и перемотка во время звучания
- Плавное изменение громкости и высоты
- Плейлисты: список треков, которые играют по очереди выбранным игрокам, с повтором, перемешиванием, паузой и пропуском
- Проверка, у каких игроков есть клиентский мод
- Публичный API для других плагинов
- Сообщения на русском и английском

### Требования

- Paper 1.21.11, Java 21
- Игрокам нужен клиентский мод SoundManager; игроки без него пропускаются

### Установка

1. Положите `SoundManagerPlugin-<version>.jar` в папку `plugins`.
2. Запустите сервер; будет создан `plugins/SoundManagerPlugin/config.yml`.
3. Попросите игроков установить мод SoundManager.

### Команды и права

Список команд и прав приведён в английской части выше (команды `playlist` — для плейлистов). Времена в командах указываются в секундах, `end = -1` означает «играть до конца».

| Право | По умолчанию | Описание |
|---|---|---|
| `soundmanager.play` | op | `play`, `playat`, `playon` |
| `soundmanager.control` | op | `stop`, `pause`, `resume`, `seek`, `volume`, `pitch` |
| `soundmanager.check` | op | `check` |
| `soundmanager.reload` | op | `reload` |
| `soundmanager.playlist` | op | команды `playlist` |
| `soundmanager.admin` | op | Все права выше |

### Настройка

Файл: `plugins/SoundManagerPlugin/config.yml`

| Ключ | Тип | По умолчанию | Диапазон | Описание |
|---|---|---|---|---|
| `settings.language` | строка | `auto` | `ru`, `en`, `auto` | Язык сообщений; `auto` берёт язык клиента игрока, для консоли английский |

### Данные

Плагин создаёт `config.yml` и, после создания первого плейлиста, `playlists.json`; данные игроков не хранятся. Копируйте `playlists.json`, чтобы сохранить плейлисты; удалите его, чтобы убрать их все. Запущенные плейлисты не сохраняются: после перезапуска запустите их снова.

### Парный мод

Воспроизведение выполняет клиентский мод SoundManager. Без него игрок ничего не услышит, а команды его пропустят. Команда `/soundmanager check <игрок>` показывает, есть ли у игрока мод. Устанавливайте версию мода, соответствующую версии плагина.

### Решение проблем

- **«Пропущено (нет мода SoundManager)».** У игрока нет мода, или он отключил его аварийной командой мода.
- **«Некорректный id звука».** Используйте `namespace:path`, например `minecraft:music.creative` или `mypack:boss.theme`. Звук должен существовать в игре или в ресурспаке игрока.
- **Звук тихий или не слышен.** Игрок мог снизить громкость категории звука или задать предел громкости в моде.
- **Плейлист не меняется.** Играющий плейлист нельзя изменить или удалить: сначала остановите его.
- **Треки накладываются или между ними пауза.** Время трека считается по параметрам и указанной длине. Неверная `length` или событие с несколькими файлами разной длины дают неверное время; задайте конец явно.
- **Команда не видна.** Выдайте `soundmanager.admin` или одно из отдельных прав.

### Лицензия

All Rights Reserved.
