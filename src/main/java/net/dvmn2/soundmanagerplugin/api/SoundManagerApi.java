package net.dvmn2.soundmanagerplugin.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * API для других плагинов. Получение:
 * <pre>
 * SoundManagerApi api = Bukkit.getServicesManager().load(SoundManagerApi.class);
 * api.playGlobal(player, "mypack:boss.theme",
 *         SoundOptions.defaults().withCategory("music").withStart(12_500).withEnd(30_000).withFadeOut(1500));
 * </pre>
 * Вызывать из основного потока сервера.
 * <p>
 * Все методы возвращают {@code false}, если у игрока нет клиентского мода SoundManager
 * (в этом случае ничего не отправляется и ванильный звук НЕ проигрывается).
 * Параметр {@code sound} — id из sounds.json ({@code namespace:path}; без namespace подразумевается minecraft).
 * Некорректный id → {@link IllegalArgumentException}.
 * <p>
 * Управляющие методы ({@code stop}, {@code pause} ...) действуют на все экземпляры звука с таким id;
 * {@code sound = null} — на все управляемые звуки игрока.
 */
public interface SoundManagerApi {

    /** Есть ли у игрока клиентский мод SoundManager. */
    boolean hasMod(Player player);

    /** Глобальный звук: слышен одинаково везде, не зависит от позиции. */
    boolean playGlobal(Player player, String sound, SoundOptions options);

    /** Звук из точки мира (затухает с расстоянием). */
    boolean playAt(Player player, String sound, Location location, SoundOptions options);

    /** Звук, привязанный к сущности (следует за ней; прекращается, если сущность пропала у клиента). */
    boolean playOnEntity(Player player, String sound, Entity entity, SoundOptions options);

    /** Остановить с затуханием fadeMs (0 = сразу). */
    boolean stop(Player player, String sound, long fadeMs);

    /** Поставить на паузу (позиция запоминается). */
    boolean pause(Player player, String sound);

    /** Продолжить с запомненной позиции. */
    boolean resume(Player player, String sound);

    /** Перемотать на позицию (мс); для звуков на паузе — сменить позицию продолжения. */
    boolean seek(Player player, String sound, long positionMs);

    /** Изменить громкость (0..10); rampMs > 0 — плавно. */
    boolean setVolume(Player player, String sound, float volume, long rampMs);

    /** Изменить высоту (0.5..2.0); rampMs > 0 — плавно. */
    boolean setPitch(Player player, String sound, float pitch, long rampMs);
}
