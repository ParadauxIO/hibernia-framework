package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for repeatable {@link Route} annotations.
 *
 * <p>You do not need to use this directly; simply annotate a method with
 * multiple {@code @Route} annotations and the compiler will wrap them
 * automatically.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Routes {
    Route[] value();
}
