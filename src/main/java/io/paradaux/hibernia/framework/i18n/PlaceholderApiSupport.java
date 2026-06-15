package io.paradaux.hibernia.framework.i18n;

import com.google.inject.Singleton;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * {@link PapiSupport} backed by PlaceholderAPI, wired reflectively so the framework takes no compile- or
 * run-time dependency on it. When PlaceholderAPI is not installed this is a no-op; when it is, it
 * delegates to {@code PlaceholderAPI.setPlaceholders(OfflinePlayer, String)}.
 *
 * <p>This is the default {@link PapiSupport} binding, so PlaceholderAPI "just works" in messages once the
 * plugin is present, and costs nothing when it is absent (text without {@code %} short-circuits).</p>
 */
@Singleton
public final class PlaceholderApiSupport implements PapiSupport {

    private static final Method SET_PLACEHOLDERS = lookup();

    @Override
    public String resolve(@Nullable OfflinePlayer player, String text) {
        if (text == null || text.indexOf('%') < 0 || SET_PLACEHOLDERS == null) {
            return text;
        }
        // Guard on the live plugin so we never touch PlaceholderAPI classes when it isn't loaded.
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return text;
        }
        try {
            Object result = SET_PLACEHOLDERS.invoke(null, player, text);
            return result instanceof String s ? s : text;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return text;
        }
    }

    private static Method lookup() {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException e) {
            return null;   // PlaceholderAPI not on the classpath — stays a no-op
        }
    }
}
