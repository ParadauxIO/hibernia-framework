package io.paradaux.hibernia.framework.usher;

import io.paradaux.hibernia.framework.usher.input.DialogInputSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogViewTest {

    @Test
    void notice_defaultsToNoButtons_andEscapeClosable() {
        DialogView view = DialogView.notice("info.title").body("info.body").build();

        assertEquals(DialogView.Kind.NOTICE, view.kind());
        assertTrue(view.buttons().isEmpty());
        assertTrue(view.canCloseWithEscape());
        assertEquals(1, view.bodies().size());
        assertTrue(view.bodies().get(0) instanceof DialogView.Body.Message);
    }

    @Test
    void notice_okSetsSingleButton_replacingPrevious() {
        DialogView view = DialogView.notice("t").ok("a", "actA").ok("b", "actB").build();

        assertEquals(1, view.buttons().size());
        assertEquals("actB", view.buttons().get(0).target());
    }

    @Test
    void confirmation_requiresBothButtons_andOrdersYesNo() {
        assertThrows(IllegalStateException.class,
                () -> DialogView.confirmation("t").confirm("yes", "save").build());

        DialogView view = DialogView.confirmation("delete.title")
                .body("delete.body")
                .confirm("button.yes", "doDelete")
                .deny("button.no")
                .build();

        assertEquals(DialogView.Kind.CONFIRMATION, view.kind());
        assertEquals(2, view.buttons().size());
        assertEquals(ButtonSpec.Kind.ACTION, view.buttons().get(0).kind());
        assertEquals("doDelete", view.buttons().get(0).target());
        assertEquals(ButtonSpec.Kind.CLOSE, view.buttons().get(1).kind());
    }

    @Test
    void multiAction_requiresAtLeastOneButton() {
        assertThrows(IllegalStateException.class, () -> DialogView.multiAction("t").build());
    }

    @Test
    void multiAction_collectsButtonsExitAndColumns() {
        DialogView view = DialogView.multiAction("find.title")
                .toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", false)
                .button("find.search", "submit")
                .open("find.filters", "filters")
                .exit("button.close")
                .columns(2)
                .build();

        assertEquals(2, view.columns());
        assertEquals(2, view.buttons().size());
        assertEquals(ButtonSpec.Kind.ACTION, view.buttons().get(0).kind());
        assertEquals(ButtonSpec.Kind.OPEN, view.buttons().get(1).kind());
        assertEquals("filters", view.buttons().get(1).target());
        assertEquals(ButtonSpec.Kind.CLOSE, view.exitButton().kind());

        assertEquals(1, view.inputs().size());
        DialogInputSpec toggle = view.inputs().get(0);
        assertEquals(DialogInputSpec.Kind.TOGGLE, toggle.kind());
        assertEquals(List.of("true", "false"), toggle.options().stream().map(DialogInputSpec.OptionSpec::id).toList());
    }

    @Test
    void kindSpecificMethods_rejectWrongKind() {
        assertThrows(IllegalStateException.class, () -> DialogView.notice("t").button("x", "a"));
        assertThrows(IllegalStateException.class, () -> DialogView.confirmation("t").columns(2));
        assertThrows(IllegalStateException.class, () -> DialogView.multiAction("t").confirm("y", "a"));
    }

    @Test
    void numberInput_validatesBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> DialogInputSpec.number("n", Text.key("l"), 10, 0, null, null));
    }

    @Test
    void toggleInput_initialFlipsOptionDefaults() {
        DialogInputSpec on = DialogInputSpec.toggle("k", Text.key("l"), Text.key("on"), Text.key("off"), true);
        assertTrue(on.options().get(0).initial());   // "true" option is selected
        assertFalse(on.options().get(1).initial());
    }

    @Test
    void text_keyValidatesPairs() {
        assertThrows(IllegalArgumentException.class, () -> Text.key("k", "only-one"));
        assertNull(((Text.Keyed) Text.key("k")).placeholders().get("missing"));
    }
}
