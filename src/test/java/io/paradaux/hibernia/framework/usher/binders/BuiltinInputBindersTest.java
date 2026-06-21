package io.paradaux.hibernia.framework.usher.binders;

import io.papermc.paper.dialog.DialogResponseView;
import io.paradaux.hibernia.framework.usher.spi.InputBinder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltinInputBindersTest {

    private final Map<Class<?>, InputBinder<?>> byType = BuiltinInputBinders.all().stream()
            .collect(Collectors.toMap(InputBinder::type, b -> b));

    @SuppressWarnings("unchecked")
    private <T> T read(Class<T> type, DialogResponseView view, String key) {
        return ((InputBinder<T>) byType.get(type)).read(view, key);
    }

    @Test
    void registersAllScalarTypes() {
        assertTrue(byType.keySet().containsAll(java.util.List.of(
                String.class, Boolean.class, Integer.class, Long.class, Float.class, Double.class)));
    }

    @Test
    void readString_returnsTextValue() {
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getText("name")).thenReturn("Alex");

        assertEquals("Alex", read(String.class, view, "name"));
        assertNull(read(String.class, view, "absent"));
    }

    @Test
    void readBoolean_usesNativeCheckboxWhenPresent() {
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getBoolean("flag")).thenReturn(true);

        assertTrue(read(Boolean.class, view, "flag"));
    }

    @Test
    void readBoolean_fallsBackToToggleTextTokens() {
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getBoolean("t")).thenReturn(null);
        when(view.getText("t")).thenReturn("true");
        assertTrue(read(Boolean.class, view, "t"));

        when(view.getText("t")).thenReturn("disabled");
        assertFalse(read(Boolean.class, view, "t"));

        when(view.getText("t")).thenReturn("nonsense");
        assertNull(read(Boolean.class, view, "t"));

        when(view.getText("t")).thenReturn(null);
        assertNull(read(Boolean.class, view, "t"));
    }

    @Test
    void numericBinders_convertSliderFloat() {
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getFloat("n")).thenReturn(3.7f);

        assertEquals(4, read(Integer.class, view, "n"));      // rounds
        assertEquals(3L, read(Long.class, view, "n"));        // floors
        assertEquals(3.7f, read(Float.class, view, "n"));
        assertEquals(3.7d, read(Double.class, view, "n"), 1e-4);
    }

    @Test
    void numericBinders_returnNullWhenAbsent() {
        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getFloat("n")).thenReturn(null);

        assertNull(read(Integer.class, view, "n"));
        assertNull(read(Double.class, view, "n"));
    }

    @Test
    void readBoolean_static_acceptsCommonTokens() {
        DialogResponseView view = mock(DialogResponseView.class);
        for (String t : java.util.List.of("true", "yes", "y", "1", "on", "enabled")) {
            when(view.getBoolean("k")).thenReturn(null);
            when(view.getText("k")).thenReturn(t);
            assertTrue(BuiltinInputBinders.readBoolean(view, "k"), t);
        }
        for (String f : java.util.List.of("false", "no", "n", "0", "off", "disabled")) {
            when(view.getBoolean("k")).thenReturn(null);
            when(view.getText("k")).thenReturn(f);
            assertFalse(BuiltinInputBinders.readBoolean(view, "k"), f);
        }
    }
}
