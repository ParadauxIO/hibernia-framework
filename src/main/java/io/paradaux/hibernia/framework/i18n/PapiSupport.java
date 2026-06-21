package io.paradaux.hibernia.framework.i18n;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Optional bridge that resolves PlaceholderAPI ({@code %token%}) placeholders in message text against a
 * player. Lets operators write {@code %player_name%}, {@code %vault_eco_balance%}, etc. in
 * {@code messages.properties} and have them filled in per recipient.
 *
 * <p>This is applied only to <strong>operator-authored</strong> text — the message pattern and
 * {@code placeholder.*} palette entries — never to caller-supplied placeholder values, so it does not
 * widen the {@code %...%} injection surface for player-controlled input.</p>
 *
 * <p>The default binding ({@link #NONE}) returns text unchanged. {@link PlaceholderApiSupport} bridges to
 * PlaceholderAPI when it is installed (and is a no-op otherwise); {@code HiberniaModule} binds it by
 * default. Bind your own implementation to integrate a different placeholder engine.</p>
 */
@FunctionalInterface
public interface PapiSupport {

    /** No-op default: returns the text unchanged. */
    PapiSupport NONE = (player, text) -> text;

    /**
     * Resolve {@code %token%} placeholders in {@code text} against {@code player}.
     *
     * @param player the player context (may be {@code null} for server/global placeholders)
     * @param text   the operator-authored text to resolve
     * @return the resolved text (unchanged when no resolver is available)
     */
    String resolve(@Nullable OfflinePlayer player, String text);
}
