# SoundManagerPlugin (Paper 1.21.11)

Плагин командует клиентским модом SoundManager. Если у игрока нет мода — команда пропускается
(ванильный звук НЕ проигрывается, игрок не кикается).

## Команды (право `soundmanager.admin`)
Все времена — в секундах (можно дробные). `end = -1` — играть до конца звука.

```
/soundmanager play   <игроки> <звук>                 [start [end [fadein [fadeout [volume [pitch [category]]]]]]]
/soundmanager playat <игроки> <звук> <x y z>         [те же опции]
/soundmanager playon <игроки> <звук> <сущность>      [те же опции]
/soundmanager stop   <игроки> [<звук> [<fade>]]
/soundmanager pause  <игроки> [<звук>]
/soundmanager resume <игроки> [<звук>]
/soundmanager seek   <игроки> <звук> <секунды>
/soundmanager volume <игроки> <звук> <0..10> [<секунд плавно>]
/soundmanager pitch  <игроки> <звук> <0.5..2> [<секунд плавно>]
/soundmanager check  <игрок>       # есть ли у игрока мод
/soundmanager reload
```
Пример: `/soundmanager play @a mypack:boss.theme 12.5 30 1 2 1 1 music` — играть с 12.5 с до 30 с,
нарастание 1 с, затухание последних 2 с, категория music.

`<звук>` — id из sounds.json. Управляющие команды действуют на все экземпляры звука с этим id;
без `<звук>` (stop/pause/resume) — на все управляемые звуки игрока.

## API для других плагинов
```java
SoundManagerApi api = Bukkit.getServicesManager().load(SoundManagerApi.class);
api.playGlobal(player, "mypack:boss.theme",
        SoundOptions.defaults().withCategory("music").withStart(12_500).withEnd(30_000).withFadeOut(2000));
api.stop(player, "mypack:boss.theme", 1500);
```
Методы возвращают `false`, если у игрока нет мода.

## Сборка
`./gradlew build` (build.gradle.kts такой же, как у ResourceControlPlugin). Скопируйте `gradlew` и `gradle/` оттуда.
Не компилировалось в среде без доступа к Maven; синтаксис проверен. Возможные места правки:
`ArgumentTypes.key()` / `finePosition()` / `entity()` / `players()` в `SoundCommand`.
