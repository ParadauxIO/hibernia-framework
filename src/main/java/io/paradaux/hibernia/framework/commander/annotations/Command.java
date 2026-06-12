package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a command handler root.
 *
 * <p>The annotated class provides one or more root labels (command names) under which
 * its {@link io.paradaux.hibernia.framework.commander.annotations.Route @Route}-annotated
 * methods will be registered.</p>
 *
 * <p>Example:
 * <pre>
 * @Command({"eco", "economy"})
 * public class EconomyCommands implements CommandHandler { ... }
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Command {
    /** Root labels e.g. {"eco","economy"} */
    String[] value();

    /**
     * Human-readable description of the command, passed to Paper's command
     * registrar (shown in {@code /help} output). When several handler classes
     * share a root label, the first non-empty description wins.
     *
     * @return the command description, or an empty string for none
     */
    String description() default "";
}
