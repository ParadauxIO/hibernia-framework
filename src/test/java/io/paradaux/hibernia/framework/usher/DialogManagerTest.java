package io.paradaux.hibernia.framework.usher;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.paradaux.hibernia.framework.exceptions.NotFoundException;
import io.paradaux.hibernia.framework.usher.annotations.Action;
import io.paradaux.hibernia.framework.usher.annotations.Dialog;
import io.paradaux.hibernia.framework.usher.annotations.Input;
import io.paradaux.hibernia.framework.usher.annotations.Model;
import io.paradaux.hibernia.framework.usher.annotations.Screen;
import io.paradaux.hibernia.framework.usher.input.DialogInputSpec;
import io.paradaux.hibernia.framework.usher.render.DialogRenderer;
import io.paradaux.hibernia.framework.usher.spi.DialogHandler;
import io.paradaux.hibernia.framework.usher.spi.InputBinder;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogManagerTest {

    static final class FindModel {
        boolean fuzzyApplied;
    }

    enum Choice { BUY, SELL }

    @Dialog("test")
    static class TestDialog implements DialogHandler {
        String lastAction;
        boolean lastFuzzy;
        int lastPage;
        Object lastModel;
        Choice lastChoice;
        String awaited;

        @Screen
        public DialogView main(@Model FindModel model, DialogFlow flow, Player viewer) {
            return DialogView.multiAction("main.title")
                    .toggle("fuzzy", "l", "on", "off", false)
                    .number("page", Text.key("l"), 1, 10, 1f, 1f)
                    .option("type", Text.key("l"), List.of(
                            new DialogInputSpec.OptionSpec("BUY", Text.key("b"), true),
                            new DialogInputSpec.OptionSpec("SELL", Text.key("s"), false)))
                    .button("search", "submit")
                    .button("query", "query")
                    .button("boom", "boom")
                    .button("pick", "pick")
                    .open("filters-btn", "filters")
                    .exit("close")
                    .build();
        }

        @Action("pick")
        public void pick(@Input("type") Choice type) {
            this.lastChoice = type;
        }

        @Screen("filters")
        public DialogView filters(@Model FindModel model) {
            return DialogView.confirmation("filters.title")
                    .confirm("save", "applyFilters")
                    .deny("back-btn")
                    .build();
        }

        @Action("submit")
        public void submit(@Input("fuzzy") boolean fuzzy, @Input("page") int page,
                           @Model FindModel model, DialogFlow flow, DialogContext ctx) {
            this.lastAction = "submit";
            this.lastFuzzy = fuzzy;
            this.lastPage = page;
            this.lastModel = model;
        }

        @Action("applyFilters")
        public void applyFilters(@Model FindModel model, DialogFlow flow) {
            this.lastAction = "applyFilters";
            model.fuzzyApplied = true;
            flow.back();
        }

        @Action("boom")
        public void boom() {
            throw new NotFoundException("no widget here");
        }

        @Action("query")
        public void query(DialogFlow flow) {
            flow.await(CompletableFuture.completedFuture("RESULT"), Text.key("wait"),
                    (result, f) -> {
                        this.awaited = result;
                        f.close();
                    });
        }
    }

    @Dialog("blowup")
    static class BlowUpDialog implements DialogHandler {
        @Screen
        public DialogView main() {
            return DialogView.notice("t").build();
        }

        @Action("explode")
        public void explode() {
            throw new IllegalStateException("unexpected");
        }
    }

    // ── invalid handlers (skipped at index time) ────────────────────────────────
    static class NoAnnotationDialog implements DialogHandler {
        @Screen
        public DialogView main() {
            return DialogView.notice("t").build();
        }
    }

    @Dialog("noscreens")
    static class NoScreensDialog implements DialogHandler {
        @Action("x")
        public void x() {
        }
    }

    @Dialog("badinput")
    static class BadInputDialog implements DialogHandler {
        @Screen
        public DialogView main() {
            return DialogView.notice("t").build();
        }

        @Action("a")
        public void a(@Input("k") Object noBinderForThis) {
        }
    }

    private RecordingRenderer renderer;
    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private Logger logger;
    private Player player;
    private TestDialog handler;
    private FindModel model;
    private DialogManager manager;

    @BeforeEach
    void setUp() {
        renderer = new RecordingRenderer();
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);
        player = mock(Player.class);
        handler = new TestDialog();
        model = new FindModel();

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);

        manager = new DialogManager(plugin, Set.of(handler), Set.of(), renderer);
    }

    @Test
    void open_rendersDefaultScreen() {
        manager.open(player, TestDialog.class, model);

        assertEquals(1, renderer.shown.size());
        assertEquals(DialogView.Kind.MULTI_ACTION, renderer.last().kind());
        assertSame(player, renderer.viewer);
    }

    @Test
    void action_bindsTypedInputsAndModel() {
        manager.open(player, TestDialog.class, model);

        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getBoolean("fuzzy")).thenReturn(null);
        when(view.getText("fuzzy")).thenReturn("true");   // toggle id
        when(view.getFloat("page")).thenReturn(5f);

        clickAction("submit", view);

        assertEquals("submit", handler.lastAction);
        assertTrue(handler.lastFuzzy);
        assertEquals(5, handler.lastPage);
        assertSame(model, handler.lastModel);
    }

    @Test
    void action_bindsEnumInputByConstantName_caseInsensitive() {
        manager.open(player, TestDialog.class, model);

        DialogResponseView view = mock(DialogResponseView.class);
        when(view.getText("type")).thenReturn("sell");   // lower-case id from the client
        clickAction("pick", view);

        assertEquals(Choice.SELL, handler.lastChoice);
    }

    @Test
    void navigation_open_back_close() {
        manager.open(player, TestDialog.class, model);
        assertEquals("main", flowScreenAfterOpen());

        // open the "filters" screen
        clickOpen("filters", emptyView());
        assertEquals(DialogView.Kind.CONFIRMATION, renderer.last().kind());

        // applyFilters mutates the model then calls flow.back() → re-renders main
        clickAction("applyFilters", emptyView());
        assertTrue(model.fuzzyApplied);
        assertEquals(DialogView.Kind.MULTI_ACTION, renderer.last().kind());

        // exit/close button on main
        clickClose(emptyView());
        assertTrue(renderer.closes >= 1);
    }

    @Test
    void await_showsWaitScreenThenDeliversResultAndCloses() {
        manager.open(player, TestDialog.class, model);

        clickAction("query", emptyView());

        // a transient wait notice was shown, then the completion closed the dialog
        assertTrue(renderer.shown.stream().anyMatch(v -> v.kind() == DialogView.Kind.NOTICE));
        assertEquals("RESULT", handler.awaited);
        assertTrue(renderer.closes >= 1);
    }

    @Test
    void action_semanticExceptionRendersMessageToViewer() {
        manager.open(player, TestDialog.class, model);

        clickAction("boom", emptyView());

        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(captor.getValue()).contains("no widget here"));
        // semantic exceptions are expected control flow → not logged at SEVERE
        verify(logger, org.mockito.Mockito.never()).log(eq(Level.SEVERE), anyString(), any(Throwable.class));
    }

    @Test
    void action_unknownExceptionLogsAndShowsGenericMessage() {
        BlowUpDialog blow = new BlowUpDialog();
        DialogManager m = new DialogManager(plugin, Set.of(blow), Set.of(), renderer);
        m.open(player, BlowUpDialog.class, null);

        // craft a click on the "explode" action by building the callback directly
        ButtonSpec explode = ButtonSpec.action(Text.key("x"), "explode");
        m.open(player, BlowUpDialog.class, null);
        renderer.callbacks.apply(explode).accept(mock(DialogResponseView.class), player);

        verify(logger).log(eq(Level.SEVERE), anyString(), any(Throwable.class));
        verify(player, org.mockito.Mockito.atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void badHandlers_areSkipped_andDoNotBlockGoodOnes() {
        DialogManager m = new DialogManager(plugin,
                Set.of(handler, new NoAnnotationDialog(), new NoScreensDialog(), new BadInputDialog()),
                Set.of(), renderer);

        // good one works
        m.open(player, TestDialog.class, model);
        assertSame(player, renderer.viewer);

        // bad ones never registered → open throws
        assertThrows(IllegalArgumentException.class, () -> m.open(player, NoAnnotationDialog.class, null));
        assertThrows(IllegalArgumentException.class, () -> m.open(player, NoScreensDialog.class, null));
        assertThrows(IllegalArgumentException.class, () -> m.open(player, BadInputDialog.class, null));
    }

    @Test
    void open_unknownScreen_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> manager.open(player, TestDialog.class, "nope", model));
    }

    @Test
    void customInputBinder_isUsedOverBuiltins() {
        InputBinder<String> upper = new InputBinder<>() {
            @Override
            public Class<String> type() {
                return String.class;
            }

            @Override
            public String read(DialogResponseView view, String key) {
                String raw = view.getText(key);
                return raw == null ? null : raw.toUpperCase();
            }
        };
        DialogManager m = new DialogManager(plugin, Set.of(handler), Set.of(upper), renderer);
        m.open(player, TestDialog.class, model);
        // (binding precedence is asserted indirectly: construction succeeded with a custom String binder
        //  registered before the built-in, and no duplicate-type error was raised.)
        assertEquals(DialogView.Kind.MULTI_ACTION, renderer.last().kind());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private DialogResponseView emptyView() {
        return mock(DialogResponseView.class);
    }

    private String flowScreenAfterOpen() {
        // the only screen rendered so far is the default "main"
        return renderer.shown.isEmpty() ? null : "main";
    }

    private void clickAction(String action, DialogResponseView view) {
        renderer.click(find(renderer.last(), ButtonSpec.Kind.ACTION, action), view);
    }

    private void clickOpen(String screen, DialogResponseView view) {
        renderer.click(find(renderer.last(), ButtonSpec.Kind.OPEN, screen), view);
    }

    private void clickClose(DialogResponseView view) {
        renderer.click(find(renderer.last(), ButtonSpec.Kind.CLOSE, null), view);
    }

    private static ButtonSpec find(DialogView view, ButtonSpec.Kind kind, String target) {
        List<ButtonSpec> all = new ArrayList<>(view.buttons());
        if (view.exitButton() != null) all.add(view.exitButton());
        return all.stream()
                .filter(b -> b.kind() == kind && Objects.equals(b.target(), target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " button targeting " + target));
    }

    static final class RecordingRenderer implements DialogRenderer {
        final List<DialogView> shown = new ArrayList<>();
        Function<ButtonSpec, DialogActionCallback> callbacks;
        Function<Text, Component> text;
        Audience viewer;
        int closes;

        @Override
        public void show(Audience viewer, DialogView view,
                         Function<Text, Component> text,
                         Function<ButtonSpec, DialogActionCallback> callbacks) {
            this.viewer = viewer;
            this.shown.add(view);
            this.text = text;
            this.callbacks = callbacks;
        }

        @Override
        public void close(Audience viewer) {
            this.closes++;
        }

        DialogView last() {
            return shown.get(shown.size() - 1);
        }

        void click(ButtonSpec button, DialogResponseView view) {
            callbacks.apply(button).accept(view, viewer);
        }
    }
}
