package dev.takesome.htmldom.runtime;

@FunctionalInterface
public interface HtmlDomAction {
    void run(HtmlDomActionEvent event);
}
