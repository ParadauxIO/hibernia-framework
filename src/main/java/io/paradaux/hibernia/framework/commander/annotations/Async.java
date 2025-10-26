package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a command route method to be executed asynchronously.
 *
 * <p>When present the CommandManager will schedule the method to run off the main server
 * thread using the plugin scheduler.</p>
 *
 * <p>Note: the annotated method must be thread-safe and avoid Bukkit API calls that require
 * the main thread unless explicitly wrapped back onto it.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Async {
    // Optional: timeout/cancel policy could go here later
}
