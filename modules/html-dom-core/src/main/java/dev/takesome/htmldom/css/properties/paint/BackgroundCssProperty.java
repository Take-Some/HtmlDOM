package dev.takesome.htmldom.css.properties.paint;

import dev.takesome.htmldom.css.UiCssDeclaration;
import dev.takesome.htmldom.css.UiCssParseContext;
import dev.takesome.htmldom.css.UiCssShorthandPropertySpec;
import dev.takesome.htmldom.css.UiCssShorthandSupport;
import dev.takesome.htmldom.css.UiCssStringPropertySpec;
import dev.takesome.htmldom.css.UiCssTokenSplitter;
import dev.takesome.htmldom.css.UiCssUrlNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BackgroundCssProperty extends UiCssStringPropertySpec implements UiCssShorthandPropertySpec {
    private static final Set<String> REPEAT = Set.of("repeat", "repeat-x", "repeat-y", "no-repeat", "space", "round");
    private static final Set<String> POSITION = Set.of("left", "right", "top", "bottom", "center");

    public BackgroundCssProperty() {
        super("background", Set.of(), true);
    }

    public List<UiCssDeclaration> expand(UiCssParseContext context, String rawValue) {
        ArrayList<UiCssDeclaration> out = new ArrayList<>();
        List<String> layers = UiCssTokenSplitter.splitTopLevelComma(rawValue);
        String firstLayer = layers.isEmpty() ? rawValue : layers.get(0);
        List<String> tokens = UiCssShorthandSupport.tokens(firstLayer);
        if (tokens.isEmpty()) return out;

        String image = "";
        String color = "";
        String repeat = "";
        ArrayList<String> position = new ArrayList<>();
        ArrayList<String> size = new ArrayList<>();
        boolean readingSize = false;

        for (String token : tokens) {
            String lower = UiCssShorthandSupport.lower(token);
            if ("/".equals(lower)) {
                readingSize = true;
                continue;
            }
            if (lower.startsWith("url(")) {
                image = UiCssUrlNormalizer.normalizeUrlValue(token);
            } else if (lower.startsWith("linear-gradient(") || lower.startsWith("radial-gradient(")) {
                image = token;
            } else if (repeat.isBlank() && REPEAT.contains(lower)) {
                repeat = token;
            } else if (color.isBlank() && UiCssShorthandSupport.colorLike(token)) {
                color = token;
            } else if (readingSize) {
                size.add(token);
            } else if (POSITION.contains(lower) || UiCssShorthandSupport.lengthLike(lower)) {
                position.add(token);
            } else {
                position.add(token);
            }
        }

        if (!color.isBlank()) out.add(new UiCssDeclaration("background-color", color));
        if (!image.isBlank()) out.add(new UiCssDeclaration("background-image", image));
        if (!position.isEmpty()) out.add(new UiCssDeclaration("background-position", String.join(" ", position)));
        if (!size.isEmpty()) out.add(new UiCssDeclaration("background-size", String.join(" ", size)));
        if (!repeat.isBlank()) out.add(new UiCssDeclaration("background-repeat", repeat));
        return out;
    }
}
