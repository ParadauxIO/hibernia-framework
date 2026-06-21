package io.paradaux.hibernia.framework.usher.spi;

import org.bukkit.entity.Player;

/**
 * Detection hook for Bedrock (Geyser/Floodgate) players.
 *
 * <p>Bedrock clients render server dialogs through Geyser's form translation, which degrades richer
 * multi-action layouts. This SPI lets a handler branch on the viewer's platform (e.g. send a chat
 * summary instead of a wait-screen dialog, as ChestShop does) without taking a hard Floodgate
 * dependency in the framework.</p>
 *
 * <p>The default binding ({@link #NONE}) reports everyone as Java. A consumer with Floodgate on the
 * classpath binds an implementation that consults {@code FloodgateApi}.</p>
 *
 * <p>This is a <em>detection</em> hook only: the framework does not divert rendering for Bedrock
 * players. {@link io.paradaux.hibernia.framework.usher.DialogFlow#isBedrockViewer()} surfaces the
 * result to handler code.</p>
 */
@FunctionalInterface
public interface BedrockSupport {

    /** Always-Java default. */
    BedrockSupport NONE = player -> false;

    boolean isBedrock(Player player);
}
