package io.paradaux.hibernia.framework.usher;

import io.paradaux.hibernia.framework.usher.input.DialogInputSpec;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A renderer-agnostic description of a dialog screen — title, body, inputs and buttons — returned from a
 * {@link io.paradaux.hibernia.framework.usher.annotations.Screen @Screen} method. The
 * {@link io.paradaux.hibernia.framework.usher.render.DialogRenderer} turns it into a Paper dialog and
 * shows it; keeping it a plain spec is what makes the dialog tier unit-testable without a running server.
 *
 * <p>Build one with the kind-specific entry points:</p>
 * <pre>
 * DialogView.notice("info.title").body("info.body").build();
 *
 * DialogView.confirmation("delete.title")
 *     .body("delete.body")
 *     .confirm("button.yes", "doDelete")     // @Action("doDelete")
 *     .deny("button.no")                       // closes
 *     .build();
 *
 * DialogView.multiAction("find.title")
 *     .toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", false)
 *     .button("find.search", "submit")         // @Action("submit")
 *     .open("find.filters", "filters")         // opens the "filters" screen
 *     .exit("button.close")
 *     .columns(1)
 *     .build();
 * </pre>
 */
public final class DialogView {

    /** Maps to Paper's {@code DialogBase.DialogAfterAction}, kept Paper-free in the spec. */
    public enum AfterAction { NONE, CLOSE, WAIT_FOR_RESPONSE }

    public enum Kind { NOTICE, CONFIRMATION, MULTI_ACTION }

    /** A body element: a message line or an item stack. */
    public sealed interface Body permits Body.Message, Body.Item {
        record Message(Text text) implements Body {}
        record Item(ItemStack item) implements Body {}
    }

    private final Kind kind;
    private final Text title;
    private final Text externalTitle;
    private final boolean canCloseWithEscape;
    private final AfterAction afterAction;
    private final List<Body> bodies;
    private final List<DialogInputSpec> inputs;
    private final List<ButtonSpec> buttons;   // CONFIRMATION: [yes, no]; NOTICE: [ok?]; MULTI_ACTION: actions
    private final ButtonSpec exitButton;      // MULTI_ACTION only
    private final int columns;                // MULTI_ACTION only

    private DialogView(Builder b) {
        this.kind = b.kind;
        this.title = b.title;
        this.externalTitle = b.externalTitle;
        this.canCloseWithEscape = b.canCloseWithEscape;
        this.afterAction = b.afterAction;
        this.bodies = List.copyOf(b.bodies);
        this.inputs = List.copyOf(b.inputs);
        this.buttons = List.copyOf(b.buttons);
        this.exitButton = b.exitButton;
        this.columns = b.columns;
    }

    public static Builder notice(Text title) { return new Builder(Kind.NOTICE, title); }

    public static Builder notice(String titleKey) { return notice(Text.key(titleKey)); }

    public static Builder confirmation(Text title) { return new Builder(Kind.CONFIRMATION, title); }

    public static Builder confirmation(String titleKey) { return confirmation(Text.key(titleKey)); }

    public static Builder multiAction(Text title) { return new Builder(Kind.MULTI_ACTION, title); }

    public static Builder multiAction(String titleKey) { return multiAction(Text.key(titleKey)); }

    public Kind kind() { return kind; }

    public Text title() { return title; }

    public Text externalTitle() { return externalTitle; }

    public boolean canCloseWithEscape() { return canCloseWithEscape; }

    public AfterAction afterAction() { return afterAction; }

    public List<Body> bodies() { return bodies; }

    public List<DialogInputSpec> inputs() { return inputs; }

    public List<ButtonSpec> buttons() { return buttons; }

    public ButtonSpec exitButton() { return exitButton; }

    public int columns() { return columns; }

    /** Fluent builder. Method availability depends on the {@link Kind} chosen at the entry point. */
    public static final class Builder {
        private final Kind kind;
        private final Text title;
        private Text externalTitle;
        private boolean canCloseWithEscape = true;
        private AfterAction afterAction = AfterAction.NONE;
        private final List<Body> bodies = new ArrayList<>();
        private final List<DialogInputSpec> inputs = new ArrayList<>();
        private final List<ButtonSpec> buttons = new ArrayList<>();
        private ButtonSpec confirmButton;
        private ButtonSpec denyButton;
        private ButtonSpec exitButton;
        private int columns = 1;

        private Builder(Kind kind, Text title) {
            this.kind = kind;
            this.title = Objects.requireNonNull(title, "title");
        }

        /** The short title shown on the button that re-opens this dialog (Paper's "external title"). */
        public Builder externalTitle(Text externalTitle) {
            this.externalTitle = externalTitle;
            return this;
        }

        public Builder canCloseWithEscape(boolean value) {
            this.canCloseWithEscape = value;
            return this;
        }

        public Builder afterAction(AfterAction afterAction) {
            this.afterAction = Objects.requireNonNull(afterAction, "afterAction");
            return this;
        }

        public Builder body(Text text) {
            bodies.add(new Body.Message(text));
            return this;
        }

        public Builder body(String key, Object... placeholders) {
            return body(Text.key(key, placeholders));
        }

        public Builder bodyItem(ItemStack item) {
            bodies.add(new Body.Item(Objects.requireNonNull(item, "item")));
            return this;
        }

        public Builder input(DialogInputSpec input) {
            inputs.add(Objects.requireNonNull(input, "input"));
            return this;
        }

        public Builder text(String key, Text label) {
            return input(DialogInputSpec.text(key, label));
        }

        public Builder bool(String key, Text label, boolean initial) {
            return input(DialogInputSpec.bool(key, label, initial));
        }

        public Builder toggle(String key, Text label, Text onLabel, Text offLabel, boolean initial) {
            return input(DialogInputSpec.toggle(key, label, onLabel, offLabel, initial));
        }

        public Builder toggle(String key, String labelKey, String onKey, String offKey, boolean initial) {
            return toggle(key, Text.key(labelKey), Text.key(onKey), Text.key(offKey), initial);
        }

        public Builder option(String key, Text label, List<DialogInputSpec.OptionSpec> options) {
            return input(DialogInputSpec.option(key, label, options));
        }

        public Builder number(String key, Text label, float min, float max, Float step, Float initial) {
            return input(DialogInputSpec.number(key, label, min, max, step, initial));
        }

        // ── MULTI_ACTION ──────────────────────────────────────────────────────────

        /** Add a button that runs {@code @Action(action)}. (MULTI_ACTION) */
        public Builder button(Text label, String action) {
            requireKind(Kind.MULTI_ACTION, "button");
            buttons.add(ButtonSpec.action(label, action));
            return this;
        }

        public Builder button(String labelKey, String action) {
            return button(Text.key(labelKey), action);
        }

        public Builder button(ButtonSpec button) {
            requireKind(Kind.MULTI_ACTION, "button");
            buttons.add(Objects.requireNonNull(button, "button"));
            return this;
        }

        /** Add a button that opens another screen of this handler. (MULTI_ACTION) */
        public Builder open(Text label, String screen) {
            requireKind(Kind.MULTI_ACTION, "open");
            buttons.add(ButtonSpec.open(label, screen));
            return this;
        }

        public Builder open(String labelKey, String screen) {
            return open(Text.key(labelKey), screen);
        }

        /** Set the exit (bottom) button. (MULTI_ACTION) */
        public Builder exit(ButtonSpec button) {
            requireKind(Kind.MULTI_ACTION, "exit");
            this.exitButton = button;
            return this;
        }

        public Builder exit(String labelKey) {
            return exit(ButtonSpec.close(Text.key(labelKey)));
        }

        public Builder columns(int columns) {
            requireKind(Kind.MULTI_ACTION, "columns");
            if (columns < 1) throw new IllegalArgumentException("columns must be >= 1");
            this.columns = columns;
            return this;
        }

        // ── CONFIRMATION ──────────────────────────────────────────────────────────

        /** The yes/confirm button. (CONFIRMATION) */
        public Builder confirm(ButtonSpec button) {
            requireKind(Kind.CONFIRMATION, "confirm");
            this.confirmButton = button;
            return this;
        }

        public Builder confirm(String labelKey, String action) {
            return confirm(ButtonSpec.action(Text.key(labelKey), action));
        }

        /** The no/deny button. (CONFIRMATION) */
        public Builder deny(ButtonSpec button) {
            requireKind(Kind.CONFIRMATION, "deny");
            this.denyButton = button;
            return this;
        }

        public Builder deny(String labelKey, String action) {
            return deny(ButtonSpec.action(Text.key(labelKey), action));
        }

        /** Deny button that simply closes the dialog. (CONFIRMATION) */
        public Builder deny(String labelKey) {
            return deny(ButtonSpec.close(Text.key(labelKey)));
        }

        // ── NOTICE ────────────────────────────────────────────────────────────────

        /** Override the single NOTICE button (default is an auto-generated close button). */
        public Builder ok(ButtonSpec button) {
            requireKind(Kind.NOTICE, "ok");
            buttons.clear();
            buttons.add(button);
            return this;
        }

        public Builder ok(String labelKey, String action) {
            return ok(ButtonSpec.action(Text.key(labelKey), action));
        }

        public DialogView build() {
            switch (kind) {
                case CONFIRMATION -> {
                    if (confirmButton == null || denyButton == null) {
                        throw new IllegalStateException("confirmation dialog requires both confirm(...) and deny(...)");
                    }
                    buttons.clear();
                    buttons.add(confirmButton);
                    buttons.add(denyButton);
                }
                case NOTICE -> {
                    if (buttons.size() > 1) {
                        throw new IllegalStateException("notice dialog allows at most one button");
                    }
                    // 0 buttons → renderer supplies the default close button.
                }
                case MULTI_ACTION -> {
                    if (buttons.isEmpty()) {
                        throw new IllegalStateException("multi-action dialog requires at least one button");
                    }
                }
            }
            return new DialogView(this);
        }

        private void requireKind(Kind required, String method) {
            if (kind != required) {
                throw new IllegalStateException(method + "(...) is only valid on a " + required + " dialog, not " + kind);
            }
        }
    }
}
