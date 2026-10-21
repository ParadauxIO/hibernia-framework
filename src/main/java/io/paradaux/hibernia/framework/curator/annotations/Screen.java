package io.paradaux.hibernia.framework.curator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a public method returning MenuView. Accepts MenuFlow, Player and @Model parameters. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Screen {
    String value() default "main";
}
