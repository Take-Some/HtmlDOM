package dev.takesome.htmldom.runtime;

import java.io.IOException;

@FunctionalInterface
public interface HtmlDomScriptSourceResolver {
    String load(String source) throws IOException;
}
