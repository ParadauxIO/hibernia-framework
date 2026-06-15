package io.paradaux.hibernia.framework.commander;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.paradaux.hibernia.framework.commander.annotations.*;
import io.paradaux.hibernia.framework.commander.arguments.BigDecimalArgumentType;
import io.paradaux.hibernia.framework.commander.resolvers.BigDecimalResolver;
import io.paradaux.hibernia.framework.commander.resolvers.BooleanResolver;
import io.paradaux.hibernia.framework.commander.resolvers.IntegerResolver;
import io.paradaux.hibernia.framework.commander.resolvers.LongResolver;
import io.paradaux.hibernia.framework.commander.resolvers.OfflinePlayerResolver;
import io.paradaux.hibernia.framework.commander.resolvers.StringResolver;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.hibernia.framework.exceptions.BadCommandException;
import io.paradaux.hibernia.framework.exceptions.ConflictException;
import io.paradaux.hibernia.framework.exceptions.ExceedsLimitException;
import io.paradaux.hibernia.framework.exceptions.NoPermissionException;
import io.paradaux.hibernia.framework.exceptions.NotFoundException;
import io.paradaux.hibernia.framework.i18n.Message;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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
 * <p>Route syntax:
 * literals are plain tokens, required arguments are {@code <name>}, optional
 * arguments are {@code [name]}. Optional segments must form the tail of the
 * route — the command is also executable with the optional tail omitted, in
 * which case the {@link OptionalArg} defaults apply.</p>
 *
 * <p>Validation:
 * routes are validated when commands are registered, not when they are first
 * run. A route placeholder with no matching parameter, a required @Arg missing
 * from the route, a literal following an optional segment, or two routes
 * binding the same path all fail registration with a descriptive
 * {@link IllegalStateException}. A failing handler class is skipped and logged;
 * other handlers still register.</p>
 *
 * <p>Resolvers:
 * Parameter resolution and suggestions are delegated to registered {@link ParameterResolver}
 * implementations. Built-in resolvers for String, Integer, Long, BigDecimal, Boolean and
 * OfflinePlayer are registered by default; additional resolvers may be provided via
 * dependency injection into the constructor.</p>
 *
 * <p>Error handling:
 * the framework's HTTP-semantic exceptions ({@link NotFoundException},
 * {@link ConflictException}, {@link BadCommandException}, {@link ExceedsLimitException},
 * {@link NoPermissionException}) thrown from a handler (typically propagated from the
 * service layer) are caught and rendered to the sender. Each maps to a
 * {@code hibernia.error.*} key in the consumer's {@code messages.properties} when a
 * {@link Message} bean is bound, falling back to built-in MiniMessage defaults
 * otherwise. Unknown exceptions render a generic internal-error message and are
 * logged with their stack trace.</p>
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

    /** messages.properties keys consulted (when a Message bean is bound) before the built-in defaults. */
    public static final String KEY_NO_PERMISSION    = "hibernia.error.no-permission";
    public static final String KEY_INVALID_ARGUMENT = "hibernia.error.invalid-argument";
    public static final String KEY_BAD_COMMAND      = "hibernia.error.bad-command";
    public static final String KEY_NOT_FOUND        = "hibernia.error.not-found";
    public static final String KEY_CONFLICT         = "hibernia.error.conflict";
    public static final String KEY_EXCEEDS_LIMIT    = "hibernia.error.exceeds-limit";
    public static final String KEY_INTERNAL         = "hibernia.error.internal";

    private static final String DEFAULT_NO_PERMISSION = "<red>You don't have permission to do that.</red>";
    private static final String DEFAULT_WITH_MESSAGE  = "<red>{message}</red>";
    private static final String DEFAULT_INTERNAL      = "<red>An internal error occurred. Please contact an administrator.</red>";

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Set<CommandHandler> handlers;
    private final Map<Class<?>, ParameterResolver<?>> resolvers = new ConcurrentHashMap<>();
    private final Injector injector;

    private volatile Message message;
    private volatile boolean messageResolved;
    private volatile List<RouteInfo> routeIndex = List.of();

    /**
     * Create a CommandManager without an injector. Error messages always use the
     * built-in MiniMessage defaults rather than a consumer-bound {@link Message}.
     *
     * @param plugin the JavaPlugin instance used for scheduling and lifecycle
     * @param handlers the set of discovered CommandHandler instances to register
     * @param resolverSet additional ParameterResolver implementations to register
     */
    public CommandManager(JavaPlugin plugin, Set<CommandHandler> handlers, Set<ParameterResolver<?>> resolverSet) {
        this(plugin, handlers, resolverSet, null);
    }

    /**
     * Create a CommandManager.
     *
     * @param plugin the JavaPlugin instance used for scheduling and lifecycle
     * @param handlers the set of discovered CommandHandler instances to register
     * @param resolverSet additional ParameterResolver implementations to register
     * @param injector the Guice injector, used to discover an explicitly bound
     *                 {@link Message} bean for rendering error feedback (optional)
     */
    @Inject
    public CommandManager(JavaPlugin plugin, Set<CommandHandler> handlers,
                          Set<ParameterResolver<?>> resolverSet, Injector injector) {
        this.plugin = plugin;
        this.handlers = handlers;
        this.injector = injector;
        resolverSet.forEach(r -> resolvers.put(r.type(), r));
        // Built-ins
        registerResolver(new StringResolver());
        registerResolver(new IntegerResolver());
        registerResolver(new LongResolver());
        registerResolver(new BigDecimalResolver());
        registerResolver(new BooleanResolver());
        registerResolver(new OfflinePlayerResolver());
    }

    /**
     * Register all commands discovered from injected CommandHandler instances.
     *
     * <p>This method hooks into the Paper lifecycle {@code COMMANDS} event and registers
     * all built Brigadier root literals returned from classes annotated with {@link Command}.
     * Handlers are processed in deterministic (class-name) order; a handler that fails
     * validation is skipped and logged without affecting the others.</p>
     */
    public void registerAll() {
        LifecycleEventManager<Plugin> manager = plugin.getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            Map<String, RootSpec> roots = new LinkedHashMap<>();
            List<RouteInfo> index = new ArrayList<>();

            // Guice multibinder sets have no stable iteration order; conflict
            // outcomes must not depend on it.
            List<CommandHandler> ordered = handlers.stream()
                    .sorted(Comparator.comparing(h -> h.getClass().getName()))
                    .toList();

            for (CommandHandler handler : ordered) {
                try {
                    registerHandler(roots, index, handler);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Skipping command handler " + handler.getClass().getName() + ": " + e.getMessage(), e);
                }
            }

            for (RootSpec spec : roots.values()) {
                // The root is visible to a sender holding *any* of the class-level
                // permissions registered under it. A handler class without a
                // class-level @Permission leaves the root visible to everyone;
                // per-route permissions are still enforced at execution time.
                if (!spec.openAccess && !spec.classPerms.isEmpty()) {
                    Set<String> perms = Set.copyOf(spec.classPerms);
                    spec.builder.requires(src -> perms.stream().anyMatch(p -> src.getSender().hasPermission(p)));
                }
                if (spec.description.isEmpty()) {
                    commands.register(spec.builder.build());
                } else {
                    commands.register(spec.builder.build(), spec.description);
                }
            }

            this.routeIndex = List.copyOf(index);
        });
    }

    /**
     * Metadata for every route registered in the last {@code COMMANDS} lifecycle pass.
     * Intended for consumers building help output. Empty until commands have registered.
     */
    public List<RouteInfo> routeIndex() {
        return routeIndex;
    }

    private void registerHandler(Map<String, RootSpec> roots, List<RouteInfo> index, CommandHandler handler) {
        Class<?> clazz = handler.getClass();
        Command cmdAnn = clazz.getAnnotation(Command.class);
        if (cmdAnn == null) {
            plugin.getLogger().warning("CommandHandler " + clazz.getName()
                    + " is bound but has no @Command annotation; nothing to register.");
            return;
        }

        String classPerm = Optional.ofNullable(clazz.getAnnotation(Permission.class))
                .map(Permission::value).orElse(null);

        List<Method> routeMethods = Arrays.stream(clazz.getDeclaredMethods())
                .filter(m -> m.getAnnotationsByType(Route.class).length > 0)
                .sorted(Comparator.comparing(Method::toGenericString))
                .toList();
        if (routeMethods.isEmpty()) {
            plugin.getLogger().warning("@Command class " + clazz.getName() + " declares no @Route methods.");
            return;
        }

        // Phase 1 — bind and validate every route. Throws before anything is
        // mutated, so a bad handler never half-registers.
        List<RouteBinding> bindings = new ArrayList<>();
        for (Method method : routeMethods) {
            for (Route route : method.getAnnotationsByType(Route.class)) {
                bindings.add(bindRoute(handler, method, route, classPerm));
            }
        }

        for (String rootLabel : cmdAnn.value()) {
            RootSpec spec = roots.computeIfAbsent(rootLabel.toLowerCase(Locale.ROOT),
                    k -> new RootSpec(Commands.literal(k)));
            if (classPerm == null) {
                spec.openAccess = true;
            } else {
                spec.classPerms.add(classPerm);
            }
            if (spec.description.isEmpty() && !cmdAnn.description().isEmpty()) {
                spec.description = cmdAnn.description();
            }

            // Phase 2 — conflict-check this handler's routes against everything
            // already registered under the root (and against each other) before
            // mutating the shared tree.
            Map<String, String> staged = new LinkedHashMap<>();
            Map<String, ArgKind> stagedKinds = new LinkedHashMap<>();
            for (RouteBinding b : bindings) {
                checkConflicts(spec, staged, stagedKinds, b, rootLabel);
            }
            spec.routesSeen.putAll(staged);
            spec.argKinds.putAll(stagedKinds);

            // Phase 3 — mutate the tree. Nothing below should throw.
            for (RouteBinding b : bindings) {
                warnOnAmbiguousSiblings(spec, b, rootLabel);
                if (b.path.isEmpty()) {
                    spec.builder.executes(ctx -> executeBinding(ctx, b));
                } else {
                    addSegments(spec.builder, b, 0, classPerm);
                }
                index.add(new RouteInfo(rootLabel, b.rawPattern, b.description, b.permission, b.async));
            }
        }
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

    private void checkConflicts(RootSpec spec, Map<String, String> staged, Map<String, ArgKind> stagedKinds,
                                RouteBinding binding, String rootLabel) {
        String where = describe(binding);
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
            Param param = findParamByName(binding.params, seg.token());
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

    private void warnOnAmbiguousSiblings(RootSpec spec, RouteBinding binding, String rootLabel) {
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

    private static String describe(RouteBinding binding) {
        return binding.method.getDeclaringClass().getSimpleName() + "#" + binding.method.getName()
                + " (route '" + binding.rawPattern + "')";
    }

    private void addSegments(ArgumentBuilder<CommandSourceStack, ?> parent,
                             RouteBinding binding, int depth, String classPerm) {
        if (depth >= binding.path.size()) {
            parent.executes(ctx -> executeBinding(ctx, binding));
            return;
        }

        Segment segment = binding.path.get(depth);

        // An optional tail means the command is also executable without it;
        // conflict checks have already guaranteed this executes slot is ours.
        if (segment.optionalArg() && parent.getCommand() == null) {
            parent.executes(ctx -> executeBinding(ctx, binding));
        }

        ArgumentBuilder<CommandSourceStack, ?> child;
        if (segment.literal()) {
            child = Commands.literal(segment.token());
        } else {
            // Validated non-null at bind time.
            Param param = findParamByName(binding.params, segment.token());
            RequiredArgumentBuilder<CommandSourceStack, ?> argBuilder =
                    createArgumentBuilder(segment.token(), param);
            argBuilder.suggests(createArgumentSuggestionProvider(param));
            child = argBuilder;
        }

        if (depth == 0 && classPerm != null) {
            child.requires(src -> src.getSender().hasPermission(classPerm));
        }
        if (depth == binding.path.size() - 1) {
            child.executes(ctx -> executeBinding(ctx, binding));
        } else {
            addSegments(child, binding, depth + 1, classPerm);
        }
        parent.then(child);
    }

    private RequiredArgumentBuilder<CommandSourceStack, ?> createArgumentBuilder(String name, Param param) {
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
                builder.suggest(param.optional ? "[" + param.name + "]" : "<" + param.name + ">");
            } else {
                for (String s : suggestions) builder.suggest(s);
            }
            return builder.buildFuture();
        };
    }

    private int executeBinding(CommandContext<CommandSourceStack> context, RouteBinding binding) {
        CommandSender sender = context.getSource().getSender();

        if (binding.permission != null && !sender.hasPermission(binding.permission)) {
            sendError(sender, KEY_NO_PERMISSION, DEFAULT_NO_PERMISSION, Map.of());
            return 0;
        }

        Runnable task = () -> {
            try {
                Object[] invokeArgs = extractArguments(context, binding, sender);
                binding.method.invoke(binding.instance, invokeArgs);
            } catch (IllegalArgumentException iae) {
                sendError(sender, KEY_INVALID_ARGUMENT, DEFAULT_WITH_MESSAGE,
                        Map.of("message", messageOf(iae, "Invalid arguments.")));
            } catch (InvocationTargetException ite) {
                handleInvocationFailure(sender, binding, ite.getTargetException());
            } catch (Exception e) {
                sendError(sender, KEY_INTERNAL, DEFAULT_INTERNAL, Map.of());
                plugin.getLogger().log(Level.SEVERE,
                        "Command dispatch failed for " + describe(binding), e);
            }
        };

        if (binding.async) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        } else {
            task.run();
        }

        return 1;
    }

    /**
     * Map the framework's HTTP-semantic exceptions, thrown by the handler or
     * propagated up from the service layer, to user feedback. Anything not in
     * the taxonomy is a bug: the sender gets a generic message and the full
     * stack trace goes to the server log.
     */
    private void handleInvocationFailure(CommandSender sender, RouteBinding binding, Throwable t) {
        if (t instanceof NoPermissionException) {
            sendError(sender, KEY_NO_PERMISSION, DEFAULT_NO_PERMISSION, Map.of());
        } else if (t instanceof BadCommandException) {
            sendError(sender, KEY_BAD_COMMAND, DEFAULT_WITH_MESSAGE,
                    Map.of("message", messageOf(t, "Invalid command.")));
        } else if (t instanceof NotFoundException) {
            sendError(sender, KEY_NOT_FOUND, DEFAULT_WITH_MESSAGE,
                    Map.of("message", messageOf(t, "Not found.")));
        } else if (t instanceof ConflictException) {
            sendError(sender, KEY_CONFLICT, DEFAULT_WITH_MESSAGE,
                    Map.of("message", messageOf(t, "That conflicts with something that already exists.")));
        } else if (t instanceof ExceedsLimitException) {
            sendError(sender, KEY_EXCEEDS_LIMIT, DEFAULT_WITH_MESSAGE,
                    Map.of("message", messageOf(t, "That exceeds a limit.")));
        } else {
            sendError(sender, KEY_INTERNAL, DEFAULT_INTERNAL, Map.of());
            plugin.getLogger().log(Level.SEVERE, "Unhandled exception in " + describe(binding), t);
        }
    }

    private static String messageOf(Throwable t, String fallback) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? fallback : m;
    }

    /**
     * Render an error through the consumer's {@link Message} bean when one is
     * bound (so operators can re-word/translate via {@code hibernia.error.*}
     * keys), otherwise through the built-in MiniMessage default pattern.
     */
    private void sendError(CommandSender sender, String key, String fallbackPattern, Map<String, ?> values) {
        Message msg = resolveMessage();
        Component component;
        if (msg != null) {
            component = msg.componentOr(sender, key, fallbackPattern, values);   // sender's locale
        } else {
            String pattern = fallbackPattern;
            for (Map.Entry<String, ?> e : values.entrySet()) {
                pattern = pattern.replace("{" + e.getKey() + "}",
                        MINI.escapeTags(Objects.toString(e.getValue())));
            }
            component = MINI.deserialize(pattern);
        }
        safeMsg(sender, component);
    }

    /**
     * Look up an explicitly bound {@link Message} bean. Uses
     * {@code getExistingBinding} so plugins that never bound Message don't get
     * one created just-in-time (its constructor expects a bundled
     * messages.properties resource).
     */
    private Message resolveMessage() {
        if (!messageResolved) {
            messageResolved = true;
            if (injector != null) {
                var binding = injector.getExistingBinding(Key.get(Message.class));
                if (binding != null) {
                    message = binding.getProvider().get();
                }
            }
        }
        return message;
    }

    private Object[] extractArguments(CommandContext<CommandSourceStack> context, RouteBinding binding, CommandSender sender) throws Exception {
        List<Object> values = new ArrayList<>();

        for (Param param : binding.params) {
            if (param.sender) {
                values.add(injectSender(param.type, sender));
            } else {
                String argName = null;
                for (Segment seg : binding.path) {
                    if (!seg.literal() && seg.token().equals(param.name)) {
                        argName = seg.token();
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
                            // Primitive params don't match wrapper-keyed resolvers
                            // out of the box; transparently fall back to the
                            // wrapper resolver so handlers can declare
                            // `boolean flag` (etc.) as naturally as `Boolean flag`.
                            if (resolver == null) {
                                Class<?> wrapper = primitiveWrapper(param.type);
                                if (wrapper != null) {
                                    @SuppressWarnings("unchecked")
                                    ParameterResolver<Object> wrappedResolver =
                                            (ParameterResolver<Object>) resolvers.get(wrapper);
                                    resolver = wrappedResolver;
                                }
                            }

                            if (resolver != null) {
                                String stringValue = rawValue.toString();
                                values.add(resolver.resolve(stringValue, sender)
                                        .orElseThrow(() -> new IllegalArgumentException("Invalid " + param.name + ": " + stringValue)));
                            } else if (param.type == Integer.class || param.type == int.class
                                    || param.type == Long.class || param.type == long.class) {
                                // Brigadier's Integer/Long arg types deliver the
                                // already-typed value; pass through.
                                values.add(rawValue);
                            } else {
                                values.add(rawValue.toString());
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        if (param.optional) {
                            values.add(resolveDefault(param, sender));
                        } else {
                            throw e;
                        }
                    }
                } else if (param.optional) {
                    values.add(resolveDefault(param, sender));
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

    /**
     * Convert an optional arg's default (a raw String from the annotation)
     * to the parameter's declared type, the same way a live argument is resolved.
     *
     * <p>The sentinel {@link OptionalArg#SENDER} injects the command sender when
     * the parameter type allows it (e.g. {@code OfflinePlayer} defaulting to the
     * executing player). An empty default yields {@code null} for non-String
     * reference types — primitives with an empty default are rejected at
     * registration time.</p>
     */
    @SuppressWarnings("unchecked")
    private Object resolveDefault(Param param, CommandSender sender) throws Exception {
        Class<?> type = param.type;
        String defaultValue = param.defaultValue == null ? null : param.defaultValue.toString();

        if (OptionalArg.SENDER.equals(defaultValue)) {
            if (type.isInstance(sender)) return type.cast(sender);
            throw new IllegalArgumentException(
                    "This command can only default to the sender when run by a " + type.getSimpleName()
                            + "; specify <" + param.name + "> explicitly");
        }
        if (defaultValue == null || (defaultValue.isEmpty() && type != String.class)) {
            // "No default": the handler receives null. Primitives were rejected
            // at bind time, so null is always assignable here.
            return type == String.class ? defaultValue : null;
        }
        if (type == String.class) {
            return defaultValue;
        }

        ParameterResolver<Object> resolver = (ParameterResolver<Object>) resolvers.get(type);
        if (resolver == null) {
            Class<?> wrapper = primitiveWrapper(type);
            if (wrapper != null) {
                resolver = (ParameterResolver<Object>) resolvers.get(wrapper);
            }
        }
        if (resolver != null) {
            return resolver.resolve(defaultValue, sender).orElseThrow(() ->
                    new IllegalArgumentException("Invalid default for " + param.name + ": " + defaultValue));
        }
        // No resolver registered for this type (Brigadier supplies Integer/Long
        // typed for live args); parse the default to match the declared type.
        try {
            if (type == Integer.class || type == int.class) return Integer.parseInt(defaultValue);
            if (type == Long.class || type == long.class)   return Long.parseLong(defaultValue);
            if (type == Double.class || type == double.class) return Double.parseDouble(defaultValue);
            if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(defaultValue);
        } catch (NumberFormatException ignored) {
            // fall through to the raw string
        }
        return defaultValue;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        if (!primitive.isPrimitive()) return null;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == int.class)     return Integer.class;
        if (primitive == long.class)    return Long.class;
        if (primitive == double.class)  return Double.class;
        if (primitive == float.class)   return Float.class;
        if (primitive == short.class)   return Short.class;
        if (primitive == byte.class)    return Byte.class;
        if (primitive == char.class)    return Character.class;
        return null;
    }

    private void safeMsg(CommandSender sender, Component msg) {
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
            if (p.startsWith("<") && p.endsWith(">")) {
                segments.add(Segment.arg(p.substring(1, p.length() - 1)));
            } else if (p.startsWith("[") && p.endsWith("]")) {
                segments.add(Segment.optionalArg(p.substring(1, p.length() - 1)));
            } else {
                segments.add(Segment.literal(p));
            }
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
            else if (opt != null) {
                if (rp.getType().isPrimitive() && opt.defaultValue().isEmpty()) {
                    throw new IllegalStateException("@OptionalArg(\"" + opt.value() + "\") on " + m
                            + " has a primitive type but no defaultValue; an omitted argument would be null."
                            + " Provide a defaultValue or use the boxed type.");
                }
                params.add(Param.optional(rp.getType(), opt.value(), opt.defaultValue(), opt.sanitize()));
            }
            else throw new IllegalStateException("Parameter missing @Sender/@Arg/@OptionalArg/@GreedyArg on " + m);
        }

        validateRoute(m, raw, segments, params);

        String methodPerm = Optional.ofNullable(m.getAnnotation(Permission.class)).map(Permission::value).orElse(null);
        String effectivePerm = methodPerm != null ? methodPerm : classPerm;

        String description = Optional.ofNullable(m.getAnnotation(Description.class)).map(Description::value).orElse("");

        return new RouteBinding(instance, m, segments, params, effectivePerm, description, raw);
    }

    /**
     * Registration-time validation: every placeholder must have a matching
     * parameter, every required parameter must appear in the route, optional
     * segments must form the tail, and greedy arguments must be terminal.
     * Failing loud here is the point — a mismatch that slipped through used to
     * silently drop the rest of the route from the command tree.
     */
    private void validateRoute(Method m, String raw, List<Segment> segments, List<Param> params) {
        String where = m.getDeclaringClass().getSimpleName() + "#" + m.getName();
        Set<String> seenNames = new HashSet<>();
        boolean optionalTail = false;

        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (seg.literal()) {
                if (optionalTail) {
                    throw new IllegalStateException("Route '" + raw + "' on " + where
                            + ": literal '" + seg.token() + "' cannot follow an optional [segment]");
                }
                continue;
            }
            if (!seenNames.add(seg.token())) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + " uses argument name '" + seg.token() + "' more than once");
            }
            Param param = findParamByName(params, seg.token());
            if (param == null) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + " references argument '" + seg.token() + "' but the method has no"
                        + " @Arg/@OptionalArg/@GreedyArg parameter with that name");
            }
            if (seg.optionalArg()) {
                if (!param.optional) {
                    throw new IllegalStateException("Route '" + raw + "' on " + where
                            + ": [" + seg.token() + "] requires an @OptionalArg parameter (found a required one)");
                }
                optionalTail = true;
            } else if (optionalTail) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + ": required <" + seg.token() + "> cannot follow an optional [segment]");
            }
            if (param.greedy && i != segments.size() - 1) {
                throw new IllegalStateException("Route '" + raw + "' on " + where
                        + ": greedy argument <" + seg.token() + "> must be the last segment");
            }
        }

        for (Param param : params) {
            if (param.sender || param.optional) continue;
            boolean inRoute = segments.stream().anyMatch(s -> !s.literal() && s.token().equals(param.name));
            if (!inRoute) {
                throw new IllegalStateException("@Arg(\"" + param.name + "\") on " + where
                        + " does not appear in route '" + raw + "'; add <" + param.name
                        + "> to the route or make the parameter @OptionalArg");
            }
        }
    }

    private enum ArgKind { INTEGER, LONG, GREEDY, BIG_DECIMAL, WORD }

    private enum SegKind { LITERAL, ARG, OPTIONAL_ARG }

    private record Segment(SegKind kind, String token) {
        static Segment literal(String s) {
            return new Segment(SegKind.LITERAL, s.toLowerCase(Locale.ROOT));
        }
        static Segment arg(String name) {
            return new Segment(SegKind.ARG, name);
        }
        static Segment optionalArg(String name) {
            return new Segment(SegKind.OPTIONAL_ARG, name);
        }
        boolean literal() { return kind == SegKind.LITERAL; }
        boolean optionalArg() { return kind == SegKind.OPTIONAL_ARG; }
    }

    private record Param(boolean sender, boolean optional, boolean sanitize, boolean greedy, Class<?> type, String name, Object defaultValue) {
        static Param sender(Class<?> t) { return new Param(true, false, true, false, t, "", null); }
        static Param required(Class<?> t, String n, boolean sanitize) { return new Param(false, false, sanitize, false, t, n, null); }
        static Param greedy(Class<?> t, String n, boolean sanitize) { return new Param(false, false, sanitize, true, t, n, null); }
        static Param optional(Class<?> t, String n, Object def, boolean sanitize) { return new Param(false, true, sanitize, false, t, n, def); }
    }

    /** Per-root registration state, shared by every handler contributing to the root. */
    private static final class RootSpec {
        final LiteralArgumentBuilder<CommandSourceStack> builder;
        final Set<String> classPerms = new LinkedHashSet<>();
        final Map<String, String> routesSeen = new HashMap<>();
        final Map<String, ArgKind> argKinds = new HashMap<>();
        final Map<String, String> argChildAt = new HashMap<>();
        boolean openAccess;
        String description = "";

        RootSpec(LiteralArgumentBuilder<CommandSourceStack> builder) {
            this.builder = builder;
        }
    }

    private static class RouteBinding {
        final Object instance;
        final Method method;
        final List<Segment> path;
        final List<Param> params;
        final String permission;
        final String description;
        final String rawPattern;
        final boolean async;

        RouteBinding(Object instance, Method method, List<Segment> path, List<Param> params,
                     String permission, String description, String rawPattern) {
            this.instance = instance;
            this.method = method;
            this.path = path;
            this.params = params;
            this.permission = permission;
            this.description = description;
            this.rawPattern = rawPattern;
            this.method.setAccessible(true);
            this.async = method.isAnnotationPresent(Async.class);
        }
    }
}
