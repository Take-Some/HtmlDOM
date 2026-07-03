package dev.takesome.htmldom.runtime;

/** Action invocation payload shared by Java and Lua action handlers. */
public record HtmlDomActionEvent(
        HtmlDomDocument document,
        String action,
        String prefix,
        String payload
) {
    public HtmlDomActionEvent {
        action = action == null ? "" : action.trim();
        prefix = prefix == null ? "" : prefix.trim();
        payload = payload == null ? "" : payload;
    }
}
