package io.paradaux.hibernia.framework.commander;

/**
 * A bound handler-method parameter: a {@code @Sender} injection, a required/optional
 * {@code @Arg}, or a {@code @GreedyArg}. Package-private value type with package-visible
 * fields so the binder, tree-builder and runtime extractor can read it directly.
 */
final class Param {
    final boolean sender;
    final boolean optional;
    final boolean sanitize;
    final boolean greedy;
    final Class<?> type;
    final String name;
    final Object defaultValue;

    private Param(boolean sender, boolean optional, boolean sanitize, boolean greedy,
                  Class<?> type, String name, Object defaultValue) {
        this.sender = sender;
        this.optional = optional;
        this.sanitize = sanitize;
        this.greedy = greedy;
        this.type = type;
        this.name = name;
        this.defaultValue = defaultValue;
    }

    static Param sender(Class<?> t) {
        return new Param(true, false, true, false, t, "", null);
    }

    static Param required(Class<?> t, String n, boolean sanitize) {
        return new Param(false, false, sanitize, false, t, n, null);
    }

    static Param greedy(Class<?> t, String n, boolean sanitize) {
        return new Param(false, false, sanitize, true, t, n, null);
    }

    static Param optional(Class<?> t, String n, Object def, boolean sanitize) {
        return new Param(false, true, sanitize, false, t, n, def);
    }
}
