package dev.takesome.htmldom.css;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UiCssImportStatementParserTest {
    @Test
    void skipsSemicolonAtRulesBeforeNormalRules() {
        UiStylesheet stylesheet = new UiCssParser().parse("""
                @charset "UTF-8";
                @import url('theme.css');

                .host {
                    display: block;
                }
                """);

        assertEquals(1, stylesheet.rules().size());
        assertEquals(".host", stylesheet.rules().get(0).selectorText());
    }
}
