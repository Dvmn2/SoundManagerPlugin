package net.dvmn2.soundmanagerplugin;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Immutable snapshot of {@code config.yml}.
 *
 * @param language message language: {@code ru}, {@code en} or {@code auto}
 */
public record SoundManagerSettings(String language) {

    private static final String ROOT = "settings.";
    private static final String DEFAULT_LANGUAGE = "auto";
    private static final Set<String> LANGUAGES = Set.of("ru", "en", DEFAULT_LANGUAGE);

    /** Reads the settings; invalid values are reported to the log and replaced by defaults. */
    public static SoundManagerSettings parse(ConfigurationSection config, Logger log) {
        String language = config.getString(ROOT + "language", DEFAULT_LANGUAGE)
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!LANGUAGES.contains(language)) {
            log.warning("settings.language must be ru, en or auto, got '" + language
                    + "'; using " + DEFAULT_LANGUAGE);
            language = DEFAULT_LANGUAGE;
        }
        return new SoundManagerSettings(language);
    }
}
