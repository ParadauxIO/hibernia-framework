package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a subpath (route) for a command method.
 *
 * <p>The value is a space-separated pattern where literals are plain tokens
 * and arguments are enclosed in &lt;&gt; (e.g. {@code "give &lt;player&gt; &lt;amount&gt;"}).
 * An empty string denotes the root route for the command.</p>
 *
 * @see io.paradaux.hibernia.framework.commander.CommandManager#bindRoute(Object, java.lang.reflect.Method, String)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Route {
    /** Subpath e.g. "", "balance", "give <player> <amount>" */
    String value();
}
