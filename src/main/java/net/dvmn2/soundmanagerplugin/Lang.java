package net.dvmn2.soundmanagerplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Localised player-visible messages in MiniMessage format. Russian and English variants of every key are checked
 * when the class is loaded. Values from players and commands are inserted only through {@link TagResolver}s.
 */
public final class Lang {

    public enum Key {
        NO_TARGETS,
        NO_ENTITY,
        BAD_POSITION,
        BAD_SOUND,
        BAD_CATEGORY,
        BAD_RANGE,
        SENT,
        SENT_PARTIAL,
        RELOADED,
        CHECK_YES,
        CHECK_NO,
        PL_CREATED,
        PL_DELETED,
        PL_EXISTS,
        PL_NOT_FOUND,
        PL_BAD_NAME,
        PL_LIMIT,
        PL_TRACK_LIMIT,
        PL_RUNNING,
        PL_BAD_INDEX,
        PL_NEEDS_LENGTH,
        PL_TRACK_ADDED,
        PL_TRACK_REMOVED,
        PL_TRACK_MOVED,
        PL_LOOP_SET,
        PL_SHUFFLE_SET,
        PL_STARTED,
        PL_STARTED_PARTIAL,
        PL_EMPTY,
        PL_NO_PLAYERS,
        PL_STOPPED,
        PL_SKIPPED,
        PL_PAUSED,
        PL_RESUMED,
        PL_NOT_RUNNING,
        PL_WRONG_STATE,
        PL_LIST_NONE,
        PL_LIST_STOPPED,
        PL_LIST_PLAYING,
        PL_LIST_PAUSED
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static volatile String language = "auto";

    static {
        put(Key.NO_TARGETS,
                "<red>Не удалось найти игроков.",
                "<red>No matching players found.");
        put(Key.NO_ENTITY,
                "<red>Не удалось найти сущность.",
                "<red>Entity not found.");
        put(Key.BAD_POSITION,
                "<red>Не удалось определить позицию.",
                "<red>Could not resolve the position.");
        put(Key.BAD_SOUND,
                "<red>Некорректный id звука.",
                "<red>Invalid sound id.");
        put(Key.BAD_CATEGORY,
                "<red>Неизвестная категория звука: <category>.",
                "<red>Unknown sound category: <category>.");
        put(Key.BAD_RANGE,
                "<red>Конец (end) должен быть позже начала (start).",
                "<red>End must be later than start.");
        put(Key.SENT,
                "<green>Отправлено игрокам: <sent>.",
                "<green>Sent to <sent> player(s).");
        put(Key.SENT_PARTIAL,
                "<yellow>Отправлено игрокам: <sent>. "
                        + "Пропущено (нет мода SoundManager): <skipped>.",
                "<yellow>Sent to <sent> player(s). Skipped (no SoundManager mod): <skipped>.");
        put(Key.RELOADED,
                "<green>Конфигурация перезагружена.",
                "<green>Configuration reloaded.");
        put(Key.CHECK_YES,
                "<green>У игрока <player> установлен мод SoundManager.",
                "<green>Player <player> has the SoundManager mod.");
        put(Key.CHECK_NO,
                "<red>У игрока <player> НЕТ мода SoundManager.",
                "<red>Player <player> does NOT have the SoundManager mod.");
        put(Key.PL_CREATED,
                "<green>Плейлист <name> создан.",
                "<green>Playlist <name> created.");
        put(Key.PL_DELETED,
                "<green>Плейлист <name> удалён.",
                "<green>Playlist <name> deleted.");
        put(Key.PL_EXISTS,
                "<red>Плейлист <name> уже существует.",
                "<red>Playlist <name> already exists.");
        put(Key.PL_NOT_FOUND,
                "<red>Плейлист <name> не найден.",
                "<red>Playlist <name> not found.");
        put(Key.PL_BAD_NAME,
                "<red>Имя плейлиста: от 1 до <max> символов, без пробелов по краям и управляющих символов.",
                "<red>A playlist name has 1 to <max> characters, no spaces at the ends and no control characters.");
        put(Key.PL_LIMIT,
                "<red>Достигнут предел плейлистов: <max>.",
                "<red>The playlist limit is reached: <max>.");
        put(Key.PL_TRACK_LIMIT,
                "<red>В плейлисте не может быть больше <max> треков.",
                "<red>A playlist cannot have more than <max> tracks.");
        put(Key.PL_RUNNING,
                "<red>Плейлист <name> сейчас играет. Остановите его, чтобы изменить или удалить.",
                "<red>Playlist <name> is playing. Stop it to edit or delete it.");
        put(Key.PL_BAD_INDEX,
                "<red>Нет трека с таким номером.",
                "<red>There is no track with that number.");
        put(Key.PL_NEEDS_LENGTH,
                "<red>Для трека без конца (end = -1) укажите длину звука в секундах, большую, чем start.",
                "<red>A track without an end (end = -1) needs the sound length in seconds, greater than start.");
        put(Key.PL_TRACK_ADDED,
                "<green>Трек добавлен в плейлист <name>.",
                "<green>Track added to playlist <name>.");
        put(Key.PL_TRACK_REMOVED,
                "<green>Трек удалён из плейлиста <name>.",
                "<green>Track removed from playlist <name>.");
        put(Key.PL_TRACK_MOVED,
                "<green>Трек перемещён в плейлисте <name>.",
                "<green>Track moved in playlist <name>.");
        put(Key.PL_LOOP_SET,
                "<green>Плейлист <name>: повтор <value>.",
                "<green>Playlist <name>: loop <value>.");
        put(Key.PL_SHUFFLE_SET,
                "<green>Плейлист <name>: перемешивание <value> (действует со следующего круга).",
                "<green>Playlist <name>: shuffle <value> (applies from the next cycle).");
        put(Key.PL_STARTED,
                "<green>Плейлист <name> запущен для игроков: <sent>.",
                "<green>Playlist <name> started for <sent> player(s).");
        put(Key.PL_STARTED_PARTIAL,
                "<yellow>Плейлист <name> запущен для игроков: <sent>. "
                        + "Пропущено (нет мода SoundManager): <skipped>.",
                "<yellow>Playlist <name> started for <sent> player(s). Skipped (no SoundManager mod): <skipped>.");
        put(Key.PL_EMPTY,
                "<red>В плейлисте <name> нет треков.",
                "<red>Playlist <name> has no tracks.");
        put(Key.PL_NO_PLAYERS,
                "<red>Нет игроков с модом SoundManager среди выбранных.",
                "<red>None of the selected players has the SoundManager mod.");
        put(Key.PL_STOPPED,
                "<green>Плейлист <name> остановлен.",
                "<green>Playlist <name> stopped.");
        put(Key.PL_SKIPPED,
                "<green>Плейлист <name>: следующий трек.",
                "<green>Playlist <name>: next track.");
        put(Key.PL_PAUSED,
                "<green>Плейлист <name> на паузе.",
                "<green>Playlist <name> paused.");
        put(Key.PL_RESUMED,
                "<green>Плейлист <name> продолжен.",
                "<green>Playlist <name> resumed.");
        put(Key.PL_NOT_RUNNING,
                "<red>Плейлист <name> не запущен.",
                "<red>Playlist <name> is not running.");
        put(Key.PL_WRONG_STATE,
                "<red>Плейлист <name> уже в этом состоянии.",
                "<red>Playlist <name> is already in that state.");
        put(Key.PL_LIST_NONE,
                "<yellow>Плейлистов пока нет.",
                "<yellow>There are no playlists yet.");
        put(Key.PL_LIST_STOPPED,
                "<gray><name>: треков <tracks>.",
                "<gray><name>: <tracks> track(s).");
        put(Key.PL_LIST_PLAYING,
                "<green><name>: треков <tracks>, играет трек <current>.",
                "<green><name>: <tracks> track(s), playing track <current>.");
        put(Key.PL_LIST_PAUSED,
                "<yellow><name>: треков <tracks>, пауза на треке <current>.",
                "<yellow><name>: <tracks> track(s), paused at track <current>.");
        verify();
    }

    private Lang() {
    }

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    private static void verify() {
        for (Key key : Key.values()) {
            if (!RU.containsKey(key) || !EN.containsKey(key)) {
                throw new IllegalStateException("Missing translation for " + key);
            }
        }
    }

    /** Sets the language: {@code ru}, {@code en} or {@code auto} (blank is treated as {@code auto}). */
    public static void setLanguage(String value) {
        language = value == null || value.isBlank() ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    /** Returns the message for the sender's language with the given placeholders applied. */
    public static Component get(Key key, CommandSender sender, TagResolver... resolvers) {
        return MM.deserialize(table(sender).get(key), resolvers);
    }

    private static Map<Key, String> table(CommandSender sender) {
        return switch (language) {
            case "ru" -> RU;
            case "en" -> EN;
            default -> sender instanceof Player player
                    && "ru".equalsIgnoreCase(player.locale().getLanguage()) ? RU : EN;
        };
    }
}
