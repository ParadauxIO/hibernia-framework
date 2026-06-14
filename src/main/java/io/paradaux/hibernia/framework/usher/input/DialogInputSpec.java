package io.paradaux.hibernia.framework.usher.input;

import io.paradaux.hibernia.framework.usher.Text;

import java.util.List;
import java.util.Objects;

/**
 * A renderer-agnostic description of one dialog input. The {@link io.paradaux.hibernia.framework.usher.render.DialogRenderer}
 * turns it into the matching Paper {@code DialogInput}; an {@link io.paradaux.hibernia.framework.usher.spi.InputBinder}
 * reads its submitted value back by {@link #key()}.
 *
 * <p>Use the static factories rather than the canonical constructor.</p>
 *
 * @param kind     the input kind
 * @param key      the response key (matches an {@code @Input("key")} parameter)
 * @param label    the field label
 * @param options  option entries for {@link Kind#TOGGLE}/{@link Kind#OPTION}; empty otherwise
 * @param min      lower bound for {@link Kind#NUMBER}
 * @param max      upper bound for {@link Kind#NUMBER}
 * @param step     step for {@link Kind#NUMBER} (nullable)
 * @param initial  initial numeric value for {@link Kind#NUMBER} (nullable), or initial boolean for {@link Kind#BOOLEAN}
 * @param width    field width in pixels, or {@code 0} for the client default
 */
public record DialogInputSpec(
        Kind kind,
        String key,
        Text label,
        List<OptionSpec> options,
        float min,
        float max,
        Float step,
        Float initial,
        int width) {

    public enum Kind { TEXT, BOOLEAN, TOGGLE, NUMBER, OPTION }

    /** One choice in a {@link Kind#TOGGLE} or {@link Kind#OPTION} input. */
    public record OptionSpec(String id, Text label, boolean initial) {
        public OptionSpec {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }

    public DialogInputSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** A free-text field. */
    public static DialogInputSpec text(String key, Text label) {
        return new DialogInputSpec(Kind.TEXT, key, label, List.of(), 0, 0, null, null, 0);
    }

    /** A native checkbox; reads back as {@code true}/{@code false}. */
    public static DialogInputSpec bool(String key, Text label, boolean initial) {
        return new DialogInputSpec(Kind.BOOLEAN, key, label, List.of(), 0, 0, null, initial ? 1f : 0f, 0);
    }

    /**
     * An on/off toggle rendered as a two-option dropdown (clearer to skim than a checkbox — the pattern
     * dialog code otherwise hand-rolls). Reads back as a boolean; option ids are {@code "true"}/{@code "false"}.
     */
    public static DialogInputSpec toggle(String key, Text label, Text onLabel, Text offLabel, boolean initial) {
        List<OptionSpec> opts = List.of(
                new OptionSpec("true", onLabel, initial),
                new OptionSpec("false", offLabel, !initial));
        return new DialogInputSpec(Kind.TOGGLE, key, label, opts, 0, 0, null, null, 0);
    }

    /** A single-choice dropdown; reads back as the chosen option id (or a domain type via a custom binder). */
    public static DialogInputSpec option(String key, Text label, List<OptionSpec> options) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("option input '" + key + "' needs at least one option");
        }
        return new DialogInputSpec(Kind.OPTION, key, label, options, 0, 0, null, null, 0);
    }

    /** A numeric slider; reads back as int/long/float/double. */
    public static DialogInputSpec number(String key, Text label, float min, float max, Float step, Float initial) {
        if (max < min) {
            throw new IllegalArgumentException("number input '" + key + "' has max < min");
        }
        return new DialogInputSpec(Kind.NUMBER, key, label, List.of(), min, max, step, initial, 0);
    }

    /** Return a copy with the given field width. */
    public DialogInputSpec withWidth(int width) {
        return new DialogInputSpec(kind, key, label, options, min, max, step, initial, width);
    }
}
