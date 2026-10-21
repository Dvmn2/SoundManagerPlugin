package net.dvmn2.soundmanagerplugin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.FinePosition;
import net.dvmn2.soundmanagerplugin.Lang;
import net.dvmn2.soundmanagerplugin.SoundManagerPlugin;
import net.dvmn2.soundmanagerplugin.SoundNetwork;
import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Все времена в командах — в СЕКУНДАХ (можно дробные: 12.5).
 * <pre>
 * /soundmanager play   &lt;игроки&gt; &lt;звук&gt;                  [start [end [fadein [fadeout [volume [pitch [category]]]]]]]
 * /soundmanager playat &lt;игроки&gt; &lt;звук&gt; &lt;x y z&gt;          [те же опции]
 * /soundmanager playon &lt;игроки&gt; &lt;звук&gt; &lt;сущность&gt;      [те же опции]
 * /soundmanager stop   &lt;игроки&gt; [&lt;звук&gt; [&lt;fade&gt;]]
 * /soundmanager pause|resume &lt;игроки&gt; [&lt;звук&gt;]
 * /soundmanager seek   &lt;игроки&gt; &lt;звук&gt; &lt;секунды&gt;
 * /soundmanager volume &lt;игроки&gt; &lt;звук&gt; &lt;громкость&gt; [&lt;секунд плавно&gt;]
 * /soundmanager pitch  &lt;игроки&gt; &lt;звук&gt; &lt;высота&gt; [&lt;секунд плавно&gt;]
 * /soundmanager check  &lt;игрок&gt;
 * /soundmanager reload
 * </pre>
 * end = -1 означает «играть до конца звука».
 */
public final class SoundCommand {

    private static final List<String> CATEGORIES = List.of(
            "master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice", "ui");

    private enum Target {GLOBAL, POSITION, ENTITY}

    private final SoundManagerPlugin plugin;

    public SoundCommand(SoundManagerPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("soundmanager")
                .requires(src -> src.getSender().hasPermission("soundmanager.admin"))
                .then(Commands.literal("play")
                        .then(targetsArg().then(soundArg()
                                .executes(ctx -> play(ctx, Target.GLOBAL))
                                .then(optionChain(ctx -> play(ctx, Target.GLOBAL))))))
                .then(Commands.literal("playat")
                        .then(targetsArg().then(soundArg()
                                .then(Commands.argument("pos", ArgumentTypes.finePosition())
                                        .executes(ctx -> play(ctx, Target.POSITION))
                                        .then(optionChain(ctx -> play(ctx, Target.POSITION)))))))
                .then(Commands.literal("playon")
                        .then(targetsArg().then(soundArg()
                                .then(Commands.argument("entity", ArgumentTypes.entity())
                                        .executes(ctx -> play(ctx, Target.ENTITY))
                                        .then(optionChain(ctx -> play(ctx, Target.ENTITY)))))))
                .then(Commands.literal("stop")
                        .then(targetsArg()
                                .executes(this::stop)
                                .then(soundArg()
                                        .executes(this::stop)
                                        .then(Commands.argument("fade", DoubleArgumentType.doubleArg(0))
                                                .executes(this::stop)))))
                .then(Commands.literal("pause")
                        .then(targetsArg()
                                .executes(ctx -> simple(ctx, (api, p, s) -> api.pause(p, s)))
                                .then(soundArg().executes(ctx -> simple(ctx, (api, p, s) -> api.pause(p, s))))))
                .then(Commands.literal("resume")
                        .then(targetsArg()
                                .executes(ctx -> simple(ctx, (api, p, s) -> api.resume(p, s)))
                                .then(soundArg().executes(ctx -> simple(ctx, (api, p, s) -> api.resume(p, s))))))
                .then(Commands.literal("seek")
                        .then(targetsArg().then(soundArg()
                                .then(Commands.argument("time", DoubleArgumentType.doubleArg(0))
                                        .executes(this::seek)))))
                .then(Commands.literal("volume")
                        .then(targetsArg().then(soundArg()
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0f, 10f))
                                        .executes(ctx -> level(ctx, true))
                                        .then(Commands.argument("ramp", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> level(ctx, true)))))))
                .then(Commands.literal("pitch")
                        .then(targetsArg().then(soundArg()
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.5f, 2f))
                                        .executes(ctx -> level(ctx, false))
                                        .then(Commands.argument("ramp", DoubleArgumentType.doubleArg(0))
                                                .executes(ctx -> level(ctx, false)))))))
                .then(Commands.literal("check")
                        .then(Commands.argument("targets", ArgumentTypes.player())
                                .executes(this::check)))
                .then(Commands.literal("reload").executes(ctx -> {
                    plugin.reloadAll();
                    info(ctx.getSource().getSender(), Lang.get(Lang.Msg.RELOAD_OK, ctx.getSource().getSender()));
                    return 1;
                }));
    }

    // ------------------------------------------------------------------
    // Аргументы
    // ------------------------------------------------------------------

    private RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetsArg() {
        return Commands.argument("targets", ArgumentTypes.players());
    }

    private RequiredArgumentBuilder<CommandSourceStack, Key> soundArg() {
        return Commands.argument("sound", ArgumentTypes.key());
    }

    /**
     * Цепочка необязательных позиционных аргументов:
     * start -> end -> fadein -> fadeout -> volume -> pitch -> category. Исполнитель висит на каждом звене.
     */
    private ArgumentBuilder<CommandSourceStack, ?> optionChain(Command<CommandSourceStack> exec) {
        RequiredArgumentBuilder<CommandSourceStack, String> category = Commands
                .argument("category", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    CATEGORIES.stream().filter(c -> c.startsWith(prefix)).forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(exec);

        RequiredArgumentBuilder<CommandSourceStack, Float> pitch = Commands
                .argument("pitch", FloatArgumentType.floatArg(0.5f, 2f)).executes(exec);
        pitch.then(category);

        RequiredArgumentBuilder<CommandSourceStack, Float> volume = Commands
                .argument("volume", FloatArgumentType.floatArg(0f, 10f)).executes(exec);
        volume.then(pitch);

        RequiredArgumentBuilder<CommandSourceStack, Double> fadeOut = Commands
                .argument("fadeout", DoubleArgumentType.doubleArg(0)).executes(exec);
        fadeOut.then(volume);

        RequiredArgumentBuilder<CommandSourceStack, Double> fadeIn = Commands
                .argument("fadein", DoubleArgumentType.doubleArg(0)).executes(exec);
        fadeIn.then(fadeOut);

        RequiredArgumentBuilder<CommandSourceStack, Double> end = Commands
                .argument("end", DoubleArgumentType.doubleArg(-1)).executes(exec);
        end.then(fadeIn);

        RequiredArgumentBuilder<CommandSourceStack, Double> start = Commands
                .argument("start", DoubleArgumentType.doubleArg(0)).executes(exec);
        start.then(end);

        return start;
    }

    // ------------------------------------------------------------------
    // Исполнители
    // ------------------------------------------------------------------

    private int play(CommandContext<CommandSourceStack> ctx, Target target) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players = players(ctx);
        if (players == null) {
            return 0;
        }
        String sound = ctx.getArgument("sound", Key.class).asString();

        SoundOptions options;
        try {
            options = new SoundOptions(
                    opt(ctx, "category", String.class, "master"),
                    opt(ctx, "volume", Float.class, 1f),
                    opt(ctx, "pitch", Float.class, 1f),
                    toMs(opt(ctx, "start", Double.class, 0.0)),
                    toMs(opt(ctx, "end", Double.class, -1.0)),
                    toMs(opt(ctx, "fadein", Double.class, 0.0)),
                    toMs(opt(ctx, "fadeout", Double.class, 0.0)));
        } catch (IllegalArgumentException ex) {
            error(sender, Lang.get(Lang.Msg.BAD_RANGE, sender));
            return 0;
        }

        FinePosition pos = null;
        Entity entity = null;
        if (target == Target.POSITION) {
            pos = ctx.getArgument("pos", FinePositionResolver.class).resolve(ctx.getSource());
        } else if (target == Target.ENTITY) {
            List<Entity> found = ctx.getArgument("entity", EntitySelectorArgumentResolver.class).resolve(ctx.getSource());
            if (found.isEmpty()) {
                error(sender, Lang.get(Lang.Msg.NO_ENTITY, sender));
                return 0;
            }
            entity = found.get(0);
        }

        SoundManagerApi api = plugin.getApi();
        int sent = 0;
        int skipped = 0;
        for (Player player : players) {
            boolean ok = switch (target) {
                case GLOBAL -> api.playGlobal(player, sound, options);
                case POSITION -> api.playAt(player, sound,
                        new Location(player.getWorld(), pos.x(), pos.y(), pos.z()), options);
                case ENTITY -> api.playOnEntity(player, sound, entity, options);
            };
            if (ok) {
                sent++;
            } else {
                skipped++;
            }
        }
        report(sender, sent, skipped);
        return sent;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        long fade = toMs(opt(ctx, "fade", Double.class, 0.0));
        Key sound = opt(ctx, "sound", Key.class, null);
        return control(ctx, (api, p) -> api.stop(p, sound == null ? null : sound.asString(), fade));
    }

    private int seek(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String sound = ctx.getArgument("sound", Key.class).asString();
        long ms = toMs(ctx.getArgument("time", Double.class));
        return control(ctx, (api, p) -> api.seek(p, sound, ms));
    }

    private int level(CommandContext<CommandSourceStack> ctx, boolean volume) throws CommandSyntaxException {
        String sound = ctx.getArgument("sound", Key.class).asString();
        float value = ctx.getArgument("value", Float.class);
        long ramp = toMs(opt(ctx, "ramp", Double.class, 0.0));
        return control(ctx, (api, p) -> volume
                ? api.setVolume(p, sound, value, ramp)
                : api.setPitch(p, sound, value, ramp));
    }

    private interface SimpleAction {
        boolean run(SoundManagerApi api, Player player, String sound);
    }

    private int simple(CommandContext<CommandSourceStack> ctx, SimpleAction action) throws CommandSyntaxException {
        Key key = opt(ctx, "sound", Key.class, null);
        String sound = key == null ? null : key.asString();
        return control(ctx, (api, p) -> action.run(api, p, sound));
    }

    private interface PlayerAction {
        boolean run(SoundManagerApi api, Player player);
    }

    private int control(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players = players(ctx);
        if (players == null) {
            return 0;
        }
        int sent = 0;
        int skipped = 0;
        for (Player player : players) {
            if (action.run(plugin.getApi(), player)) {
                sent++;
            } else {
                skipped++;
            }
        }
        report(sender, sent, skipped);
        return sent;
    }

    private int check(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players = players(ctx);
        if (players == null) {
            return 0;
        }
        for (Player player : players) {
            boolean has = SoundNetwork.hasMod(player);
            Component text = Component.text(
                    Lang.get(has ? Lang.Msg.CHECK_YES : Lang.Msg.CHECK_NO, sender, player.getName()),
                    has ? NamedTextColor.GREEN : NamedTextColor.RED);
            sender.sendMessage(text);
        }
        return players.size();
    }

    // ------------------------------------------------------------------
    // Утилиты
    // ------------------------------------------------------------------

    /** Игроки из аргумента targets; null (и сообщение об ошибке), если никого не нашлось. */
    private List<Player> players(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> list = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (list.isEmpty()) {
            error(ctx.getSource().getSender(), Lang.get(Lang.Msg.NO_TARGETS, ctx.getSource().getSender()));
            return null;
        }
        return list;
    }

    private static <T> T opt(CommandContext<CommandSourceStack> ctx, String name, Class<T> type, T def) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException ex) {
            return def; // необязательный аргумент не указан
        }
    }

    private static long toMs(double seconds) {
        return seconds < 0 ? -1L : Math.round(seconds * 1000.0);
    }

    private void report(CommandSender sender, int sent, int skipped) {
        Component text = Component.text(Lang.get(Lang.Msg.SENT, sender, sent, skipped),
                skipped == 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW);
        sender.sendMessage(text);
    }

    private void info(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GREEN));
    }

    private void error(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.RED));
    }
}
