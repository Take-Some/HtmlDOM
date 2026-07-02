package dev.takesome.htmldom.desktop;

import dev.takesome.htmldom.css.UiStylesheet;
import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HtmlDomStylesheetLoaderTest {
    @Test
    void loadsLinkedStylesheetFromDocumentHead() {
        UiMarkupDocument document = new UiMarkupParser().parse(
                "<html><head><link rel=\"stylesheet\" href=\"showcase.ui.css\"></head><body><section class=\"page\"></section></body></html>",
                "html-dom/bundled/test.ui.html"
        );

        UiStylesheet stylesheet = new HtmlDomStylesheetLoader(getClass().getClassLoader()).load(
                document.dom(),
                "",
                "html-dom/bundled/test.ui.html",
                "html-dom/bundled/"
        );

        assertFalse(stylesheet.rules().isEmpty(), "linked stylesheet must produce CSS rules");
        assertTrue(hasSelector(stylesheet, ".page"), "linked showcase stylesheet must contain .page rule");
    }

    @Test
    void skipsDisabledOrNonScreenStylesheetLinks() {
        UiMarkupDocument document = new UiMarkupParser().parse(
                "<html><head>"
                        + "<link rel=\"stylesheet\" href=\"showcase.ui.css\" disabled>"
                        + "<link rel=\"stylesheet\" href=\"showcase.ui.css\" media=\"print\">"
                        + "</head><body><section class=\"page\"></section></body></html>",
                "html-dom/bundled/test.ui.html"
        );

        UiStylesheet stylesheet = new HtmlDomStylesheetLoader(getClass().getClassLoader()).load(
                document.dom(),
                "",
                "html-dom/bundled/test.ui.html",
                "html-dom/bundled/"
        );

        assertTrue(stylesheet.rules().isEmpty(), "disabled and print-only links must not be applied to the screen renderer");
    }

    @Test
    void expandsRelativeImportsFromLinkedStylesheet() {
        UiMarkupDocument document = new UiMarkupParser().parse(
                "<html><head><link rel=\"stylesheet\" href=\"root.css\"></head><body><section class=\"root-import relative-import\"></section></body></html>",
                "html-dom/imports/test.ui.html"
        );

        UiStylesheet stylesheet = new HtmlDomStylesheetLoader(getClass().getClassLoader()).load(
                document.dom(),
                "",
                "html-dom/imports/test.ui.html",
                "html-dom/imports/"
        );

        assertTrue(hasSelector(stylesheet, ".relative-import"), "@import url('nested/relative.css') must load relative to the importing CSS file");
        assertTrue(hasSelector(stylesheet, ".root-import"), "rules after @import must still be parsed");
    }

    @Test
    void expandsAbsoluteImportsFromConstructorCss(@TempDir Path tempDir) throws Exception {
        Path imported = tempDir.resolve("absolute.css");
        Files.writeString(imported, ".absolute-import { opacity: 1; }\n", StandardCharsets.UTF_8);
        String absolutePath = imported.toString().replace('\\', '/');

        UiStylesheet stylesheet = new HtmlDomStylesheetLoader(getClass().getClassLoader()).load(
                null,
                "@import url('" + absolutePath + "');\n.host-rule { display: block; }",
                "absolute-import-test.html",
                ""
        );

        assertTrue(hasSelector(stylesheet, ".absolute-import"), "@import must support absolute filesystem paths");
        assertTrue(hasSelector(stylesheet, ".host-rule"), "rules after absolute @import must still be parsed");
    }

    private static boolean hasSelector(UiStylesheet stylesheet, String selector) {
        return stylesheet.rules().stream().anyMatch(rule -> selector.equals(rule.selectorText()));
    }
}
