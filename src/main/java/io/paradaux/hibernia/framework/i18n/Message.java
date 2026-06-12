package io.paradaux.hibernia.framework.i18n;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.models.HiberniaPlayer;
import lombok.extern.slf4j.Slf4j;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Templated messaging over a {@code messages.properties} file with MiniMessage
 * formatting and {@code {placeholder}} substitution.
 *
 * <p>Placeholders resolve in order: caller-supplied values, then
 * {@code <namespace>.placeholder.*} keys, then global {@code placeholder.*}
 * keys; expansion is recursive (bounded) so placeholders can reference each
 * other.</p>
 *
 * <p><strong>Caller-supplied values are inert by default:</strong> MiniMessage
 * tags in a value are escaped (rendered literally) and braces in a value are
 * never re-expanded, so player-controlled strings cannot inject markup or
 * recurse into other placeholders. To deliberately pass markup through a value
 * — e.g. a pre-formatted amount — wrap it with {@link #rich(String)}.</p>
 *
 * <p>This is a single-file template system, not full internationalisation:
 * there is one {@code messages.properties} per plugin and no per-player locale
 * selection (yet).</p>
 */
@Slf4j
@Singleton
public final class Message {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.]+)}");
    private static final String LBR = "\u0000_LBR_";
    private static final String RBR = "\u0000_RBR_";
    private static final int MAX_EXPANSION_DEPTH = 8;

    private final JavaPlugin plugin;
    private final Path file;
    private final Properties props = new Properties();
    private final MiniMessage mm = MiniMessage.miniMessage();

    // cached placeholder maps
    private Map<String, String> globalPh = Map.of();
    private Map<String, Map<String,String>> nsPh = Map.of();

    @Inject
    public Message(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.file = plugin.getDataFolder().toPath().resolve("messages.properties");
        ensureDefaultFile();
        reload();
    }

    /**
     * Mark a placeholder value as trusted MiniMessage markup: it is substituted
     * verbatim (tags parsed, braces expandable) instead of being escaped.
     * Only use for values the operator controls — never for raw player input.
     */
    public static Rich rich(String value) {
        return new Rich(Objects.requireNonNull(value));
    }

    /** Wrapper marking a placeholder value as trusted markup. See {@link #rich(String)}. */
    public record Rich(String value) {
        @Override
        public String toString() {
            return value;
        }
    }

    public String format(String key, Object... kvPairs) {
        return format(key, kvToMap(kvPairs));
    }

    public Component component(String key, Object... kvPairs) {
        return mm.deserialize(format(key, kvPairs));
    }

    public Component component(String key, Map<String, ?> values) {
        return mm.deserialize(format(key, values));
    }

    /**
     * Render {@code key} when it exists in the messages file, otherwise render
     * the given MiniMessage {@code fallbackPattern} through the same placeholder
     * pipeline. Used by the framework to give every built-in message an
     * operator-overridable key without requiring it in every plugin's file.
     */
    public Component componentOr(String key, String fallbackPattern, Object... kvPairs) {
        return componentOr(key, fallbackPattern, kvToMap(kvPairs));
    }

    public Component componentOr(String key, String fallbackPattern, Map<String, ?> values) {
        String pattern = props.getProperty(key);
        if (pattern == null) pattern = fallbackPattern;
        return mm.deserialize(formatPattern(pattern, namespaceOf(key), values));
    }

    public void send(CommandSender to, String key, Object... kvPairs) {
        to.sendMessage(component(key, kvPairs));
    }

    public void send(HiberniaPlayer to, String key, Object... kvPairs) {
        send(to.getUniqueId(), key, kvPairs);
    }

    public void send(UUID to, String key, Object... kvPairs) {
        Player player = Bukkit.getPlayer(to);
        if (player != null) {
            send(player, key, kvPairs);
        }
    }

    public void send(Collection<? extends CommandSender> recipients, String key, Object... kvPairs) {
        Component msg = component(key, kvPairs);
        for (CommandSender s : recipients) s.sendMessage(msg);
    }

    public void broadcast(String key, Object... kvPairs) {
        Component msg = component(key, kvPairs);
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void ensureDefaultFile() {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder: " + dir);
        }

        // Only write the bundled default when the operator has no file yet —
        // saveResource(..., true) would overwrite operator-edited messages on
        // every boot. replace=false is also passed as belt-and-suspenders.
        if (!Files.exists(file)) {
            plugin.saveResource("messages.properties", false);
        }
    }

    public synchronized void reload() {
        props.clear();
        try (var reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (Exception e) {
            log.error("Failed to load {}: {}", file, e.getMessage());
        }
        rebuildPlaceholderCaches();
    }

    private void rebuildPlaceholderCaches() {
        Map<String, String> global = new LinkedHashMap<>();
        Map<String, Map<String,String>> ns = new LinkedHashMap<>();

        for (String key : props.stringPropertyNames()) {
            String val = props.getProperty(key);
            if (key.startsWith("placeholder.")) {
                global.put(key.substring("placeholder.".length()), val);
            } else {
                int dot = key.indexOf('.');
                if (dot > 0 && key.regionMatches(dot + 1, "placeholder.", 0, "placeholder.".length())) {
                    String nsName = key.substring(0, dot);
                    String phName = key.substring(dot + 1 + "placeholder.".length());
                    ns.computeIfAbsent(nsName, k -> new LinkedHashMap<>()).put(phName, val);
                }
            }
        }

        // Expand placeholder values recursively (so {prefix} can use {color}, etc.)
        this.globalPh = expandAll(global, global, ns.getOrDefault("placeholder", Map.of()));
        Map<String, Map<String,String>> expandedNs = new LinkedHashMap<>();
        for (var e : ns.entrySet()) {
            // namespace placeholders see: their own map + global
            Map<String,String> merged = new LinkedHashMap<>(globalPh);
            merged.putAll(e.getValue());
            expandedNs.put(e.getKey(), expandAll(e.getValue(), merged, Map.of()));
        }
        this.nsPh = expandedNs;
    }

    private Map<String,String> expandAll(Map<String,String> source, Map<String,String> primary, Map<String,String> secondary) {
        Map<String,String> out = new LinkedHashMap<>(source.size());
        for (var e : source.entrySet()) {
            out.put(e.getKey(), expandString(e.getValue(), primary, secondary, MAX_EXPANSION_DEPTH));
        }
        return out;
    }

    private String raw(String key) {
        return props.getProperty(key, key);
    }

    private static String namespaceOf(String key) {
        int i = key.indexOf('.');
        return i > 0 ? key.substring(0, i) : "";
    }

    /** Expand {name} using: user values -> ns placeholders -> global placeholders; recursively. */
    public String format(String key, Map<String, ?> values) {
        return formatPattern(raw(key), namespaceOf(key), values);
    }

    private String formatPattern(String pattern, String ns, Map<String, ?> values) {
        // escape literal braces
        pattern = pattern.replace("{{", LBR).replace("}}", RBR);

        // merged resolvers for this key
        Map<String,String> resolved = new LinkedHashMap<>();
        if (!globalPh.isEmpty()) resolved.putAll(globalPh);
        Map<String,String> nsMap = nsPh.get(ns);
        if (nsMap != null) resolved.putAll(nsMap);
        // user-supplied values override placeholders
        if (values != null) {
            for (var e : values.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    resolved.put(e.getKey(), renderValue(e.getValue()));
                }
            }
        }

        String out = expandString(pattern, resolved, Map.of(), MAX_EXPANSION_DEPTH);

        // restore literal braces
        return out.replace(LBR, "{").replace(RBR, "}");
    }

    /**
     * Caller-supplied values are inert unless wrapped in {@link Rich}: tags are
     * escaped so they render literally, and braces are sentinel-encoded so a
     * value like "{prefix}" can't trigger another round of expansion.
     */
    private String renderValue(Object value) {
        if (value instanceof Rich rich) {
            return rich.value();
        }
        String s = Objects.toString(value);
        return mm.escapeTags(s).replace("{", LBR).replace("}", RBR);
    }

    private String expandString(String input, Map<String,String> primary, Map<String,String> secondary, int depth) {
        if (depth <= 0 || input.indexOf('{') < 0) return input;
        StringBuilder builder = new StringBuilder(input.length() + 16);
        Matcher m = PLACEHOLDER.matcher(input);
        boolean changed = false;
        while (m.find()) {
            String name = m.group(1);
            String repl = primary.getOrDefault(name, secondary.get(name));
            if (repl == null) {
                m.appendReplacement(builder, Matcher.quoteReplacement("{" + name + "}"));
            } else {
                changed = true;
                m.appendReplacement(builder, Matcher.quoteReplacement(repl));
            }
        }
        m.appendTail(builder);
        return changed ? expandString(builder.toString(), primary, secondary, depth - 1) : builder.toString();
    }

    private static Map<String, Object> kvToMap(Object... kvPairs) {
        if ((kvPairs.length & 1) == 1) {
            throw new IllegalArgumentException("Placeholder arguments must be in pairs: key, value, …");
        }
        Map<String, Object> map = new LinkedHashMap<>(kvPairs.length / 2);
        for (int i = 0; i < kvPairs.length; i += 2) {
            Object k = kvPairs[i];
            if (!(k instanceof String)) {
                throw new IllegalArgumentException("Placeholder name at index " + i + " must be a String");
            }
            map.put((String) k, kvPairs[i + 1]);
        }
        return map;
    }
}
