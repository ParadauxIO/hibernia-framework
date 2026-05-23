package io.paradaux.hibernia.framework.commander;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.paradaux.hibernia.framework.commander.arguments.BigDecimalArgumentType;
import io.paradaux.hibernia.framework.commander.annotations.Arg;
import io.paradaux.hibernia.framework.commander.annotations.Async;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.GreedyArg;
import io.paradaux.hibernia.framework.commander.annotations.OptionalArg;
import io.paradaux.hibernia.framework.commander.annotations.Permission;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.annotations.Sender;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandManagerTest {

    static class DummyType {
        final String value;

        DummyType(String value) {
            this.value = value;
        }
    }

    static class DummyResolver implements ParameterResolver<DummyType> {
        @Override
        public Class<DummyType> type() {
            return DummyType.class;
        }

        @Override
        public Optional<DummyType> resolve(String token, CommandSender sender) {
            return Optional.of(new DummyType(token));
        }

        @Override
        public List<String> suggestions(String prefix, CommandSender sender) {
            return List.of("one", "two");
        }
    }

    @Command("test")
    static class TestHandler implements CommandHandler {
        String lastMessage;
        int lastNumber;
        boolean asyncCalled;

        @Route("say <message>")
        public void say(@Sender CommandSender sender, @Arg(value = "message", sanitize = false) String message) {
            this.lastMessage = message;
        }

        @Route("opt <amount>")
        public void optional(@OptionalArg(value = "amount", defaultValue = "7") int amount) {
            this.lastNumber = amount;
        }

        @Async
        @Route("async")
        public void runAsync() {
            asyncCalled = true;
        }

        @Permission("test.run")
        @Route("secure")
        public void secure() {
            // no-op
        }

        @Route("boom")
        public void boom() {
            throw new RuntimeException("kaboom");
        }

        @Route("custom <target>")
        public void custom(@Arg("target") DummyType target) {
            this.lastMessage = target.value;
        }

        @Route("plain <value>")
        public void plain(@Arg("value") Object value) {
            this.lastMessage = String.valueOf(value);
        }

        @Route("num <n>")
        public void num(@Arg("n") int n) {
            this.lastNumber = n;
        }

        @Route("price <amount>")
        public void price(@Arg("amount") BigDecimal amount) {
            // no-op
        }

        @Route("san <text>")
        public void sanitized(@Arg("text") String text) {
            this.lastMessage = text;
        }

        @Route("")
        public void optionalAtRoot(@OptionalArg(value = "amount", defaultValue = "5") int amount) {
            this.lastNumber = amount;
        }

        @Route("alpha <first> beta <second>")
        public void deep(@Arg("first") String first, @Arg("second") String second) {
            this.lastMessage = first + second;
        }

        @Route("<first>")
        public void argFirst(@Arg("first") int first) {
            this.lastNumber = first;
        }

        @Route("<sender>")
        public void senderAsArg(@Sender CommandSender sender) {
            // no-op
        }
    }

    static class BadGreedyHandler {
        @Route("bad <msg> <tail>")
        public void invalid(@GreedyArg("msg") String msg, @Arg("tail") String tail) {
            // no-op
        }
    }

    static class MissingAnnotationHandler {
        @Route("oops <value>")
        public void invalid(String value) {
            // no-op
        }
    }

    static class DefaultRouteHandler implements CommandHandler {
        boolean called;

        @Route("")
        public void root() {
            called = true;
        }
    }

    static class MismatchArgHandler {
        @Route("raw <provided>")
        public void mismatch(@Arg("different") String value) {
            // no-op
        }
    }

    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private Logger logger;
    private CommandManager manager;
    private TestHandler handler;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);
        handler = new TestHandler();

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.isPrimaryThread()).thenReturn(true);

        Set<CommandHandler> handlers = new HashSet<>();
        handlers.add(handler);
        Set<ParameterResolver<?>> resolvers = Set.of(new DummyResolver());

        manager = new CommandManager(plugin, handlers, resolvers);
    }

    @Test
    void bindRoute_andExtractArguments_resolveAndInvokeArguments() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class);
        Object binding = invokeBindRoute(handler, method, "say <message>", null);

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        when(context.getArgument("message", Object.class)).thenReturn("https://example.com/path?a=1");

        Object[] args = invokeExtractArguments(context, binding, mock(CommandSender.class));

        assertEquals(2, args.length);
        assertTrue(args[0] instanceof CommandSender);
        assertEquals("https://example.com/path?a=1", args[1]);
    }

    @Test
    void extractArguments_optionalUsesDefaultWhenArgumentMissing() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("optional", int.class);
        Object binding = invokeBindRoute(handler, method, "opt <amount>", null);

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        when(context.getArgument("amount", Object.class)).thenThrow(new IllegalArgumentException("missing"));

        Object[] args = invokeExtractArguments(context, binding, mock(CommandSender.class));

        assertEquals(1, args.length);
        assertEquals("7", args[0]);
    }

    @Test
    void createArgumentSuggestionProvider_usesPlaceholderFallbackWhenNoResolverSuggestions() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class);
        Object binding = invokeBindRoute(handler, method, "say <message>", null);
        Object param = getFirstArgParam(binding);

        Object provider = invokePrivate(
                "createArgumentSuggestionProvider",
                new Class[]{param.getClass()},
                param
        );

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        Suggestions suggestions = ((com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) provider)
                .getSuggestions(context, new SuggestionsBuilder("", 0))
                .join();

        List<String> texts = suggestions.getList().stream().map(s -> s.getText()).toList();
        assertEquals(List.of("<message>"), texts);
    }

    @Test
    void createArgumentSuggestionProvider_usesResolverSuggestions() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("custom", DummyType.class);
        Object binding = invokeBindRoute(handler, method, "custom <target>", null);
        Object param = getFirstArgParam(binding);

        Object provider = invokePrivate(
                "createArgumentSuggestionProvider",
                new Class[]{param.getClass()},
                param
        );

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        Suggestions suggestions = ((com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) provider)
                .getSuggestions(context, new SuggestionsBuilder("", 0))
                .join();

        List<String> texts = suggestions.getList().stream().map(s -> s.getText()).toList();
        assertTrue(texts.contains("one"));
        assertTrue(texts.contains("two"));
    }

    @Test
    void executeBinding_deniesWhenPermissionMissing() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("secure");
        Object binding = invokeBindRoute(handler, method, "secure", null);

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("test.run")).thenReturn(false);
        CommandContext<CommandSourceStack> context = mockCommandContext(sender);

        int result = (int) invokePrivate(
                "executeBinding",
                new Class[]{CommandContext.class, binding.getClass()},
                context,
                binding
        );

        assertEquals(0, result);
        verify(sender).sendMessage(anyString());
    }

    @Test
    void executeBinding_logsAndMessagesOnInvocationTargetException() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("boom");
        Object binding = invokeBindRoute(handler, method, "boom", null);

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        CommandContext<CommandSourceStack> context = mockCommandContext(sender);

        int result = (int) invokePrivate(
                "executeBinding",
                new Class[]{CommandContext.class, binding.getClass()},
                context,
                binding
        );

        assertEquals(1, result);
        verify(sender).sendMessage(anyString());
        verify(logger).warning(anyString());
    }

    @Test
    void executeBinding_asyncRouteUsesScheduler() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("runAsync");
        Object binding = invokeBindRoute(handler, method, "async", null);

        AtomicBoolean taskRan = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            taskRan.set(true);
            return null;
        }).when(scheduler).runTaskAsynchronously(any(JavaPlugin.class), any(Runnable.class));

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));

        int result = (int) invokePrivate(
                "executeBinding",
                new Class[]{CommandContext.class, binding.getClass()},
                context,
                binding
        );

        assertEquals(1, result);
        assertTrue(taskRan.get());
        assertTrue(handler.asyncCalled);
    }

    @Test
    void injectSender_throwsWhenTypeMismatch() {
        CommandSender sender = mock(CommandSender.class);

        assertThrows(IllegalArgumentException.class,
                () -> invokePrivate("injectSender", new Class[]{Class.class, CommandSender.class}, String.class, sender));
    }

    @Test
    void injectSender_returnsSenderWhenCompatible() throws Exception {
        CommandSender sender = mock(CommandSender.class);

        Object out = invokePrivate("injectSender", new Class[]{Class.class, CommandSender.class}, CommandSender.class, sender);

        assertEquals(sender, out);
    }

    @Test
    void createArgumentBuilder_selectsIntegerAndGreedyAndWordTypes() throws Exception {
        Object intBinding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("num", int.class),
                "num <n>", null);
        Object intParam = getFirstArgParam(intBinding);

        Object sayBinding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class),
                "say <message>", null);
        Object stringParam = getFirstArgParam(sayBinding);

        Method create = CommandManager.class.getDeclaredMethod("createArgumentBuilder", String.class, intParam.getClass());
        create.setAccessible(true);

        Object intArg = create.invoke(manager, "n", intParam);
        assertTrue(intArg instanceof RequiredArgumentBuilder<?, ?>);
        assertTrue(((RequiredArgumentBuilder<?, ?>) intArg).getType() instanceof IntegerArgumentType);

        Object strArg = create.invoke(manager, "message", stringParam);
        assertTrue(strArg instanceof RequiredArgumentBuilder<?, ?>);
        assertTrue(((RequiredArgumentBuilder<?, ?>) strArg).getType() instanceof StringArgumentType);
    }

    @Test
    void findParamByName_returnsMatchingParam_andNullWhenMissing() throws Exception {
        Object binding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class),
                "say <message>", null);

        Field paramsField = binding.getClass().getDeclaredField("params");
        paramsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) paramsField.get(binding);

        Method find = CommandManager.class.getDeclaredMethod("findParamByName", List.class, String.class);
        find.setAccessible(true);

        Object found = find.invoke(manager, params, "message");
        Object missing = find.invoke(manager, params, "unknown");

        assertNotNull(found);
        assertEquals(null, missing);
    }

    @Test
    void extractArguments_usesStringFallbackForUnknownResolverType() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("plain", Object.class);
        Object binding = invokeBindRoute(handler, method, "plain <value>", null);
        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        when(context.getArgument("value", Object.class)).thenReturn(12345);

        Object[] args = invokeExtractArguments(context, binding, mock(CommandSender.class));

        assertEquals(1, args.length);
        assertEquals("12345", args[0]);
    }

    @Test
    void extractArguments_usesPrimitiveIntFallbackWhenResolverAbsentForIntClass() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("num", int.class);
        Object binding = invokeBindRoute(handler, method, "num <n>", null);
        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        when(context.getArgument("n", Object.class)).thenReturn(9);

        Object[] args = invokeExtractArguments(context, binding, mock(CommandSender.class));

        assertEquals(1, args.length);
        assertEquals(9, args[0]);
    }

    @Test
    void safeMsg_schedulesOnMainThreadWhenNotPrimaryThread() throws Exception {
        when(server.isPrimaryThread()).thenReturn(false);

        AtomicBoolean ran = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            ran.set(true);
            return null;
        }).when(scheduler).runTask(any(JavaPlugin.class), any(Runnable.class));

        CommandSender sender = mock(CommandSender.class);
        invokePrivate("safeMsg", new Class[]{CommandSender.class, String.class}, sender, "hello");

        assertTrue(ran.get());
        verify(sender).sendMessage("hello");
    }

    @Test
    void bindRoute_throwsWhenGreedyArgIsNotLast() throws Exception {
        BadGreedyHandler bad = new BadGreedyHandler();
        Method method = BadGreedyHandler.class.getDeclaredMethod("invalid", String.class, String.class);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeBindRoute(bad, method, "bad <msg> <tail>", null));
        assertTrue(ex.getMessage().contains("@GreedyArg must be the last argument"));
    }

    @Test
    void addRoute_andBuildCommandTree_createExpectedNodes() throws Exception {
        Method sayMethod = TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class);
        Method customMethod = TestHandler.class.getDeclaredMethod("custom", DummyType.class);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("root");
        invokePrivate("buildCommandTree",
                new Class[]{LiteralArgumentBuilder.class, CommandHandler.class, List.class, String.class},
                root,
                handler,
                List.of(sayMethod, customMethod),
                null);

        var node = root.build();
        assertNotNull(node.getChild("say"));
        assertNotNull(node.getChild("custom"));
    }

    @Test
    void addRouteToArgument_handlesLiteralAndArgumentSegments() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("num", int.class);
        Object binding = invokeBindRoute(handler, method, "num <n>", null);

        RequiredArgumentBuilder<CommandSourceStack, String> parent =
                Commands.argument("base", StringArgumentType.word());

        invokePrivate("addRouteToArgument",
                new Class[]{RequiredArgumentBuilder.class, binding.getClass(), int.class},
                parent,
                binding,
                0);

        var node = parent.build();
        assertNotNull(node.getChild("num"));
    }

    @Test
    void buildCommandTree_withDefaultRoute_setsRootCommand() throws Exception {
        DefaultRouteHandler defaultHandler = new DefaultRouteHandler();
        Method rootMethod = DefaultRouteHandler.class.getDeclaredMethod("root");

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("base");
        invokePrivate("buildCommandTree",
                new Class[]{LiteralArgumentBuilder.class, CommandHandler.class, List.class, String.class},
                root,
                defaultHandler,
                List.of(rootMethod),
                null);

        var node = root.build();
        assertNotNull(node.getCommand());
    }

    @Test
    void bindRoute_throwsWhenParameterHasNoArgumentAnnotation() throws Exception {
        MissingAnnotationHandler bad = new MissingAnnotationHandler();
        Method method = MissingAnnotationHandler.class.getDeclaredMethod("invalid", String.class);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invokeBindRoute(bad, method, "oops <value>", null));
        assertTrue(ex.getMessage().contains("Parameter missing"));
    }

    @Test
    void bindRoute_usesMethodPermissionAndDescriptionAndAsyncFlag() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("secure");
        Object binding = invokeBindRoute(handler, method, "secure", "class.perm");

        assertEquals("test.run", readField(binding, "permission"));
        assertEquals("", readField(binding, "description"));
        assertFalse((boolean) readField(binding, "async"));

        Method asyncMethod = TestHandler.class.getDeclaredMethod("runAsync");
        Object asyncBinding = invokeBindRoute(handler, asyncMethod, "async", null);
        assertTrue((boolean) readField(asyncBinding, "async"));
    }

    @Test
    void extractArguments_throwsWhenRequiredArgumentMissingFromRoute() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("num", int.class);
        Object binding = invokeBindRoute(handler, method, "", null);

        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokeExtractArguments(context, binding, mock(CommandSender.class)));
        assertTrue(ex.getMessage().contains("Missing required argument"));
    }

    @Test
    void extractArguments_sanitizeFalseStringRejectsBlank() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("say", CommandSender.class, String.class);
        Object binding = invokeBindRoute(handler, method, "say <message>", null);
        CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
        when(context.getArgument("message", Object.class)).thenReturn("   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> invokeExtractArguments(context, binding, mock(CommandSender.class)));
        assertTrue(ex.getMessage().contains("blank value"));
    }

    @Test
    void safeMsg_sendsDirectlyOnPrimaryThread() throws Exception {
        when(server.isPrimaryThread()).thenReturn(true);
        CommandSender sender = mock(CommandSender.class);

        invokePrivate("safeMsg", new Class[]{CommandSender.class, String.class}, sender, "direct");

        verify(sender).sendMessage("direct");
        verify(scheduler, never()).runTask(any(JavaPlugin.class), any(Runnable.class));
    }

    @Test
    void addRoute_returnsWithoutAddingNodeWhenParamMissing() throws Exception {
        MismatchArgHandler mismatch = new MismatchArgHandler();
        Method method = MismatchArgHandler.class.getDeclaredMethod("mismatch", String.class);
        Object binding = invokeBindRoute(mismatch, method, "raw <provided>", null);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("root");
        invokePrivate("addRoute",
                new Class[]{LiteralArgumentBuilder.class, binding.getClass(), int.class, String.class},
                root,
                binding,
                0,
                null);

        var node = root.build();
        assertNotNull(node.getChild("raw"));
        assertNull(node.getChild("raw").getChild("provided"));
    }

    @Test
    void createArgumentBuilder_bigDecimalUsesBigDecimalType() throws Exception {
        Object priceBinding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("price", BigDecimal.class),
                "price <amount>", null);
        Object amountParam = getFirstArgParam(priceBinding);

        Method create = CommandManager.class.getDeclaredMethod("createArgumentBuilder", String.class, amountParam.getClass());
        create.setAccessible(true);

        Object arg = create.invoke(manager, "amount", amountParam);
        assertTrue(arg instanceof RequiredArgumentBuilder<?, ?>);
        assertTrue(((RequiredArgumentBuilder<?, ?>) arg).getType() instanceof BigDecimalArgumentType);
    }

    @Test
    void executeBinding_handlesIllegalArgumentExceptionFromExtraction() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("num", int.class);
        Object binding = invokeBindRoute(handler, method, "num <n>", null);

        CommandSender sender = mock(CommandSender.class);
        CommandContext<CommandSourceStack> context = mockCommandContext(sender);
        when(context.getArgument("n", Object.class)).thenThrow(new IllegalArgumentException("bad number"));

        int result = (int) invokePrivate(
                "executeBinding",
                new Class[]{CommandContext.class, binding.getClass()},
                context,
                binding
        );

        assertEquals(1, result);
        verify(sender).sendMessage(anyString());
    }

    @Test
    void executeBinding_handlesGenericExceptionPath() throws Exception {
        Method method = TestHandler.class.getDeclaredMethod("num", int.class);
        Object binding = invokeBindRoute(handler, method, "num <n>", null);

        CommandSender sender = mock(CommandSender.class);
        CommandContext<CommandSourceStack> context = mockCommandContext(sender);
        when(context.getArgument("n", Object.class)).thenThrow(new NullPointerException("boom"));

        int result = (int) invokePrivate(
                "executeBinding",
                new Class[]{CommandContext.class, binding.getClass()},
                context,
                binding
        );

        assertEquals(1, result);
        verify(sender).sendMessage(anyString());
        verify(logger).warning(anyString());
    }

    @Test
    void addRoute_literalWithClassPermission_addsLiteralNode() throws Exception {
        Object binding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("num", int.class),
                "num <n>", null);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("root");
        invokePrivate("addRoute",
                new Class[]{LiteralArgumentBuilder.class, binding.getClass(), int.class, String.class},
                root,
                binding,
                0,
                "perm.root");

        var node = root.build();
        assertNotNull(node.getChild("num"));
    }

    @Test
    void addRouteToArgument_depthAtEnd_setsCommand() throws Exception {
        Object binding = invokeBindRoute(handler,
                TestHandler.class.getDeclaredMethod("num", int.class),
                "num <n>", null);

        Field pathField = binding.getClass().getDeclaredField("path");
        pathField.setAccessible(true);
        List<?> path = (List<?>) pathField.get(binding);

        RequiredArgumentBuilder<CommandSourceStack, String> parent =
                Commands.argument("base", StringArgumentType.word());

        invokePrivate("addRouteToArgument",
                new Class[]{RequiredArgumentBuilder.class, binding.getClass(), int.class},
                parent,
                binding,
                path.size());

        assertNotNull(parent.build().getCommand());
    }

    @Test
    void addRouteToArgument_recursiveLiteralAndArgPath_buildsNestedNodes() throws Exception {
    Object binding = invokeBindRoute(handler,
        TestHandler.class.getDeclaredMethod("deep", String.class, String.class),
        "alpha <first> beta <second>", null);

    RequiredArgumentBuilder<CommandSourceStack, String> parent =
        Commands.argument("start", StringArgumentType.word());

    invokePrivate("addRouteToArgument",
        new Class[]{RequiredArgumentBuilder.class, binding.getClass(), int.class},
        parent,
        binding,
        1);

    var node = parent.build();
    assertNotNull(node.getChild("first"));
    assertNotNull(node.getChild("first").getChild("beta"));
    assertNotNull(node.getChild("first").getChild("beta").getChild("second"));
    }

    @Test
    void addRoute_argFirstWithClassPermission_addsArgumentNode() throws Exception {
    Object binding = invokeBindRoute(handler,
        TestHandler.class.getDeclaredMethod("argFirst", int.class),
        "<first>", null);

    LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("root");
    invokePrivate("addRoute",
        new Class[]{LiteralArgumentBuilder.class, binding.getClass(), int.class, String.class},
        root,
        binding,
        0,
        "perm.arg");

    assertNotNull(root.build().getChild("first"));
    }

    @Test
    void addRoute_argWithSenderParam_returnsWithoutAddingNode() throws Exception {
    Object binding = invokeBindRoute(handler,
        TestHandler.class.getDeclaredMethod("senderAsArg", CommandSender.class),
        "<sender>", null);

    LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("root");
    invokePrivate("addRoute",
        new Class[]{LiteralArgumentBuilder.class, binding.getClass(), int.class, String.class},
        root,
        binding,
        0,
        null);

    assertNull(root.build().getChild("sender"));
    }

    @Test
    void extractArguments_optionalWithoutRoutePlaceholder_usesDefaultValue() throws Exception {
    Object binding = invokeBindRoute(handler,
        TestHandler.class.getDeclaredMethod("optionalAtRoot", int.class),
        "", null);

    CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
    Object[] args = invokeExtractArguments(context, binding, mock(CommandSender.class));

    assertEquals(1, args.length);
    assertEquals("5", args[0]);
    }

    @Test
    void extractArguments_resolverEmpty_triggersInvalidArgumentSupplier() throws Exception {
    Object binding = invokeBindRoute(handler,
        TestHandler.class.getDeclaredMethod("sanitized", String.class),
        "san <text>", null);

    CommandContext<CommandSourceStack> context = mockCommandContext(mock(CommandSender.class));
    when(context.getArgument("text", Object.class)).thenReturn("!@#$");

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> invokeExtractArguments(context, binding, mock(CommandSender.class)));
    assertTrue(ex.getMessage().contains("Invalid text"));
    }

    private Object readField(Object instance, String field) throws Exception {
        Field f = instance.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(instance);
    }

    private CommandContext<CommandSourceStack> mockCommandContext(CommandSender sender) {
        CommandContext<CommandSourceStack> context = mock(CommandContext.class);
        CommandSourceStack stack = mock(CommandSourceStack.class);
        when(context.getSource()).thenReturn(stack);
        when(stack.getSender()).thenReturn(sender);
        return context;
    }

    private Object invokeBindRoute(Object instance, Method method, String route, String classPerm) throws Exception {
        Route routeAnnotation = new Route() {
            @Override
            public Class<Route> annotationType() {
                return Route.class;
            }

            @Override
            public String value() {
                return route;
            }
        };

        return invokePrivate(
                "bindRoute",
                new Class[]{Object.class, Method.class, Route.class, String.class},
                instance,
                method,
                routeAnnotation,
                classPerm
        );
    }

    private Object[] invokeExtractArguments(CommandContext<CommandSourceStack> context, Object binding, CommandSender sender) throws Exception {
        Object out = invokePrivate(
                "extractArguments",
                new Class[]{CommandContext.class, binding.getClass(), CommandSender.class},
                context,
                binding,
                sender
        );
        assertNotNull(out);
        return (Object[]) out;
    }

    private Object getFirstArgParam(Object binding) throws Exception {
        Field params = binding.getClass().getDeclaredField("params");
        params.setAccessible(true);
        List<?> list = (List<?>) params.get(binding);
        for (Object candidate : list) {
            Field senderField = candidate.getClass().getDeclaredField("sender");
            senderField.setAccessible(true);
            if (!(boolean) senderField.get(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No non-sender parameter found");
    }

    private Object invokePrivate(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = CommandManager.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(manager, args);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getTargetException();
            if (cause instanceof Exception exception) throw exception;
            throw ite;
        }
    }
}
