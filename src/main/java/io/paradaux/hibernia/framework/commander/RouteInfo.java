package io.paradaux.hibernia.framework.commander;

/**
 * Metadata describing one registered command route, exposed via
 * {@link CommandManager#routeIndex()} so consumers can build help output
 * (or other tooling) from the routes the framework actually registered
 * instead of hand-maintaining a parallel list.
 *
 * @param root        the root command label the route is registered under (e.g. {@code "eco"})
 * @param pattern     the raw route pattern as written in the {@code @Route} annotation
 *                    (e.g. {@code "give <player> <amount>"}; empty string for the root route)
 * @param description the {@code @Description} text, or an empty string when absent
 * @param permission  the effective permission gating the route (method-level
 *                    {@code @Permission}, falling back to class-level), or {@code null}
 * @param async       whether the route is dispatched off the main thread ({@code @Async})
 */
public record RouteInfo(String root, String pattern, String description, String permission, boolean async) {
}
