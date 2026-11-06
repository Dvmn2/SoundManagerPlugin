package net.dvmn2.soundmanagerplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.soundmanagerplugin.Lang;
import net.dvmn2.soundmanagerplugin.api.SoundGroup;
import net.dvmn2.soundmanagerplugin.api.SoundOptions;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Argument names and helpers shared by the command trees of the plugin: the play options, targets and permission
 * checks. Stateless; used on the server main thread.
 */
final class CommandSupport {

    static final String ARG_TARGETS = "targets";
    static final String ARG_SOUND = "sound";
    static final String ARG_START = "start";
    static final String ARG_END = "end";
    static final String ARG_FADE_IN = "fadein";
    static final String ARG_FADE_OUT = "fadeout";
    static final String ARG_VOLUME = "volume";
    static final String ARG_PITCH = "pitch";
    static final String ARG_CATEGORY = "category";

    private static final double MILLIS_PER_SECOND = 1000.0;

    private CommandSupport() {
    }

    /**
     * Optional positional arguments: start, end, fadein, fadeout, volume, pitch, category.
     * The executor is attached to every link of the chain.
     */
    static ArgumentBuilder<CommandSourceStack, ?> optionChain(Command<CommandSourceStack> exec) {
        RequiredArgumentBuilder<CommandSourceStack, String> category = Commands
                .argument(ARG_CATEGORY, StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
                    for (SoundGroup group : SoundGroup.values()) {
                        if (group.getId().startsWith(prefix)) {
                            builder.suggest(group.getId());
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(exec);

        RequiredArgumentBuilder<CommandSourceStack, Float> pitch = Commands
                .argument(ARG_PITCH, FloatArgumentType.floatArg(SoundOptions.MIN_PITCH, SoundOptions.MAX_PITCH))
                .executes(exec);
        pitch.then(category);

        RequiredArgumentBuilder<CommandSourceStack, Float> volume = Commands
                .argument(ARG_VOLUME, FloatArgumentType.floatArg(0f, SoundOptions.MAX_VOLUME))
                .executes(exec);
        volume.then(pitch);

        RequiredArgumentBuilder<CommandSourceStack, Double> fadeOut = Commands
                .argument(ARG_FADE_OUT, DoubleArgumentType.doubleArg(0)).executes(exec);
        fadeOut.then(volume);

        RequiredArgumentBuilder<CommandSourceStack, Double> fadeIn = Commands
                .argument(ARG_FADE_IN, DoubleArgumentType.doubleArg(0)).executes(exec);
        fadeIn.then(fadeOut);

        RequiredArgumentBuilder<CommandSourceStack, Double> end = Commands
                .argument(ARG_END, DoubleArgumentType.doubleArg(-1)).executes(exec);
        end.then(fadeIn);

        RequiredArgumentBuilder<CommandSourceStack, Double> start = Commands
                .argument(ARG_START, DoubleArgumentType.doubleArg(0)).executes(exec);
        start.then(end);

        return start;
    }

    /** Parses the optional play arguments; sends an error and returns null if they are invalid. */
    @Nullable
    static SoundOptions parseOptions(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        SoundGroup group = SoundGroup.MASTER;
        String categoryName = opt(ctx, ARG_CATEGORY, String.class, null);
        if (categoryName != null) {
            SoundGroup parsed = SoundGroup.fromId(categoryName);
            if (parsed == null) {
                sender.sendMessage(Lang.get(Lang.Key.BAD_CATEGORY, sender,
                        Placeholder.unparsed("category", categoryName)));
                return null;
            }
            group = parsed;
        }
        try {
            return new SoundOptions(
                    group,
                    opt(ctx, ARG_VOLUME, Float.class, 1f),
                    opt(ctx, ARG_PITCH, Float.class, 1f),
                    toMs(opt(ctx, ARG_START, Double.class, 0.0)),
                    toMs(opt(ctx, ARG_END, Double.class, -1.0)),
                    toMs(opt(ctx, ARG_FADE_IN, Double.class, 0.0)),
                    toMs(opt(ctx, ARG_FADE_OUT, Double.class, 0.0)));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Lang.get(Lang.Key.BAD_RANGE, sender));
            return null;
        }
    }

    static List<Player> resolvePlayers(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getArgument(ARG_TARGETS, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        } catch (CommandSyntaxException ex) {
            return List.of();
        }
    }

    /** Value of an optional argument, or {@code def} when it was not given. */
    static <T> T opt(CommandContext<CommandSourceStack> ctx, String name, Class<T> type, @Nullable T def) {
        try {
            return ctx.getArgument(name, type);
        } catch (IllegalArgumentException ex) {
            return def; // the optional argument is absent
        }
    }

    static long toMs(double seconds) {
        return seconds < 0 ? -1L : Math.round(seconds * MILLIS_PER_SECOND);
    }

    static boolean has(CommandSourceStack src, String permission) {
        return src.getSender().hasPermission(permission);
    }

    static boolean hasAny(CommandSourceStack src, String... permissions) {
        for (String permission : permissions) {
            if (has(src, permission)) {
                return true;
            }
        }
        return false;
    }
}
