package net.dvmn2.soundmanagerplugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class Lang {

    public enum Msg {
        NO_TARGETS,
        SENT,
        NO_ENTITY,
        BAD_RANGE,
        RELOAD_OK,
        CHECK_YES,
        CHECK_NO
    }

    private static final Map<Msg, String> RU = new EnumMap<>(Msg.class);
    private static final Map<Msg, String> EN = new EnumMap<>(Msg.class);

    private static String setting = "auto";

    private static void put(Msg key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    static {
        put(Msg.NO_TARGETS, "Не удалось найти игроков.", "No matching players found.");
        put(Msg.SENT, "Отправлено игрокам: %d. Пропущено (нет мода SoundManager): %d.",
                "Sent to %d player(s). Skipped (no SoundManager mod): %d.");
        put(Msg.NO_ENTITY, "Не удалось найти сущность.", "Entity not found.");
        put(Msg.BAD_RANGE, "Конец (end) должен быть позже начала (start).", "End must be later than start.");
        put(Msg.RELOAD_OK, "Конфиг перезагружен.", "Config reloaded.");
        put(Msg.CHECK_YES, "У игрока %s установлен мод SoundManager.", "Player %s has the SoundManager mod.");
        put(Msg.CHECK_NO, "У игрока %s НЕТ мода SoundManager.", "Player %s does NOT have the SoundManager mod.");
    }

    private Lang() {
    }

    public static void setLanguage(String value) {
        setting = value == null ? "auto" : value.toLowerCase(Locale.ROOT);
    }

    public static String get(Msg key, CommandSender sender, Object... args) {
        boolean ru = switch (setting) {
            case "ru" -> true;
            case "en" -> false;
            default -> sender instanceof Player p && "ru".equals(p.locale().getLanguage());
        };
        String template = (ru ? RU : EN).get(key);
        return args.length == 0 ? template : String.format(template, args);
    }
}
