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
        CHECK_NO
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
