package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as a greedy command argument that consumes all remaining input.
 *
 * <p>Unlike {@link Arg}, which captures a single word, a greedy argument captures everything
 * from its position to the end of the command string. This makes it suitable for arguments
 * that may contain spaces or special characters, such as URLs or multi-word names.</p>
 *
 * <p><strong>Important:</strong> A {@code @GreedyArg} must be the last argument in the route,
 * as it consumes all remaining input.</p>
 *
 * <p>Example:
 * <pre>
 * &#064;Route("announce &lt;message&gt;")
 * public void announce(@Sender Player sender, @GreedyArg("message") String message) { ... }
 *
 * &#064;Route("link &lt;url&gt;")
 * public void link(@Sender Player sender, @GreedyArg(value = "url", sanitize = false) String url) { ... }
 * </pre>
 * </p>
 *
 * @see io.paradaux.hibernia.framework.commander.CommandManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface GreedyArg {
    /**
     * The name of the argument as used in the route placeholder (without &lt;&gt;).
     *
     * @return the argument name
     */
    String value(); // name

    /**
     * Whether to sanitize the argument value (strip MiniMessage tags, special characters, etc.).
     *
     * <p>Defaults to {@code true}. Set to {@code false} when the argument may contain
     * special characters such as URLs (e.g. {@code https://example.com}).</p>
     *
     * @return {@code true} if the value should be sanitized
     */
    boolean sanitize() default true;
}
