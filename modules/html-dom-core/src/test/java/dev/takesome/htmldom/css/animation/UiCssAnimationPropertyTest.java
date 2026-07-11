package dev.takesome.htmldom.css.animation;

import dev.takesome.htmldom.css.UiCssDeclaration;
import dev.takesome.htmldom.css.UiCssParseContext;
import dev.takesome.htmldom.css.UiCssPropertyRegistry;
import dev.takesome.htmldom.css.properties.animation.AnimationCssProperty;
import dev.takesome.htmldom.css.transition.UiCssTransitionTiming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UiCssAnimationPropertyTest {
    @Test
    void builtinsRegisterAnimationShorthandAndLonghands() {
        UiCssPropertyRegistry registry = UiCssPropertyRegistry.loadBuiltins();

        for (String property : List.of(
                "animation",
                "animation-name",
                "animation-duration",
                "animation-delay",
                "animation-timing-function",
                "animation-iteration-count",
                "animation-direction",
                "animation-fill-mode",
                "animation-play-state"
        )) {
            assertTrue(registry.find(property).isPresent(), property + " must be registered");
        }
    }

    @Test
    void animationShorthandExpandsToCompleteLonghandSet() {
        List<UiCssDeclaration> declarations = new AnimationCssProperty().expand(
                new UiCssParseContext(),
                "helix-rise-in 420ms ease-out 160ms both alternate 2 running"
        );

        assertEquals("helix-rise-in", declaration(declarations, "animation-name"));
        assertEquals("420ms", declaration(declarations, "animation-duration"));
        assertEquals("160ms", declaration(declarations, "animation-delay"));
        assertEquals("ease-out", declaration(declarations, "animation-timing-function"));
        assertEquals("2", declaration(declarations, "animation-iteration-count"));
        assertEquals("alternate", declaration(declarations, "animation-direction"));
        assertEquals("both", declaration(declarations, "animation-fill-mode"));
        assertEquals("running", declaration(declarations, "animation-play-state"));
    }


    @Test
    void cubicBezierTimingFunctionIsEvaluated() {
        double linear = UiCssTransitionTiming.apply("linear", 0.5);
        double eased = UiCssTransitionTiming.apply("cubic-bezier(.16, 1, .3, 1)", 0.5);

        assertTrue(eased > 0.5 && eased <= 1.0, "custom cubic-bezier must accelerate the mid-frame progress");
        assertEquals(0.5, linear, 0.00001);
    }

    private static String declaration(List<UiCssDeclaration> declarations, String property) {
        return declarations.stream()
                .filter(declaration -> property.equals(declaration.property()))
                .findFirst()
                .orElseThrow()
                .value();
    }
}
