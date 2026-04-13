package io.paradaux.hibernia.framework.commander;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.resolvers.BigDecimalResolver;
import io.paradaux.hibernia.framework.commander.resolvers.IntegerResolver;
import io.paradaux.hibernia.framework.commander.resolvers.OfflinePlayerResolver;
import io.paradaux.hibernia.framework.commander.resolvers.StringResolver;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for registering and dispatching plugin commands.
 *
 * <p>Responsibilities:
 * - Scans provided CommandHandler instances for @Command and @Route annotations,
 *   and builds a Brigadier command tree for Paper.
 * - Binds method parameters annotated with @Arg, @OptionalArg, @GreedyArg and @Sender to
 *   command arguments and injects them at invocation time.
 * - Respects @Permission on classes or methods to gate execution.
 * - Supports asynchronous execution for methods annotated with @Async.</p>
 *
 * <p>Resolvers:
 * Parameter resolution and suggestions are delegated to registered {@link ParameterResolver}
 * implementations. Built-in resolvers for String, Integer, BigDecimal and OfflinePlayer are
 * registered by default; additional resolvers may be provided via dependency injection
 * into the constructor.</p>
 *
 * <p>Lifecycle:
 * The manager registers commands during the Paper COMMANDS lifecycle event using the plugin
 * lifecycle manager. Registered commands use Brigadier argument builders and suggestion
 * providers driven by resolvers.</p>
 *
 * <p>Threading:
 * Commands annotated with {@link Async} are dispatched asynchronously; sender messages and
 * other Bukkit main-thread operations are scheduled back onto the main thread where necessary.</p>
 *
 * <p>Example usage:
 * <pre>
 * // A handler class
 * @Command("example")
 * public class Example implements CommandHandler {
 *     @Route("give &lt;player&gt; &lt;amount&gt;")
 *     public void give(@Sender Player sender, @Arg("player") OfflinePlayer target, @Arg("amount") int amount) { ... }
 * }
 * </pre>
 * </p>
 */
@Singleton
@Slf4j
public class CommandManager {

    private static final String PLACEHOLDER_PREFIX = "<";
    private static final String PLACEHOLDER_SUFFIX = ">";

    private final JavaPlugin plugin;
    private final Set<CommandHandler> handlers;
    private final Map<Class<?>, ParameterResolver<?>> resolvers = new ConcurrentHashMap<>();

    /**
     * Create a CommandManager.
     *
     * @param plugin the JavaPlugin instance used for scheduling and lifecycle
     * @param handlers the set of discovered CommandHandler instances to register
     * @param resolverSet additional ParameterResolver implementations to register
     */
    @Inject
    public CommandManager(JavaPlugin plugin, Set<CommandHandler> handlers, Set<ParameterResolver<?>> resolverSet) {
        this.plugin = plugin;
        this.handlers = handlers;
        resolverSet.forEach(r -> resolvers.put(r.type(), r));
        // Built-ins
        registerResolver(new StringResolver());
        registerResolver(new IntegerResolver());
        registerResolver(new BigDecimalResolver());
        registerResolver(new OfflinePlayerResolver());
    }

    /**
     * Register all commands discovered from injected CommandHandler instances.
     *
     * <p>This method hooks into the Paper lifecycle {@code COMMANDS} event and registers
     * all built Brigadier root literals returned from classes annotated with {@link Command}.</p>
     */
    public void registerAll() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            Map<String, LiteralArgumentBuilder<CommandSourceStack>> roots = new LinkedHashMap<>();

            for (CommandHandler handler : handlers) {
                Class<?> clazz = handler.getClass();
                Command cmdAnn = clazz.getAnnotation(Command.class);
                if (cmdAnn == null) continue;

                String classPerm = Optional.ofNullable(clazz.getAnnotation(Permission.class))
                        .map(Permission::value).orElse(null);

                List<Method> routes = Arrays.stream(clazz.getDeclaredMethods())
                        .filter(m -> m.getAnnotationsByType(Route.class).length > 0)
                        .toList();
                if (routes.isEmpty()) continue;

                for (String root : cmdAnn.value()) {
                    LiteralArgumentBuilder<CommandSourceStack> rootBuilder =
                            roots.computeIfAbsent(root, k -> {
                                LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(k);
                                if (classPerm != null) {
                                    b.requires(src -> src.getSender().hasPermission(classPerm));
                                }
                                return b;
                            });

                    buildCommandTree(rootBuilder, handler, routes, classPerm);
                }
            }

            roots.values().forEach(b -> commands.register(b.build()));
        });
    }

    private void buildCommandTree(LiteralArgumentBuilder<CommandSourceStack> rootBuilder,
                                  CommandHandler handler, List<Method> routes, String classPerm) {

        List<RouteBinding> bindings = new ArrayList<>();
        for (Method method : routes) {
            for (Route route : method.getAnnotationsByType(Route.class)) {
                bindings.add(bindRoute(handler, method, route, classPerm));
            }
        }

        Map<String, List<RouteBinding>> routesByFirstSegment = new HashMap<>();
        List<RouteBinding> defaultRoutes = new ArrayList<>();
        for (RouteBinding b : bindings) {
            if (b.path.isEmpty()) {
                defaultRoutes.add(b);
            } else {
                String first = b.path.get(0).token;
                routesByFirstSegment.computeIfAbsent(first, k -> new ArrayList<>()).add(b);
            }
        }

        if (!defaultRoutes.isEmpty()) {
            rootBuilder.executes(ctx -> executeBinding(ctx, defaultRoutes.get(0)));
        }

        for (List<RouteBinding> group : routesByFirstSegment.values()) {
            for (RouteBinding b : group) {
                addRoute(rootBuilder, b, 0, classPerm);
            }
        }
    }

    private void addRoute(LiteralArgumentBuilder<CommandSourceStack> parent,
                          RouteBinding binding, int depth, String classPerm) {
        if (depth >= binding.path.size()) {
            parent.executes(ctx -> executeBinding(ctx, binding));
            return;
        }

        Segment segment = binding.path.get(depth);

        if (segment.literal) {
            LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(segment.token);

            if (depth == 0 && classPerm != null) {
                literal.requires(src -> src.getSender().hasPermission(classPerm));
            }
            if (depth == binding.path.size() - 1) {
                literal.executes(ctx -> executeBinding(ctx, binding));
            } else {
                addRoute(literal, binding, depth + 1, classPerm);
            }
            parent.then(literal);
        } else {
            Param matchingParam = findParamByName(binding.params, segment.token);
            if (matchingParam == null || matchingParam.sender) return;

            RequiredArgumentBuilder<CommandSourceStack, ?> argBuilder =
                    createArgumentBuilder(segment.token, matchingParam);

            // Resolver-driven suggestions (with placeholder fallback)
            argBuilder.suggests(createArgumentSuggestionProvider(matchingParam));

            if (depth == 0 && classPerm != null) {
                argBuilder.requires(src -> src.getSender().hasPermission(classPerm));
            }
            if (depth == binding.path.size() - 1) {
                argBuilder.executes(ctx -> executeBinding(ctx, binding));
            } else {
                addRouteToArgument(argBuilder, binding, depth + 1);
            }
            parent.then(argBuilder);
        }
    }

    private void addRouteToArgument(RequiredArgumentBuilder<CommandSourceStack, ?> parent,
                                    RouteBinding binding, int depth) {
        if (depth >= binding.path.size()) {
            parent.executes(ctx -> executeBinding(ctx, binding));
            return;
        }

        Segment segment = binding.path.get(depth);

        if (segment.literal) {
            LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(segment.token);

            if (depth == binding.path.size() - 1) {
                literal.executes(ctx -> executeBinding(ctx, binding));
            } else {
                addRoute(literal, binding, depth + 1, null);
            }
            parent.then(literal);
        } else {
            Param matchingParam = findParamByName(binding.params, segment.token);
            if (matchingParam == null || matchingParam.sender) return;

            RequiredArgumentBuilder<CommandSourceStack, ?> argBuilder =
                    createArgumentBuilder(segment.token, matchingParam);

            argBuilder.suggests(createArgumentSuggestionProvider(matchingParam));

            if (depth == binding.path.size() - 1) {
                argBuilder.executes(ctx -> executeBinding(ctx, binding));
            } else {
                addRouteToArgument(argBuilder, binding, depth + 1);
            }
            parent.then(argBuilder);
        }
    }

    private RequiredArgumentBuilder<CommandSourceStack, ?> createArgumentBuilder(String name, Param param) {
        if (param.type == Integer.class || param.type == int.class) {
            return Commands.argument(name, IntegerArgumentType.integer());
        } else if (param.greedy) {
            return Commands.argument(name, StringArgumentType.greedyString());
        } else if (param.type == BigDecimal.class) {
            return Commands.argument(name, StringArgumentType.word());
        } else {
            return Commands.argument(name, StringArgumentType.word());
        }
    }

    private SuggestionProvider<CommandSourceStack> createArgumentSuggestionProvider(Param param) {
        return (context, builder) -> {
            CommandSender sender = context.getSource().getSender();
            String input = builder.getRemaining();

            @SuppressWarnings("unchecked")
            ParameterResolver<Object> resolver = (ParameterResolver<Object>) resolvers.get(param.type);

            List<String> suggestions = (resolver != null)
                    ? resolver.suggestions(input, sender)
                    : List.of();

            if (suggestions.isEmpty()) {
                builder.suggest(PLACEHOLDER_PREFIX + param.name + PLACEHOLDER_SUFFIX);
            } else {
                for (String s : suggestions) builder.suggest(s);
            }
            return builder.buildFuture();
        };
    }

    private int executeBinding(CommandContext<CommandSourceStack> context, RouteBinding binding) {
        CommandSender sender = context.getSource().getSender();

        if (binding.permission != null && !sender.hasPermission(binding.permission)) {
            sender.sendMessage("§cYou don't have permission.");
            return 0;
        }

        Runnable task = () -> {
            try {
                Object[] invokeArgs = extractArguments(context, binding, sender);
                binding.method.invoke(binding.instance, invokeArgs);
            } catch (IllegalArgumentException iae) {
                safeMsg(sender, "§cError: " + iae.getMessage());
            } catch (InvocationTargetException ite) {
                Throwable t = ite.getTargetException();
                safeMsg(sender, "§cError: " + t.getMessage());
                plugin.getLogger().warning("Command error: " + t);
            } catch (Exception e) {
                safeMsg(sender, "§cInternal error.");
                plugin.getLogger().warning("Command exception: " + e);
            }
        };

        if (binding.async) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }

        return 1;
    }

    private Object[] extractArguments(CommandContext<CommandSourceStack> context, RouteBinding binding, CommandSender sender) throws Exception {
        List<Object> values = new ArrayList<>();

        for (Param param : binding.params) {
            if (param.sender) {
                values.add(injectSender(param.type, sender));
            } else {
                String argName = null;
                for (Segment seg : binding.path) {
                    if (!seg.literal && seg.token.equals(param.name)) {
                        argName = seg.token;
                        break;
                    }
                }

                if (argName != null) {
                    try {
                        Object rawValue = context.getArgument(argName, Object.class);

                        // When sanitize is disabled for a String parameter, bypass the resolver
                        if (!param.sanitize && param.type == String.class) {
                            String stringValue = rawValue.toString();
                            if (stringValue.isBlank()) {
                                throw new IllegalArgumentException("Invalid " + param.name + ": blank value");
                            }
                            values.add(stringValue);
                        } else {
                            @SuppressWarnings("unchecked")
                            ParameterResolver<Object> resolver = (ParameterResolver<Object>) resolvers.get(param.type);

                            if (resolver != null) {
                                String stringValue = rawValue.toString();
                                values.add(resolver.resolve(stringValue, sender)
                                        .orElseThrow(() -> new IllegalArgumentException("Invalid " + param.name + ": " + stringValue)));
                            } else if (param.type == Integer.class || param.type == int.class) {
                                values.add(rawValue);
                            } else {
                                values.add(rawValue.toString());
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        if (param.optional) {
                            values.add(param.defaultValue);
                        } else {
                            throw e;
                        }
                    }
                } else if (param.optional) {
                    values.add(param.defaultValue);
                } else {
                    throw new IllegalArgumentException("Missing required argument: " + param.name);
                }
            }
        }

        return values.toArray();
    }

    private Param findParamByName(List<Param> params, String name) {
        for (Param p : params) {
            if (!p.sender && p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    private void registerResolver(ParameterResolver<?> r) {
        resolvers.putIfAbsent(r.type(), r);
    }

    private void safeMsg(CommandSender sender, String msg) {
        if (plugin.getServer().isPrimaryThread()) {
            sender.sendMessage(msg);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(msg));
        }
    }

    private Object injectSender(Class<?> type, CommandSender sender) {
        if (type.isInstance(sender)) return type.cast(sender);
        throw new IllegalArgumentException("Sender must be " + type.getSimpleName());
    }

    private RouteBinding bindRoute(Object instance, Method m, Route r, String classPerm) {
        String raw = r.value().trim();
        List<String> parts = raw.isEmpty() ? List.of() : List.of(raw.split("\\s+"));

        List<Segment> segments = new ArrayList<>();
        for (String p : parts) {
            segments.add(p.startsWith("<") && p.endsWith(">") ?
                    Segment.arg(p.substring(1, p.length() - 1)) : Segment.literal(p));
        }

        List<Param> params = new ArrayList<>();
        boolean foundGreedy = false;
        for (Parameter rp : m.getParameters()) {
            boolean isSender = rp.isAnnotationPresent(Sender.class);
            Arg arg = rp.getAnnotation(Arg.class);
            OptionalArg opt = rp.getAnnotation(OptionalArg.class);
            GreedyArg greedy = rp.getAnnotation(GreedyArg.class);

            if (foundGreedy && !isSender) {
                throw new IllegalStateException("@GreedyArg must be the last argument in the route on " + m);
            }

            if (isSender) params.add(Param.sender(rp.getType()));
            else if (greedy != null) {
                foundGreedy = true;
                params.add(Param.greedy(rp.getType(), greedy.value(), greedy.sanitize()));
            }
            else if (arg != null) params.add(Param.required(rp.getType(), arg.value(), arg.sanitize()));
            else if (opt != null) params.add(Param.optional(rp.getType(), opt.value(), opt.defaultValue(), opt.sanitize()));
            else throw new IllegalStateException("Parameter missing @Sender/@Arg/@OptionalArg/@GreedyArg on " + m);
        }

        String methodPerm = Optional.ofNullable(m.getAnnotation(Permission.class)).map(Permission::value).orElse(null);
        String effectivePerm = methodPerm != null ? methodPerm : classPerm;

        String description = Optional.ofNullable(m.getAnnotation(Description.class)).map(Description::value).orElse("");

        return new RouteBinding(instance, m, segments, params, effectivePerm, description);
    }

    private record Segment(boolean literal, String token) {
        static Segment literal(String s) {
            return new Segment(true, s.toLowerCase(Locale.ROOT));
        }
        static Segment arg(String name) {
            return new Segment(false, name);
        }
    }

    private record Param(boolean sender, boolean optional, boolean sanitize, boolean greedy, Class<?> type, String name, Object defaultValue) {
        static Param sender(Class<?> t) { return new Param(true, false, true, false, t, "", null); }
        static Param required(Class<?> t, String n, boolean sanitize) { return new Param(false, false, sanitize, false, t, n, null); }
        static Param greedy(Class<?> t, String n, boolean sanitize) { return new Param(false, false, sanitize, true, t, n, null); }
        static Param optional(Class<?> t, String n, Object def, boolean sanitize) { return new Param(false, true, sanitize, false, t, n, def); }
    }

    private static class RouteBinding {
        final Object instance;
        final Method method;
        final List<Segment> path;
        final List<Param> params;
        final String permission;
        final String description;
        final boolean async;

        RouteBinding(Object instance, Method method, List<Segment> path, List<Param> params, String permission, String description) {
            this.instance = instance;
            this.method = method;
            this.path = path;
            this.params = params;
            this.permission = permission;
            this.description = description;
            this.method.setAccessible(true);
            this.async = method.isAnnotationPresent(Async.class);
        }
    }
}
