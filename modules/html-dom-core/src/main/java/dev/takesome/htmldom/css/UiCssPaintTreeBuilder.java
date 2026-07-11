package dev.takesome.htmldom.css;

import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Builds a CSS-like paint tree from computed DOM styles and resolved layout boxes. */
public final class UiCssPaintTreeBuilder {
    private int order;

    public UiCssPaintTree build(UiDomDocument document, UiCssLayoutResult layout) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(layout, "layout");
        this.order = 0;
        ArrayList<UiCssPaintNode> nodes = new ArrayList<>();
        append(document.documentElement(), 0, 0, layout, nodes);
        return new UiCssPaintTree(nodes);
    }

    public List<UiDomElement> orderedChildren(UiDomElement parent) {
        if (parent == null) return List.of();
        ArrayList<UiDomElement> children = new ArrayList<>();
        for (UiDomNode childNode : parent.children()) {
            if (childNode instanceof UiDomElement child && !skip(child)) children.add(child);
        }
        children.sort(Comparator
                .comparingInt(this::paintBucket)
                .thenComparingInt(this::zIndex)
                .thenComparingInt(UiDomElement::nodeId));
        return children;
    }

    private void append(UiDomElement element, int parentNodeId, int depth, UiCssLayoutResult layout, ArrayList<UiCssPaintNode> nodes) {
        if (skip(element)) return;
        UiCssBox box = layout.box(element).orElse(new UiCssBox(0f, 0f, 0f, 0f));
        nodes.add(new UiCssPaintNode(
                element.nodeId(),
                parentNodeId,
                depth,
                order++,
                element.tagName(),
                element.id(),
                String.join(" ", element.classList().values()),
                box.x(), box.y(), box.width(), box.height(),
                zIndex(element),
                zIndexAuto(element),
                positioned(element),
                stackingContext(element),
                scrollContainer(element),
                opacity(element),
                phases(element)
        ));
        for (UiDomElement child : orderedChildren(element)) append(child, element.nodeId(), depth + 1, layout, nodes);
    }

    private List<UiCssPaintPhase> phases(UiDomElement element) {
        ArrayList<UiCssPaintPhase> phases = new ArrayList<>();
        if (hasBoxShadow(element)) phases.add(UiCssPaintPhase.BOX_SHADOW);
        if (hasBackground(element)) phases.add(UiCssPaintPhase.BACKGROUND);
        if (hasBorder(element)) phases.add(UiCssPaintPhase.BORDER);
        phases.add(UiCssPaintPhase.CONTENT);
        if (hasMask(element)) phases.add(UiCssPaintPhase.MASK);
        if (!element.style("outline", "").isBlank()) phases.add(UiCssPaintPhase.OUTLINE);
        return phases;
    }

    private int paintBucket(UiDomElement element) {
        int z = zIndex(element);
        if (positioned(element) && !zIndexAuto(element) && z < 0) return 0;
        if (!positioned(element)) return 1;
        if (zIndexAuto(element) || z == 0) return 2;
        return 3;
    }

    private boolean skip(UiDomElement element) {
        String tag = element.tagName();
        return tag.equals("head") || tag.equals("title") || tag.equals("style") || tag.equals("option") || "none".equals(element.style("display", ""));
    }

    private boolean positioned(UiDomElement element) {
        String pos = element.style("position", "").toLowerCase(Locale.ROOT);
        return pos.equals("relative") || pos.equals("absolute") || pos.equals("fixed") || pos.equals("sticky");
    }

    private boolean stackingContext(UiDomElement element) {
        if (element.parent() == null) return true;
        if (positioned(element) && !zIndexAuto(element)) return true;
        if (opacity(element) < 0.999f) return true;
        String transform = element.style("transform", "").trim().toLowerCase(Locale.ROOT);
        if (!transform.isBlank() && !"none".equals(transform)) return true;
        if (UiCssMaskResolver.resolve(element).active()) return true;
        String mixBlend = element.style("mix-blend-mode", "").trim().toLowerCase(Locale.ROOT);
        return !mixBlend.isBlank() && !"normal".equals(mixBlend);
    }

    private boolean zIndexAuto(UiDomElement element) {
        String raw = element.style("z-index", "").trim();
        return raw.isBlank() || "auto".equalsIgnoreCase(raw);
    }

    private int zIndex(UiDomElement element) {
        String raw = element.style("z-index", "").trim();
        if (raw.isBlank() || "auto".equalsIgnoreCase(raw)) return 0;
        try { return Integer.parseInt(raw); }
        catch (RuntimeException ignored) { return 0; }
    }

    private float opacity(UiDomElement element) {
        String raw = element.style("opacity", "").trim();
        if (raw.isBlank()) return 1f;
        try { return Math.max(0f, Math.min(1f, Float.parseFloat(raw))); }
        catch (RuntimeException ignored) { return 1f; }
    }

    private boolean scrollContainer(UiDomElement element) {
        String overflow = first(element, "overflow", "overflow-x", "overflow-y").toLowerCase(Locale.ROOT);
        return overflow.equals("hidden") || overflow.equals("clip") || overflow.equals("auto") || overflow.equals("scroll");
    }

    private boolean hasBoxShadow(UiDomElement element) {
        String value = element.style("box-shadow", "").trim();
        return !value.isBlank() && !"none".equalsIgnoreCase(value);
    }

    private boolean hasMask(UiDomElement element) {
        return UiCssMaskResolver.resolve(element).active();
    }

    private boolean hasBackground(UiDomElement element) {
        return !first(element, "background", "background-image", "background-color").isBlank();
    }

    private boolean hasBorder(UiDomElement element) {
        return !first(element, "border", "border-width", "border-color", "border-style").isBlank();
    }

    private String first(UiDomElement element, String... names) {
        for (String name : names) {
            String value = element.style(name, "");
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
