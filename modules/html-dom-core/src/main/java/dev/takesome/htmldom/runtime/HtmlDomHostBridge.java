package dev.takesome.htmldom.runtime;

/**
 * Host operations exposed to document scripts.
 *
 * <p>This bridge is intentionally desktop/application-neutral. It does not know about
 * Minecraft, Forge, Swing windows, web URLs as navigation contexts, or game state.</p>
 */
public interface HtmlDomHostBridge {
    HtmlDomHostBridge NOOP = new HtmlDomHostBridge() { };

    default void close(HtmlDomDocument document) {
    }

    default void openDocument(HtmlDomDocument document, String target) {
    }

    default void openUrl(HtmlDomDocument document, String url) {
    }
}
