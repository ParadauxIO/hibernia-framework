package io.paradaux.hibernia.framework.commander.spi;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

/**
 * Converts a raw command-argument token into a typed value, and (optionally)
 * supplies tab-completion suggestions for it.
 *
 * <p>Implementations are registered against {@link #type()} — the parameter
 * class a handler method declares — either as framework built-ins or through a
 * Guice {@code Multibinder<ParameterResolver<?>>} in the consuming plugin.</p>
 *
 * <h2>Threading contract</h2>
 * <p><strong>{@code resolve} is called on the thread the route executes on.</strong>
 * For a normal route that is the server main thread; for an {@code @Async} route
 * it is a Bukkit async worker thread. <strong>{@code suggestions} is always
 * called off the main thread</strong> (Brigadier completes suggestions
 * asynchronously). Implementations must therefore be thread-safe and must not
 * touch Bukkit API that requires the main thread from {@code suggestions} (or
 * from {@code resolve} when used by {@code @Async} routes) — prefer
 * service-managed caches, as e.g. a player-name cache, over live world/entity
 * access.</p>
 */
public interface ParameterResolver<T> {

    /** The parameter class this resolver services (use the boxed type for primitives). */
    Class<T> type();

    /**
     * Resolve a single argument token to a value.
     *
     * @return the resolved value, or {@link Optional#empty()} when the token is
     *         invalid — the framework then rejects the input with an
     *         invalid-argument message and the handler is never invoked
     */
    Optional<T> resolve(String token, CommandSender sender) throws Exception;

    /**
     * Tab-completion candidates for the partial token {@code prefix}.
     * Called off the main thread — see the threading contract above.
     */
    default List<String> suggestions(String prefix, CommandSender sender) { return List.of(); }
}
