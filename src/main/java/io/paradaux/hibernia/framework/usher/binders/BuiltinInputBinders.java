package io.paradaux.hibernia.framework.usher.binders;

import io.papermc.paper.dialog.DialogResponseView;
import io.paradaux.hibernia.framework.usher.spi.InputBinder;

import java.util.List;
import java.util.Locale;

/**
 * The framework's built-in {@link InputBinder}s, covering the value types a screen's inputs produce:
 * text → {@code String}, native checkbox / on-off toggle → {@code Boolean}, slider →
 * {@code Integer}/{@code Long}/{@code Float}/{@code Double}.
 *
 * <p>The parsing helpers are static and side-effect-free so they can be unit-tested directly.</p>
 */
public final class BuiltinInputBinders {

    private BuiltinInputBinders() {}

    /** Every built-in binder, in registration order. */
    public static List<InputBinder<?>> all() {
        return List.of(
                binder(String.class, BuiltinInputBinders::readString),
                binder(Boolean.class, BuiltinInputBinders::readBoolean),
                binder(Integer.class, (view, key) -> mapFloat(view, key, f -> Math.round(f))),
                binder(Long.class, (view, key) -> mapFloat(view, key, f -> (long) Math.floor(f))),
                binder(Float.class, (view, key) -> mapFloat(view, key, f -> f)),
                binder(Double.class, (view, key) -> mapFloat(view, key, f -> (double) f)));
    }

    /**
     * Read a string input. {@link DialogResponseView#getText} covers text fields and the chosen id of
     * option/toggle inputs.
     */
    public static String readString(DialogResponseView view, String key) {
        return view.getText(key);
    }

    /**
     * Read a boolean. A native checkbox arrives via {@link DialogResponseView#getBoolean}; an on/off
     * toggle (rendered as a two-option dropdown) arrives as text — accept the common truthy tokens so a
     * handler can declare {@code @Input boolean} regardless of which input kind the screen used.
     */
    public static Boolean readBoolean(DialogResponseView view, String key) {
        Boolean direct = view.getBoolean(key);
        if (direct != null) return direct;
        String text = view.getText(key);
        if (text == null) return null;
        return switch (text.toLowerCase(Locale.ROOT).trim()) {
            case "true", "yes", "y", "1", "on", "enabled" -> Boolean.TRUE;
            case "false", "no", "n", "0", "off", "disabled" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static <T> T mapFloat(DialogResponseView view, String key, FloatMapper<T> mapper) {
        Float value = view.getFloat(key);
        return value == null ? null : mapper.map(value);
    }

    private static <T> InputBinder<T> binder(Class<T> type, Reader<T> reader) {
        return new InputBinder<>() {
            @Override
            public Class<T> type() {
                return type;
            }

            @Override
            public T read(DialogResponseView view, String key) {
                return reader.read(view, key);
            }
        };
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(DialogResponseView view, String key);
    }

    @FunctionalInterface
    private interface FloatMapper<T> {
        T map(float value);
    }
}
