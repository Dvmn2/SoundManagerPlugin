package net.dvmn2.soundmanagerplugin.command;

import static net.dvmn2.soundmanagerplugin.command.CommandSupport.ARG_SOUND;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.ARG_TARGETS;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.has;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.hasAny;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.opt;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.optionChain;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.parseOptions;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.resolvePlayers;
import static net.dvmn2.soundmanagerplugin.command.CommandSupport.toMs;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.math.FinePosition;
import net.dvmn2.soundmanagerplugin.Lang;
import net.dvmn2.soundmanagerplugin.Permissions;
import net.dvmn2.soundmanagerplugin.PlaylistManager;
import net.dvmn2.soundmanagerplugin.SoundManagerPlugin;
import net.dvmn2.soundmanagerplugin.api.SoundManagerApi;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The {@code /soundmanager} command. All times in commands are in SECONDS (fractions allowed, e.g. 12.5);
 * {@code end = -1} means "play to the end of the sound".
 * <pre>
 * /soundmanager play   &lt;players&gt; &lt;sound&gt; [options]
 * /soundmanager playat &lt;players&gt; &lt;sound&gt; &lt;x y z&gt; [options]
 * /soundmanager playon &lt;players&gt; &lt;sound&gt; &lt;entity&gt; [options]
 * /soundmanager stop   &lt;players&gt; [&lt;sound&gt; [&lt;fade&gt;]]
 * /soundmanager pause|resume &lt;players&gt; [&lt;sound&gt;]
 * /soundmanager seek   &lt;players&gt; &lt;sound&gt; &lt;seconds&gt;
 * /soundmanager volume &lt;players&gt; &lt;sound&gt; &lt;volume&gt; [&lt;ramp seconds&gt;]
 * /soundmanager pitch  &lt;players&gt; &lt;sound&gt; &lt;pitch&gt; [&lt;ramp seconds&gt;]
 * /soundmanager check  &lt;player&gt;
 * /soundmanager reload
 * /soundmanager playlist ...   (see {@link PlaylistNode})
 *
 * options: [start [end [fadein [fadeout [volume [pitch [category]]]]]]]
 * </pre>
 */
public final class SoundManagerCommand {

    private static final String ARG_POS = "pos";
    private static final String ARG_ENTITY = "entity";
    private static final String ARG_FADE = "fade";
    private static final String ARG_TIME = "time";
    private static final String ARG_VALUE = "value";
    private static final String ARG_RAMP = "ramp";

    private final SoundManagerPlugin plugin;
    private final SoundManagerApi api;
    private final PlaylistNode playlistNode;

    public SoundManagerCommand(SoundManagerPlugin plugin, SoundManagerApi api, PlaylistManager playlists) {
        this.plugin = plugin;
        this.api = api;
        this.playlistNode = new PlaylistNode(playlists);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("soundmanager")
                .requires(src -> hasAny(src, Permissions.PLAY, Permissions.CONTROL,
                        Permissions.CHECK, Permissions.RELOAD, Permissions.PLAYLIST))
                .then(playNode())
                .then(playAtNode())
                .then(playOnNode())
                .then(stopNode())
                .then(soundActionNode("pause", api::pause))
                .then(soundActionNode("resume", api::resume))
                .then(seekNode())
                .then(volumeNode())
                .then(pitchNode())
                .then(checkNode())
                .then(reloadNode())
                .then(playlistNode.build())
                .build();
    }

    // ---------- Tree ----------

    private ArgumentBuilder<CommandSourceStack, ?> playNode() {
        return Commands.literal("play")
                .requires(src -> has(src, Permissions.PLAY))
                .then(targetsArg().then(soundArg()
                        .executes(this::playGlobal)
                        .then(optionChain(this::playGlobal))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> playAtNode() {
        return Commands.literal("playat")
                .requires(src -> has(src, Permissions.PLAY))
                .then(targetsArg().then(soundArg()
                        .then(Commands.argument(ARG_POS, ArgumentTypes.finePosition())
                                .executes(this::playAt)
                                .then(optionChain(this::playAt)))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> playOnNode() {
        return Commands.literal("playon")
                .requires(src -> has(src, Permissions.PLAY))
                .then(targetsArg().then(soundArg()
                        .then(Commands.argument(ARG_ENTITY, ArgumentTypes.entity())
                                .executes(this::playOn)
                                .then(optionChain(this::playOn)))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> stopNode() {
        return Commands.literal("stop")
                .requires(src -> has(src, Permissions.CONTROL))
                .then(targetsArg()
                        .executes(this::stop)
                        .then(soundArg()
                                .executes(this::stop)
                                .then(Commands.argument(ARG_FADE, DoubleArgumentType.doubleArg(0))
                                        .executes(this::stop))));
    }

    /** A control command with an optional sound argument ({@code pause}, {@code resume}). */
    private ArgumentBuilder<CommandSourceStack, ?> soundActionNode(String literal, SoundAction action) {
        Command<CommandSourceStack> exec = ctx -> control(ctx, player -> action.run(player, optionalSound(ctx)));
        return Commands.literal(literal)
                .requires(src -> has(src, Permissions.CONTROL))
                .then(targetsArg()
                        .executes(exec)
                        .then(soundArg().executes(exec)));
    }

    private ArgumentBuilder<CommandSourceStack, ?> seekNode() {
        return Commands.literal("seek")
                .requires(src -> has(src, Permissions.CONTROL))
                .then(targetsArg().then(soundArg()
                        .then(Commands.argument(ARG_TIME, DoubleArgumentType.doubleArg(0))
                                .executes(this::seek))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> volumeNode() {
        return Commands.literal("volume")
                .requires(src -> has(src, Permissions.CONTROL))
                .then(targetsArg().then(soundArg()
                        .then(Commands.argument(ARG_VALUE, FloatArgumentType.floatArg(0f, SoundOptions.MAX_VOLUME))
                                .executes(this::volume)
                                .then(Commands.argument(ARG_RAMP, DoubleArgumentType.doubleArg(0))
                                        .executes(this::volume)))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> pitchNode() {
        return Commands.literal("pitch")
                .requires(src -> has(src, Permissions.CONTROL))
                .then(targetsArg().then(soundArg()
                        .then(Commands.argument(ARG_VALUE,
                                        FloatArgumentType.floatArg(SoundOptions.MIN_PITCH, SoundOptions.MAX_PITCH))
                                .executes(this::pitch)
                                .then(Commands.argument(ARG_RAMP, DoubleArgumentType.doubleArg(0))
                                        .executes(this::pitch)))));
    }

    private ArgumentBuilder<CommandSourceStack, ?> checkNode() {
        return Commands.literal("check")
                .requires(src -> has(src, Permissions.CHECK))
                .then(Commands.argument(ARG_TARGETS, ArgumentTypes.player())
                        .executes(this::check));
    }

    private ArgumentBuilder<CommandSourceStack, ?> reloadNode() {
        return Commands.literal("reload")
                .requires(src -> has(src, Permissions.RELOAD))
                .executes(this::reload);
    }

    // ---------- Arguments ----------

    private static RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetsArg() {
        return Commands.argument(ARG_TARGETS, ArgumentTypes.players());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Key> soundArg() {
        return Commands.argument(ARG_SOUND, ArgumentTypes.key());
    }

    // ---------- Executors ----------

    private int playGlobal(CommandContext<CommandSourceStack> ctx) {
        return play(ctx, (player, sound, options) -> api.playGlobal(player, sound, options));
    }

    private int playAt(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        FinePosition pos;
        try {
            pos = ctx.getArgument(ARG_POS, FinePositionResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            sender.sendMessage(Lang.get(Lang.Key.BAD_POSITION, sender));
            return 0;
        }
        return play(ctx, (player, sound, options) -> api.playAt(player, sound,
                new Location(player.getWorld(), pos.x(), pos.y(), pos.z()), options));
    }

    private int playOn(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Entity> found;
        try {
            found = ctx.getArgument(ARG_ENTITY, EntitySelectorArgumentResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            found = List.of();
        }
        if (found.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.NO_ENTITY, sender));
            return 0;
        }
        Entity entity = found.get(0);
        return play(ctx, (player, sound, options) -> api.playOnEntity(player, sound, entity, options));
    }

    private int play(CommandContext<CommandSourceStack> ctx, PlaySender delivery) {
        CommandSender source = ctx.getSource().getSender();
        List<Player> players = resolvePlayers(ctx);
        if (players.isEmpty()) {
            source.sendMessage(Lang.get(Lang.Key.NO_TARGETS, source));
            return 0;
        }
        SoundOptions options = parseOptions(ctx);
        if (options == null) {
            return 0;
        }
        String sound = ctx.getArgument(ARG_SOUND, Key.class).asString();
        int sent = 0;
        try {
            for (Player player : players) {
                if (delivery.send(player, sound, options)) {
                    sent++;
                }
            }
        } catch (IllegalArgumentException ex) {
            // Thrown by the API for an invalid sound id before anything is sent.
            source.sendMessage(Lang.get(Lang.Key.BAD_SOUND, source));
            return 0;
        }
        report(source, sent, players.size() - sent);
        return sent;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        long fadeMs = toMs(opt(ctx, ARG_FADE, Double.class, 0.0));
        return control(ctx, player -> api.stop(player, optionalSound(ctx), fadeMs));
    }

    private int seek(CommandContext<CommandSourceStack> ctx) {
        String sound = ctx.getArgument(ARG_SOUND, Key.class).asString();
        long positionMs = toMs(ctx.getArgument(ARG_TIME, Double.class));
        return control(ctx, player -> api.seek(player, sound, positionMs));
    }

    private int volume(CommandContext<CommandSourceStack> ctx) {
        String sound = ctx.getArgument(ARG_SOUND, Key.class).asString();
        float value = ctx.getArgument(ARG_VALUE, Float.class);
        long rampMs = toMs(opt(ctx, ARG_RAMP, Double.class, 0.0));
        return control(ctx, player -> api.setVolume(player, sound, value, rampMs));
    }

    private int pitch(CommandContext<CommandSourceStack> ctx) {
        String sound = ctx.getArgument(ARG_SOUND, Key.class).asString();
        float value = ctx.getArgument(ARG_VALUE, Float.class);
        long rampMs = toMs(opt(ctx, ARG_RAMP, Double.class, 0.0));
        return control(ctx, player -> api.setPitch(player, sound, value, rampMs));
    }

    private int control(CommandContext<CommandSourceStack> ctx, Predicate<Player> action) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players = resolvePlayers(ctx);
        if (players.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.NO_TARGETS, sender));
            return 0;
        }
        int sent = 0;
        try {
            for (Player player : players) {
                if (action.test(player)) {
                    sent++;
                }
            }
        } catch (IllegalArgumentException ex) {
            // Thrown by the API for an invalid sound id before anything is sent.
            sender.sendMessage(Lang.get(Lang.Key.BAD_SOUND, sender));
            return 0;
        }
        report(sender, sent, players.size() - sent);
        return sent;
    }

    private int check(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> players = resolvePlayers(ctx);
        if (players.isEmpty()) {
            sender.sendMessage(Lang.get(Lang.Key.NO_TARGETS, sender));
            return 0;
        }
        for (Player player : players) {
            Lang.Key key = api.hasMod(player) ? Lang.Key.CHECK_YES : Lang.Key.CHECK_NO;
            sender.sendMessage(Lang.get(key, sender, Placeholder.unparsed("player", player.getName())));
        }
        return players.size();
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        plugin.reloadAll();
        sender.sendMessage(Lang.get(Lang.Key.RELOADED, sender));
        return Command.SINGLE_SUCCESS;
    }

    // ---------- Helpers ----------

    @Nullable
    private static String optionalSound(CommandContext<CommandSourceStack> ctx) {
        Key key = opt(ctx, ARG_SOUND, Key.class, null);
        return key == null ? null : key.asString();
    }

    private static void report(CommandSender sender, int sent, int skipped) {
        Lang.Key key = skipped == 0 ? Lang.Key.SENT : Lang.Key.SENT_PARTIAL;
        sender.sendMessage(Lang.get(key, sender,
                Placeholder.unparsed("sent", String.valueOf(sent)),
                Placeholder.unparsed("skipped", String.valueOf(skipped))));
    }

    private interface PlaySender {
        boolean send(Player player, String sound, SoundOptions options);
    }

    private interface SoundAction {
        boolean run(Player player, @Nullable String sound);
    }
}
