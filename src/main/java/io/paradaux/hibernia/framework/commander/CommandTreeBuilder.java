package io.paradaux.hibernia.framework.commander;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.paradaux.hibernia.framework.commander.arguments.BigDecimalArgumentType;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the Brigadier command tree for a route and enforces the registration-time
 * conflict guarantees: two routes may not execute at the same path, and a shared
 * argument node may not be declared as two different Brigadier types. Pure
 * tree-construction concerns, extracted from {@link CommandManager}.
 */
final class CommandTreeBuilder {

    /** Executes a bound route at dispatch time; wired back to {@code CommandManager#executeBinding}. */
    @FunctionalInterface
    interface BindingExecutor {
        int execute(CommandContext<CommandSourceStack> context, RouteBinding binding);
    }

    private final JavaPlugin plugin;
    private final Function<Class<?>, ParameterResolver<?>> resolverLookup;
    private final BindingExecutor executor;

    CommandTreeBuilder(JavaPlugin plugin, Function<Class<?>, ParameterResolver<?>> resolverLookup,
                       BindingExecutor executor) {
        this.plugin = plugin;
        this.resolverLookup = resolverLookup;
        this.executor = executor;
    }

    /**
     * Every path at which a binding is executable: its full path, plus each
     * truncation produced by omitting trailing optional segments.
     */
    private List<String> executablePathKeys(RouteBinding binding) {
        List<String> keys = new ArrayList<>();
        keys.add(pathKey(binding.path, binding.path.size()));
        for (int end = binding.path.size() - 1; end >= 0 && binding.path.get(end).optionalArg(); end--) {
            keys.add(pathKey(binding.path, end));
        }
        return keys;
    }

    private static String pathKey(List<Segment> path, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            Segment s = path.get(i);
            if (i > 0) sb.append(' ');
            sb.append(s.literal() ? "lit:" + s.token() : "arg:" + s.token());
        }
        return sb.toString();
    }

    void checkConflicts(RootSpec spec, Map<String, String> staged, Map<String, ArgKind> stagedKinds,
                        RouteBinding binding, String rootLabel) {
        String where = binding.describe();
        for (String key : executablePathKeys(binding)) {
            String existing = spec.routesSeen.getOrDefault(key, staged.get(key));
            if (existing != null) {
                throw new IllegalStateException("Route conflict under /" + rootLabel + ": " + where
                        + " and " + existing + " both execute at '" + (key.isEmpty() ? "(root)" : key) + "'");
            }
            staged.put(key, where);
        }
        // Same-named argument nodes merge in Brigadier; if their argument types
        // differ, the first type silently wins at parse time and the other
        // binding receives garbage. Refuse to register that.
        for (int i = 0; i < binding.path.size(); i++) {
            Segment seg = binding.path.get(i);
            if (seg.literal()) continue;
            Param param = RouteBinder.findParamByName(binding.params, seg.token());
            ArgKind kind = argKindOf(param);
            String nodeKey = pathKey(binding.path, i) + " arg:" + seg.token();
            ArgKind existing = spec.argKinds.getOrDefault(nodeKey, stagedKinds.get(nodeKey));
            if (existing != null && existing != kind) {
                throw new IllegalStateException("Argument type conflict under /" + rootLabel + ": <" + seg.token()
                        + "> at '" + pathKey(binding.path, i) + "' is declared both as " + existing + " and as "
                        + kind + " (" + where + ")");
            }
            stagedKinds.put(nodeKey, kind);
        }
    }

    void warnOnAmbiguousSiblings(RootSpec spec, RouteBinding binding, String rootLabel) {
        for (int i = 0; i < binding.path.size(); i++) {
            Segment seg = binding.path.get(i);
            if (seg.literal()) continue;
            String parentKey = pathKey(binding.path, i);
            String existing = spec.argChildAt.putIfAbsent(parentKey, seg.token());
            if (existing != null && !existing.equals(seg.token())) {
                plugin.getLogger().warning("Ambiguous routes under /" + rootLabel + ": arguments <" + existing
                        + "> and <" + seg.token() + "> are siblings at '"
                        + (parentKey.isEmpty() ? "(root)" : parentKey)
                        + "'. Brigadier will parse input with whichever registered first.");
            }
        }
    }

    void addSegments(ArgumentBuilder<CommandSourceStack, ?> parent,
                     RouteBinding binding, int depth, String classPerm) {
        if (depth >= binding.path.size()) {
            parent.executes(ctx -> executor.execute(ctx, binding));
            return;
        }

        Segment segment = binding.path.get(depth);

        // An optional tail means the command is also executable without it;
        // conflict checks have already guaranteed this executes slot is ours.
        if (segment.optionalArg() && parent.getCommand() == null) {
            parent.executes(ctx -> executor.execute(ctx, binding));
        }

        ArgumentBuilder<CommandSourceStack, ?> child;
        if (segment.literal()) {
            child = Commands.literal(segment.token());
        } else {
            // Validated non-null at bind time.
            Param param = RouteBinder.findParamByName(binding.params, segment.token());
            RequiredArgumentBuilder<CommandSourceStack, ?> argBuilder =
                    createArgumentBuilder(segment.token(), param);
            argBuilder.suggests(createArgumentSuggestionProvider(param));
            child = argBuilder;
        }

        if (depth == 0 && classPerm != null) {
            child.requires(src -> src.getSender().hasPermission(classPerm));
        }
        if (depth == binding.path.size() - 1) {
            child.executes(ctx -> executor.execute(ctx, binding));
        } else {
            addSegments(child, binding, depth + 1, classPerm);
        }
        parent.then(child);
    }

    RequiredArgumentBuilder<CommandSourceStack, ?> createArgumentBuilder(String name, Param param) {
        if (param.type == Integer.class || param.type == int.class) {
            return Commands.argument(name, IntegerArgumentType.integer());
        } else if (param.type == Long.class || param.type == long.class) {
            return Commands.argument(name, LongArgumentType.longArg());
        } else if (param.greedy) {
            return Commands.argument(name, StringArgumentType.greedyString());
        } else if (param.type == BigDecimal.class) {
            return Commands.argument(name, BigDecimalArgumentType.bigDecimal());
        } else {
            return Commands.argument(name, StringArgumentType.word());
        }
    }

    private static ArgKind argKindOf(Param param) {
        if (param.type == Integer.class || param.type == int.class) return ArgKind.INTEGER;
        if (param.type == Long.class || param.type == long.class) return ArgKind.LONG;
        if (param.greedy) return ArgKind.GREEDY;
        if (param.type == BigDecimal.class) return ArgKind.BIG_DECIMAL;
        return ArgKind.WORD;
    }

    SuggestionProvider<CommandSourceStack> createArgumentSuggestionProvider(Param param) {
        return (context, builder) -> {
            CommandSender sender = context.getSource().getSender();
            String input = builder.getRemaining();

            @SuppressWarnings("unchecked")
            ParameterResolver<Object> resolver = (ParameterResolver<Object>) resolverLookup.apply(param.type);

            List<String> suggestions = (resolver != null)
                    ? resolver.suggestions(input, sender)
                    : List.of();

            if (suggestions.isEmpty()) {
                builder.suggest(param.optional ? "[" + param.name + "]" : "<" + param.name + ">");
            } else {
                for (String s : suggestions) builder.suggest(s);
            }
            return builder.buildFuture();
        };
    }
}
