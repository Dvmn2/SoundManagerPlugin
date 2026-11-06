package net.dvmn2.soundmanagerplugin.command;

import static net.dvmn2.soundmanagerplugin.command.CommandSupport.ARG_SOUND;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.ARG_TARGETS;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.has;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.optionChain;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.parseOptions;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.resolvePlayers;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.toMs;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.FinePosition;
import net.dvmn2.soundmanagerplugin.Lang;
import net.dvmn2.soundmanagerplugin.Permissions;
import net.dvmn2.soundmanagerplugin.PlaylistManager;
import net.dvmn2.soundmanagerplugin.PlaylistManager.EditResult;
import net.dvmn2.soundmanagerplugin.PlaylistManager.RunResult;
import net.dvmn2.soundmanagerplugin.PlaylistManager.StartOutcome;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.dvmn2.soundmanagerplugin.model.Playlist;
import net.dvmn2.soundmanagerplugin.model.PlaylistTrack;
import net.dvmn2.soundmanagerplugin.util.SoundIds;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The {@code playlist} subtree of {@code /soundmanager}: edits playlists and runs them for players. The root
 * literal stays in {@link SoundManagerCommand}; the subtree has its own class so that neither grows too large.
 * Times are in SECONDS. A track is a copy of the play options, so it does not change when a preset of a client
 * changes.
 * <pre>
 * /soundmanager playlist list
 * /soundmanager playlist create &lt;name&gt;
 * /soundmanager playlist delete &lt;name&gt;
 * /soundmanager playlist add &lt;name&gt; play &lt;sound&gt; &lt;length&gt; [options]
 * /soundmanager playlist add &lt;name&gt; playat &lt;sound&gt; &lt;x y z&gt; &lt;length&gt; [options]
 * /soundmanager playlist remove &lt;name&gt; &lt;number&gt;
 * /soundmanager playlist move &lt;name&gt; &lt;from&gt; &lt;to&gt;
 * /soundmanager playlist set &lt;name&gt; loop|shuffle &lt;true|false&gt;
 * /soundmanager playlist start &lt;name&gt; &lt;players&gt;
 * /soundmanager playlist stop|next|pause|resume &lt;name&gt;
 *
 * length:  length of the whole sound file in seconds; -1 if unknown (then options must set an end)
 * options: [start [end [fadein [fadeout [volume [pitch [category]]]]]]]
 * </pre>
 */
final class PlaylistNode {

    private static final String ARG_NAME = "name";
    private static final String ARG_POSITION = "pos";
    private static final String ARG_LENGTH = "length";
    private static final String ARG_NUMBER = "number";
    private static final String ARG_FROM = "from";
    private static final String ARG_TO = "to";
    private static final String ARG_ENABLED = "enabled";

    private static final String QUOTE = "\"";
    private static final String SAFE_WORD = "[A-Za-z0-9_.+-]+";

    private final PlaylistManager manager;

    PlaylistNode(PlaylistManager manager) {
        this.manager = manager;
    }

    ArgumentBuilder<CommandSourceStack, ?> build() {
        return Commands.literal("playlist")
                .requires(src -> has(src, Permissions.PLAYLIST))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("create").then(nameArg(false).executes(this::create)))
                .then(Commands.literal("delete").then(nameArg(true).executes(this::delete)))
                .then(addNode())
                .then(Commands.literal("remove").then(nameArg(true)
                        .then(Commands.argument(ARG_NUMBER, IntegerArgumentType.integer(1))
                                .executes(this::remove))))
                .then(Commands.literal("move").then(nameArg(true)
                        .then(Commands.argument(ARG_FROM, IntegerArgumentType.integer(1))
                                .then(Commands.argument(ARG_TO, IntegerArgumentType.integer(1))
                                        .executes(this::move)))))
                .then(Commands.literal("set").then(nameArg(true)
                        .then(Commands.literal("loop").then(enabledArg().executes(this::setLoop)))
                        .then(Commands.literal("shuffle").then(enabledArg().executes(this::setShuffle)))))
                .then(Commands.literal("start").then(nameArg(true)
                        .then(Commands.argument(ARG_TARGETS, ArgumentTypes.players())
                                .executes(this::start))))
                .then(Commands.literal("stop").then(nameArg(true).executes(this::stop)))
                .then(Commands.literal("next").then(nameArg(true).executes(this::next)))
                .then(Commands.literal("pause").then(nameArg(true).executes(this::pause)))
                .then(Commands.literal("resume").then(nameArg(true).executes(this::resume)));
    }

    // ---------- Tree ----------

    private ArgumentBuilder<CommandSourceStack, ?> addNode() {
        Command<CommandSourceStack> addGlobal = ctx -> add(ctx, PlaylistTrack.Mode.GLOBAL);
        Command<CommandSourceStack> addAt = ctx -> add(ctx, PlaylistTrack.Mode.POSITION);
        return Commands.literal("add").then(nameArg(true)
                .then(Commands.literal("play")
                        .then(Commands.argument(ARG_SOUND, ArgumentTypes.key())
                                .then(lengthArg(addGlobal))))
                .then(Commands.literal("playat")
                        .then(Commands.argument(ARG_SOUND, ArgumentTypes.key())
                                .then(Commands.argument(ARG_POSITION, ArgumentTypes.finePosition())
                                        .then(lengthArg(addAt))))));
    }

    /** The length argument with the optional play options after it. */
    private static ArgumentBuilder<CommandSourceStack, ?> lengthArg(Command<CommandSourceStack> exec) {
        return Commands.argument(ARG_LENGTH, DoubleArgumentType.doubleArg(-1))
                .executes(exec)
                .then(optionChain(exec));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Boolean> enabledArg() {
        return Commands.argument(ARG_ENABLED, BoolArgumentType.bool());
    }

    /** A playlist name: a word or a quoted phrase; suggests existing names when {@code existing} is true. */
    private RequiredArgumentBuilder<CommandSourceStack, String> nameArg(boolean existing) {
        RequiredArgumentBuilder<CommandSourceStack, String> argument =
                Commands.argument(ARG_NAME, StringArgumentType.string());
        if (existing) {
            argument.suggests((ctx, builder) -> {
                String typed = builder.getRemaining().replace(QUOTE, "").toLowerCase(Locale.ROOT);
                for (Playlist playlist : manager.list()) {
                    if (playlist.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        builder.suggest(quoteIfNeeded(playlist.getName()));
                    }
                }
                return builder.buildFuture();
            });
        }
        return argument;
    }

    private static String quoteIfNeeded(String name) {
        if (name.matches(SAFE_WORD)) {
            return name;
        }
        return QUOTE + name.replace("\\", "\\\\").replace(QUOTE, "\\" + QUOTE) + QUOTE;
    }

    // ---------- Editing ----------

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Playlist> playlists = manager.list();
        if (playlists.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.PL_LIST_NONE, sender));
            return 0;
        }
        for (Playlist playlist : playlists) {
            Lang.Key key = switch (manager.getState(playlist)) {
                case PLAYING -> Lang.Key.PL_LIST_PLAYING;
                case PAUSED -> Lang.Key.PL_LIST_PAUSED;
                case STOPPED -> Lang.Key.PL_LIST_STOPPED;
            };
            sender.sendMessage(Lang.get(key, sender,
                    Placeholder.unparsed("name", playlist.getName()),
                    Placeholder.unparsed("tracks", String.valueOf(playlist.getTracks().size())),
                    Placeholder.unparsed("current", String.valueOf(manager.getCurrent(playlist) + 1))));
        }
        return playlists.size();
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        return edit(ctx, manager::create, Lang.Key.PL_CREATED);
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        return edit(ctx, manager::delete, Lang.Key.PL_DELETED);
    }

    private int remove(CommandContext<CommandSourceStack> ctx) {
        int number = ctx.getArgument(ARG_NUMBER, Integer.class);
        return edit(ctx, name -> manager.removeTrack(name, number), Lang.Key.PL_TRACK_REMOVED);
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        int from = ctx.getArgument(ARG_FROM, Integer.class);
        int to = ctx.getArgument(ARG_TO, Integer.class);
        return edit(ctx, name -> manager.moveTrack(name, from, to), Lang.Key.PL_TRACK_MOVED);
    }

    private int setLoop(CommandContext<CommandSourceStack> ctx) {
        boolean value = ctx.getArgument(ARG_ENABLED, Boolean.class);
        return edit(ctx, name -> manager.setLoop(name, value), Lang.Key.PL_LOOP_SET, onOff(value));
    }

    private int setShuffle(CommandContext<CommandSourceStack> ctx) {
        boolean value = ctx.getArgument(ARG_ENABLED, Boolean.class);
        return edit(ctx, name -> manager.setShuffle(name, value), Lang.Key.PL_SHUFFLE_SET, onOff(value));
    }

    private int add(CommandContext<CommandSourceStack> ctx, PlaylistTrack.Mode mode) {
        CommandSender sender = ctx.getSource().getSender();
        String sound = SoundIds.normalize(ctx.getArgument(ARG_SOUND, Key.class).asString());
        if (sound == null) {
            sender.sendMessage(Lang.get(Lang.Key.BAD_SOUND, sender));
            return 0;
        }
        SoundOptions options = parseOptions(ctx);
        if (options == null) {
            return 0;
        }
        long lengthMs = toMs(ctx.getArgument(ARG_LENGTH, Double.class));
        PlaylistTrack track = createTrack(ctx, mode, sound, options, lengthMs);
        if (track == null) {
            return 0;
        }
        return edit(ctx, name -> manager.addTrack(name, track), Lang.Key.PL_TRACK_ADDED);
    }

    /** Builds the track; sends an error and returns null if the position or the length is invalid. */
    @Nullable
    private static PlaylistTrack createTrack(CommandContext<CommandSourceStack> ctx, PlaylistTrack.Mode mode,
                                             String sound, SoundOptions options, long lengthMs) {
        CommandSender sender = ctx.getSource().getSender();
        double x = 0;
        double y = 0;
        double z = 0;
        if (mode == PlaylistTrack.Mode.POSITION) {
            FinePosition position = resolvePosition(ctx);
            if (position == null) {
                return null;
            }
            x = position.x();
            y = position.y();
            z = position.z();
        }
        try {
            return new PlaylistTrack(mode, sound, x, y, z, options, lengthMs);
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Lang.get(Lang.Key.PL_NEEDS_LENGTH, sender));
            return null;
        }
    }

    /** Resolves the position argument; sends an error and returns null if it cannot be resolved. */
    @Nullable
    private static FinePosition resolvePosition(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument(ARG_POSITION, FinePositionResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            CommandSender sender = ctx.getSource().getSender();
            sender.sendMessage(Lang.get(Lang.Key.BAD_POSITION, sender));
            return null;
        }
    }

    // ---------- Running ----------

    private int start(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = playlistName(ctx);
        List<Player> targets = resolvePlayers(ctx);
        if (targets.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.NO_TARGETS, sender));
            return 0;
        }
        StartOutcome outcome = manager.start(name, targets);
        TagResolver[] placeholders = {
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("sent", String.valueOf(outcome.players())),
                Placeholder.unparsed("skipped", String.valueOf(outcome.skipped()))
        };
        switch (outcome.result()) {
            case STARTED -> {
                Lang.Key key = outcome.skipped() == 0 ? Lang.Key.PL_STARTED : Lang.Key.PL_STARTED_PARTIAL;
                sender.sendMessage(Lang.get(key, sender, placeholders));
                return outcome.players();
            }
            case NOT_FOUND -> sender.sendMessage(Lang.get(Lang.Key.PL_NOT_FOUND, sender, placeholders));
            case EMPTY -> sender.sendMessage(Lang.get(Lang.Key.PL_EMPTY, sender, placeholders));
            case NO_PLAYERS -> sender.sendMessage(Lang.get(Lang.Key.PL_NO_PLAYERS, sender, placeholders));
        }
        return 0;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, manager::stop, Lang.Key.PL_STOPPED);
    }

    private int next(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, manager::next, Lang.Key.PL_SKIPPED);
    }

    private int pause(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, manager::pause, Lang.Key.PL_PAUSED);
    }

    private int resume(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, manager::resume, Lang.Key.PL_RESUMED);
    }

    // ---------- Reporting ----------

    private int edit(CommandContext<CommandSourceStack> ctx, Function<String, EditResult> action, Lang.Key success) {
        return edit(ctx, action, success, null);
    }

    private int edit(CommandContext<CommandSourceStack> ctx, Function<String, EditResult> action,
                     Lang.Key success, @Nullable String value) {
        CommandSender sender = ctx.getSource().getSender();
        String name = playlistName(ctx);
        EditResult result = action.apply(name);
        TagResolver[] placeholders = {
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("value", value == null ? "" : value),
                Placeholder.unparsed("max", String.valueOf(maxFor(result)))
        };
        Lang.Key key = switch (result) {
            case OK -> success;
            case NOT_FOUND -> Lang.Key.PL_NOT_FOUND;
            case EXISTS -> Lang.Key.PL_EXISTS;
            case BAD_NAME -> Lang.Key.PL_BAD_NAME;
            case LIMIT -> Lang.Key.PL_LIMIT;
            case TRACK_LIMIT -> Lang.Key.PL_TRACK_LIMIT;
            case RUNNING -> Lang.Key.PL_RUNNING;
            case BAD_INDEX -> Lang.Key.PL_BAD_INDEX;
        };
        sender.sendMessage(Lang.get(key, sender, placeholders));
        return result == EditResult.OK ? Command.SINGLE_SUCCESS : 0;
    }

    private int run(CommandContext<CommandSourceStack> ctx, Function<String, RunResult> action, Lang.Key success) {
        CommandSender sender = ctx.getSource().getSender();
        String name = playlistName(ctx);
        RunResult result = action.apply(name);
        Lang.Key key = switch (result) {
            case OK -> success;
            case NOT_FOUND -> Lang.Key.PL_NOT_FOUND;
            case NOT_RUNNING -> Lang.Key.PL_NOT_RUNNING;
            case WRONG_STATE -> Lang.Key.PL_WRONG_STATE;
        };
        sender.sendMessage(Lang.get(key, sender, Placeholder.unparsed("name", name)));
        return result == RunResult.OK ? Command.SINGLE_SUCCESS : 0;
    }

    private static int maxFor(EditResult result) {
        return switch (result) {
            case BAD_NAME -> Playlist.MAX_NAME_LENGTH;
            case LIMIT -> PlaylistManager.MAX_PLAYLISTS;
            case TRACK_LIMIT -> Playlist.MAX_TRACKS;
            default -> 0;
        };
    }

    private static String playlistName(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument(ARG_NAME, String.class);
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }
}
