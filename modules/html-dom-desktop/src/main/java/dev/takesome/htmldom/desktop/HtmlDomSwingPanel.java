package dev.takesome.htmldom.desktop;

import dev.takesome.htmldom.css.UiCssCascade;
import dev.takesome.htmldom.css.UiCssLayoutEngine;
import dev.takesome.htmldom.css.UiCssLayoutResult;
import dev.takesome.htmldom.css.UiCssPaintTreeBuilder;
import dev.takesome.htmldom.css.UiCssPropertyRegistry;
import dev.takesome.htmldom.css.UiCssScrollBox;
import dev.takesome.htmldom.css.UiCssStyleImpact;
import dev.takesome.htmldom.css.UiStylesheet;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceBundle;
import dev.takesome.htmldom.css.animation.UiCssAnimationDescriptor;
import dev.takesome.htmldom.css.animation.UiCssAnimationResolver;
import dev.takesome.htmldom.devtools.HtmlDomDevTools;
import dev.takesome.htmldom.devtools.HtmlDomDevToolsHitNode;
import dev.takesome.htmldom.devtools.HtmlDomDevToolsSnapshot;
import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomTraversal;
import dev.takesome.htmldom.runtime.HtmlDomAction;
import dev.takesome.htmldom.runtime.HtmlDomDocument;
import dev.takesome.htmldom.runtime.HtmlDomHostBridge;
import dev.takesome.htmldom.runtime.HtmlDomValueBinding;
import dev.takesome.htmldom.desktop.HtmlDomHitTestEngine.ScrollAxis;
import dev.takesome.htmldom.desktop.HtmlDomHitTestEngine.ScrollDrag;
import dev.takesome.htmldom.fonts.HtmlDomFonts;
import dev.takesome.htmldom.icons.fontawesome.FontAwesomeFonts;
import dev.takesome.htmldom.html.UiHtmlDiagnostic;
import dev.takesome.htmldom.html.UiHtmlDiagnosticSeverity;
import dev.takesome.htmldom.logging.HtmlDomLog;
import dev.takesome.htmldom.logging.HtmlDomLogger;
import dev.takesome.htmldom.markup.UiMarkupDocument;
import dev.takesome.htmldom.markup.UiMarkupParser;
import dev.takesome.htmldom.scripting.lua.HtmlDomLuaRuntime;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Stroke;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Composite;
import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Desktop Swing renderer for HtmlDom markup + CSS. No browser and no HTTP server. */
public final class HtmlDomSwingPanel extends JPanel implements HtmlDomInputRouter.Host, HtmlDomSurface {
    private static final HtmlDomLogger LOG = HtmlDomLog.logger(HtmlDomSwingPanel.class);
    private static final String TRACE_LIFECYCLE_PROPERTY = "htmldom.lifecycle.trace";
    private static final String TRACE_FORCED_LAYOUT_PROPERTY = "htmldom.lifecycle.traceForcedLayout";
    private static final long MIN_RUNTIME_EFFECT_INTERVAL_MS = 15L;
    private static final long FORCED_LAYOUT_LOG_INTERVAL_MS = 1_000L;
    private static final long DEVTOOLS_REFRESH_INTERVAL_MS = 125L;
    private final HtmlDomConfig config;
    private final HtmlDomClient client;
    private final UiDomDocument dom;
    private final HtmlDomDocument htmlDocument;
    private final UiStylesheet stylesheet;
    private final UiCssCascade cascade = new UiCssCascade();
    private final UiCssLayoutEngine layoutEngine = new UiCssLayoutEngine(UiCssPropertyRegistry.loadBuiltins(), new HtmlDomJava2DTextMeasurer());
    private final UiCssPaintTreeBuilder paintTreeBuilder = new UiCssPaintTreeBuilder();
    private final HtmlDomScrollController scrollController = new HtmlDomScrollController();
    private final HtmlDomPositioningController positioningController = new HtmlDomPositioningController();
    private final HtmlDomFocusController focusController = new HtmlDomFocusController();
    private final HtmlDomHitTestEngine hitTest = new HtmlDomHitTestEngine();
    private final HtmlDomPaintEngine paintEngine;
    private final HtmlDomTextPaintEngine textPaintEngine = new HtmlDomTextPaintEngine();
    private final HtmlDomOverlayPaintEngine overlayPaintEngine = new HtmlDomOverlayPaintEngine();
    private final HtmlDomScrollbarPaintEngine scrollbarPaintEngine = new HtmlDomScrollbarPaintEngine();
    private final HtmlDomControlPaintEngine controlPaintEngine = new HtmlDomControlPaintEngine();
    private final HtmlDomTransformEngine transformEngine = new HtmlDomTransformEngine();
    private final HtmlDomDirtyRegionEngine dirtyRegionEngine = new HtmlDomDirtyRegionEngine();
    private final HtmlDomTransitionController transitionController = new HtmlDomTransitionController();
    private final UiCssAnimationResolver animationResolver = new UiCssAnimationResolver();
    private final HtmlDomAnimationFrameExecutor animationFrameExecutor;
    private final long animationEpochMs = System.currentTimeMillis();
    private final Timer transitionTimer;
    private final Map<Integer, Set<String>> animationOverlayProperties = new HashMap<>();
    private final Map<Integer, AnimationTarget> animationTargets = new HashMap<>();
    private final Set<UiDomElement> runtimeDirtyElements = new HashSet<>();
    private final Map<Integer, Set<String>> runtimeDirtyPropertiesByNode = new HashMap<>();
    private final Map<Integer, Rectangle> runtimeDirtyBoundsByNode = new HashMap<>();
    private final Object runtimeLock = new Object();
    private final HtmlDomEventDispatcher eventDispatcher = new HtmlDomEventDispatcher();
    private final HtmlDomPseudoStateController pseudoStateController = new HtmlDomPseudoStateController();
    private ScrollDrag activeScrollDrag;
    private UiDomElement hoveredScrollElement;
    private ScrollAxis hoveredScrollAxis;
    private final HtmlDomLuaRuntime lua;
    private final HtmlDomInputRouter inputRouter;
    private HtmlDomDevToolsWindow devToolsWindow;
    private int devToolsHighlightedNodeId;
    private UiCssLayoutResult layout;
    private boolean cascadeDirty = true;
    private boolean layoutDirty = true;
    private long styledDomVersion = -1L;
    private boolean animationsActive;
    private boolean animationTargetsDirty = true;
    private HtmlDomLifecyclePhase lifecyclePhase = HtmlDomLifecyclePhase.IDLE;
    private int lifecycleDepth;
    private long lifecyclePassSequence;
    private long lastRuntimeEffectsMs;
    private long skippedRuntimeEffectTicks;
    private long lastForcedLayoutLogMs;
    private long lastDevToolsRefreshMs;

    public HtmlDomSwingPanel(String markup, String css) {
        this(markup, css, "desktop.ui.html", "", HtmlDomConfig.defaults());
    }

    public HtmlDomSwingPanel(String markup, String css, HtmlDomConfig config) {
        this(markup, css, "desktop.ui.html", "", config);
    }

    public HtmlDomSwingPanel(String markup, String css, String stylesheetBasePath) {
        this(markup, css, "desktop.ui.html", stylesheetBasePath, HtmlDomConfig.defaults());
    }

    public HtmlDomSwingPanel(String markup, String css, String stylesheetBasePath, HtmlDomConfig config) {
        this(markup, css, "desktop.ui.html", stylesheetBasePath, config);
    }

    public HtmlDomSwingPanel(String markup, String css, String sourcePath, String stylesheetBasePath) {
        this(markup, css, sourcePath, stylesheetBasePath, HtmlDomConfig.defaults());
    }

    public HtmlDomSwingPanel(String markup, String css, String sourcePath, String stylesheetBasePath, HtmlDomConfig config) {
        this(markup, css, sourcePath, stylesheetBasePath, config, HtmlDomClient.DEFAULT);
    }

    public HtmlDomSwingPanel(String markup, String css, String sourcePath, String stylesheetBasePath, HtmlDomConfig config, HtmlDomClient client) {
        this.config = config == null ? HtmlDomConfig.defaults() : config;
        this.client = client == null ? HtmlDomClient.DEFAULT : client;
        this.animationFrameExecutor = new HtmlDomAnimationFrameExecutor(this.config.animationWorkerLimit());
        this.transitionTimer = new Timer(this.config.animationFrameIntervalMs(), event -> runAnimationFrame());
        String safeMarkup = markup == null ? "" : markup;
        String safeCss = css == null ? "" : css;
        String resolvedSourcePath = normalizeSourcePath(sourcePath);
        LOG.info(
                "HtmlDom panel bootstrap source='{}' markupChars={} explicitCssChars={} stylesheetBase='{}' devTools={} windowType={} zOrder={}",
                resolvedSourcePath,
                safeMarkup.length(),
                safeCss.length(),
                stylesheetBasePath == null ? "" : stylesheetBasePath,
                this.config.allowDevTools(),
                this.config.devToolsWindowType(),
                this.config.devToolsZOrder()
        );
        UiMarkupDocument document = new UiMarkupParser().parse(safeMarkup, resolvedSourcePath);
        this.dom = document.dom();
        this.htmlDocument = new HtmlDomDocument(this.dom);
        HtmlDomResourceBundle defaultResourceBundle = HtmlDomResourceBundle.forDocument(resolvedSourcePath, stylesheetBasePath);
        HtmlDomResourceBundle resourceBundle = this.client.configureResources(resolvedSourcePath, stylesheetBasePath, defaultResourceBundle);
        this.paintEngine = new HtmlDomPaintEngine(HtmlDomSwingPanel.class.getClassLoader(), resolvedSourcePath, stylesheetBasePath);
        logMarkupDiagnostics(document);
        FontAwesomeFonts.register(HtmlDomFonts.registry());
        this.lua = new HtmlDomLuaRuntime(this.htmlDocument);
        this.stylesheet = new HtmlDomStylesheetLoader(HtmlDomSwingPanel.class.getClassLoader()).load(this.dom, safeCss, resolvedSourcePath, stylesheetBasePath, resourceBundle);
        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        this.inputRouter = new HtmlDomInputRouter(this);
        this.inputRouter.install(this);
        this.transitionTimer.setRepeats(true);
        if (!this.stylesheet.keyframes().isEmpty()) updateTransitionTimer(true);
        LOG.info(
                "HtmlDom panel ready source='{}' elements={} rules={} fontFaces={} keyframes={} animationWorkerLimit={}",
                resolvedSourcePath,
                elementCount(),
                stylesheet.rules().size(),
                stylesheet.fontFaces().size(),
                stylesheet.keyframes().size(),
                animationFrameExecutor.maxWorkerThreads()
        );
    }

    public UiDomDocument document() {
        return dom;
    }

    public HtmlDomDocument htmlDocument() {
        return htmlDocument;
    }

    public HtmlDomSwingPanel bind(String name, HtmlDomValueBinding binding) {
        htmlDocument.bind(name, binding);
        return this;
    }

    public HtmlDomSwingPanel value(String name, Object value) {
        htmlDocument.value(name, value);
        return this;
    }

    public HtmlDomSwingPanel action(String name, HtmlDomAction action) {
        htmlDocument.action(name, action);
        return this;
    }

    public HtmlDomSwingPanel bridge(HtmlDomHostBridge bridge) {
        htmlDocument.bridge(bridge);
        return this;
    }

    public boolean runAction(String actionName) {
        return lua.dispatchAction(actionName);
    }

    public void executeLua(String source, String chunkName) {
        lua.execute(source, chunkName);
    }

    public HtmlDomEventDispatcher.ListenerRegistration addEventListener(String elementId, String type, HtmlDomEventDispatcher.Handler handler) {
        return addEventListener(elementId, type, false, handler);
    }

    public HtmlDomEventDispatcher.ListenerRegistration addEventListener(String elementId, String type, boolean capture, HtmlDomEventDispatcher.Handler handler) {
        UiDomElement element = dom.getElementById(elementId == null ? "" : elementId).orElse(null);
        return addEventListener(element, type, capture, handler);
    }

    public HtmlDomEventDispatcher.ListenerRegistration addEventListener(UiDomElement element, String type, HtmlDomEventDispatcher.Handler handler) {
        return addEventListener(element, type, false, handler);
    }

    public HtmlDomEventDispatcher.ListenerRegistration addEventListener(UiDomElement element, String type, boolean capture, HtmlDomEventDispatcher.Handler handler) {
        return eventDispatcher.addEventListener(element, type, capture, handler);
    }

    public HtmlDomDevToolsSnapshot devToolsSnapshot() {
        updateStyleLayoutAndEffects("devtools", null);
        return HtmlDomDevTools.snapshot(dom, layout, paintTreeBuilder.build(dom, layout), scrollController.resolvedScrollBoxes(layout));
    }

    public List<HtmlDomDevToolsHitNode> devToolsHitTargets() {
        return hitTest.devToolsHitTargets(this::selector);
    }

    public List<HtmlDomEventDispatcher.EventLogEntry> devToolsEventLog() {
        return eventDispatcher.eventLog();
    }

    public void setDevToolsHighlightedNodeId(int nodeId) {
        int next = Math.max(0, nodeId);
        if (devToolsHighlightedNodeId == next) return;
        devToolsHighlightedNodeId = next;
        repaint();
    }

    public void closeDevTools() {
        HtmlDomDevToolsWindow window = devToolsWindow;
        if (window != null) {
            window.close();
            devToolsWindow = null;
        }
        setDevToolsHighlightedNodeId(0);
    }

    @Override public void removeNotify() {
        if (config.devToolsClosePolicy().closeWithHost()) {
            closeDevTools();
        }
        animationFrameExecutor.close();
        super.removeNotify();
    }

    private static String optionalResource(String path) {
        try (java.io.InputStream stream = HtmlDomSwingPanel.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                LOG.debug("HtmlDom optional resource missing path='{}'", path);
                return "";
            }
            byte[] bytes = stream.readAllBytes();
            LOG.debug("HtmlDom optional resource loaded path='{}' bytes={}", path, bytes.length);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException error) {
            LOG.warn("HtmlDom optional resource read failed path='{}' error='{}'", path, error.getMessage());
            return "";
        }
    }

    private static String normalizeSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) return "desktop.ui.html";
        return sourcePath.trim().replace('\\', '/');
    }

    private void logMarkupDiagnostics(UiMarkupDocument document) {
        if (document == null) return;
        if (!document.hasDiagnostics()) {
            LOG.info("HtmlDom markup parsed source='{}' diagnostics=0", document.sourcePath());
            return;
        }
        LOG.warn("HtmlDom markup parsed source='{}' diagnostics={}", document.sourcePath(), document.diagnostics().size());
        for (UiHtmlDiagnostic diagnostic : document.diagnostics()) {
            if (diagnostic.severity() == UiHtmlDiagnosticSeverity.ERROR) {
                LOG.error("HtmlDom markup diagnostic severity='{}' code='{}' at={} message='{}'", diagnostic.severity(), diagnostic.code(), diagnostic.jumpTarget(), diagnostic.message());
            } else {
                LOG.warn("HtmlDom markup diagnostic severity='{}' code='{}' at={} message='{}'", diagnostic.severity(), diagnostic.code(), diagnostic.jumpTarget(), diagnostic.message());
            }
        }
    }

    private int elementCount() {
        if (dom == null || dom.rootOptional().isEmpty()) return 0;
        return UiDomTraversal.depthFirstElements(dom.documentElement()).size();
    }

    private RuntimeUpdateResult updateStyleLayoutAndEffects(String reason, ScrollAnchor anchor) {
        return updateStyleLayoutAndEffects(reason, anchor, true);
    }

    private RuntimeUpdateResult updateStyleLayoutAndEffects(String reason, ScrollAnchor anchor, boolean updateRuntimeEffects) {
        synchronized (runtimeLock) {
            long pass = ++lifecyclePassSequence;
            HtmlDomLifecyclePhase outerPhase = lifecyclePhase;
            if (outerPhase != HtmlDomLifecyclePhase.IDLE) {
                LOG.warn(
                        "HtmlDom lifecycle reentrant pass pass={} reason='{}' currentPhase={} depth={} thread='{}'",
                        pass,
                        reason,
                        outerPhase,
                        lifecycleDepth,
                        Thread.currentThread().getName()
                );
            }

            long started = System.nanoTime();
            long beforeVersion = dom.version();
            boolean hadStableSnapshot = layout != null && !layoutDirty && !cascadeDirty && beforeVersion == styledDomVersion;
            boolean cascadeNeeded = cascadeDirty || layout == null || beforeVersion != styledDomVersion;
            traceForcedLayoutIfNeeded(reason, cascadeNeeded);

            UiCssStyleImpact impact = UiCssStyleImpact.NONE;
            runtimeDirtyElements.clear();
            runtimeDirtyPropertiesByNode.clear();
            try (PhaseScope ignored = enterLifecyclePhase(HtmlDomLifecyclePhase.STYLE_RECALC, reason, pass)) {
                if (cascadeNeeded) {
                    UiCssStyleImpact cascadeImpact = cascade.apply(dom, stylesheet);
                    impact = impact.merge(cascadeImpact);
                    styledDomVersion = dom.version();
                    cascadeDirty = false;
                    animationTargetsDirty = true;
                    if (cascadeImpact.needsLayout()) layoutDirty = true;
                }
            }

            if (updateRuntimeEffects) {
                try (PhaseScope ignored = enterLifecyclePhase(HtmlDomLifecyclePhase.ANIMATION_UPDATE, reason, pass)) {
                    impact = impact.merge(applyRuntimeEffects(cascadeNeeded));
                }
                if (impact.needsLayout()) layoutDirty = true;

                long afterEffectsVersion = dom.version();
                if (afterEffectsVersion != styledDomVersion) {
                    cascadeDirty = true;
                    layoutDirty = true;
                    if (LOG.debugEnabled()) {
                        LOG.debug(
                                "HtmlDom lifecycle dirtied during effects pass={} reason='{}' styledVersion={} currentVersion={}",
                                pass,
                                reason,
                                styledDomVersion,
                                afterEffectsVersion
                        );
                    }
                }
            }

            boolean layoutUpdated = false;
            if (layout == null || layoutDirty) {
                try (PhaseScope ignored = enterLifecyclePhase(HtmlDomLifecyclePhase.LAYOUT, reason, pass)) {
                    layout = layoutEngine.layout(dom, Math.max(1, getWidth()), Math.max(1, getHeight()));
                    scrollController.sync(layout);
                    layoutDirty = false;
                    layoutUpdated = true;
                    if (anchor != null) restoreScrollAnchor(anchor);
                }
            }

            UiCssStyleImpact.RuntimeCategory impactCategory = impact.category();
            if ("animation-frame".equals(reason) && updateRuntimeEffects && impact.layoutAffecting()) {
                LOG.warn(
                        "HtmlDom layout-trigger animation-frame impact={} category={} layoutUpdated={} cascade={} dirtyElements={} thread='{}'",
                        impact,
                        impactCategory,
                        layoutUpdated,
                        cascadeNeeded,
                        runtimeDirtyElements.size(),
                        Thread.currentThread().getName()
                );
            }
            if ("animation-frame".equals(reason) && updateRuntimeEffects && hadStableSnapshot && impact.compositorOnly() && (cascadeNeeded || layoutUpdated)) {
                LOG.warn(
                        "HtmlDom compositor-only animation-frame caused expensive update impact={} category={} layoutUpdated={} cascade={} dirtyElements={}",
                        impact,
                        impactCategory,
                        layoutUpdated,
                        cascadeNeeded,
                        runtimeDirtyElements.size()
                );
            }
            if (LOG.debugEnabled()) {
                LOG.debug(
                        "HtmlDom runtime pass pass={} reason='{}' cascade={} runtimeEffects={} impact={} impactCategory={} layoutUpdated={} dirtyElements={} skippedEffectTicks={} viewport={}x{} elements={} rules={} elapsedMs={}",
                        pass,
                        reason,
                        cascadeNeeded,
                        updateRuntimeEffects,
                        impact,
                        impactCategory,
                        layoutUpdated,
                        runtimeDirtyElements.size(),
                        skippedRuntimeEffectTicks,
                        Math.max(1, getWidth()),
                        Math.max(1, getHeight()),
                        elementCount(),
                        stylesheet.rules().size(),
                        Math.max(0L, (System.nanoTime() - started) / 1_000_000L)
                );
            }
            return new RuntimeUpdateResult(impact, cascadeNeeded, layoutUpdated, updateRuntimeEffects);
        }
    }

    private void ensurePaintSnapshot(String reason, ScrollAnchor anchor) {
        if (!renderSnapshotDirty()) return;
        updateStyleLayoutAndEffects(reason, anchor, false);
    }

    private boolean renderSnapshotDirty() {
        synchronized (runtimeLock) {
            return layout == null || layoutDirty || cascadeDirty || dom.version() != styledDomVersion;
        }
    }

    private void runAnimationFrame() {
        RuntimeUpdateResult result = updateStyleLayoutAndEffects("animation-frame", null, true);
        if (result.needsRepaint()) repaintRuntimeFrame(result);
    }

    private void repaintRuntimeFrame(RuntimeUpdateResult result) {
        if (result == null || !result.needsRepaint()) return;
        if (result.requiresFullRepaint()) {
            repaint();
            return;
        }
        Rectangle dirty = runtimeDirtyBounds();
        if (dirty == null || dirty.width <= 0 || dirty.height <= 0) {
            repaint();
            return;
        }
        repaint(dirty.x, dirty.y, dirty.width, dirty.height);
    }

    private Rectangle runtimeDirtyBounds() {
        Rectangle dirty = null;
        for (UiDomElement element : runtimeDirtyElements) {
            Rectangle current = compositorDirtyRect(element);
            if (current == null) continue;
            Rectangle previous = runtimeDirtyBoundsByNode.put(element.nodeId(), new Rectangle(current));
            dirty = union(dirty, current);
            dirty = union(dirty, previous);
        }
        if (dirty == null) return null;
        return clampToViewport(dirty);
    }

    private Rectangle compositorDirtyRect(UiDomElement element) {
        if (element == null || layout == null) return null;
        Rectangle rect = rect(element);
        if (rect == null || rect.width <= 0 || rect.height <= 0) return null;
        return dirtyRegionEngine.dirtyRect(element, rect, runtimeDirtyPropertiesByNode.get(element.nodeId()), transformEngine);
    }

    private Rectangle clampToViewport(Rectangle rect) {
        if (rect == null) return null;
        int maxWidth = Math.max(1, getWidth());
        int maxHeight = Math.max(1, getHeight());
        int x1 = Math.max(0, rect.x);
        int y1 = Math.max(0, rect.y);
        int x2 = Math.min(maxWidth, rect.x + rect.width);
        int y2 = Math.min(maxHeight, rect.y + rect.height);
        if (x2 <= x1 || y2 <= y1) return null;
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    private Rectangle union(Rectangle a, Rectangle b) {
        if (b == null) return a;
        return a == null ? new Rectangle(b) : a.union(b);
    }

    private void markRuntimeDirty(UiDomElement element) {
        markRuntimeDirty(element, "*");
    }

    private void markRuntimeDirty(UiDomElement element, String property) {
        if (element == null) return;
        runtimeDirtyElements.add(element);
        runtimeDirtyPropertiesByNode
                .computeIfAbsent(element.nodeId(), ignored -> new HashSet<>())
                .add(property == null || property.isBlank() ? "*" : property);
    }


    private UiCssStyleImpact applyRuntimeEffects(boolean cascadeWasUpdated) {
        long now = System.currentTimeMillis();
        boolean runtimeActive = transitionController.active() || animationsActive;
        if (!cascadeWasUpdated && runtimeActive && lastRuntimeEffectsMs > 0L && now - lastRuntimeEffectsMs < MIN_RUNTIME_EFFECT_INTERVAL_MS) {
            skippedRuntimeEffectTicks++;
            updateTransitionTimer(true);
            return UiCssStyleImpact.NONE;
        }
        lastRuntimeEffectsMs = now;
        UiCssStyleImpact impact = UiCssStyleImpact.NONE;
        impact = impact.merge(applyTransitions(now, cascadeWasUpdated));
        impact = impact.merge(applyAnimations(now, cascadeWasUpdated));
        updateTransitionTimer(transitionController.active() || animationsActive);
        return impact;
    }

    private PhaseScope enterLifecyclePhase(HtmlDomLifecyclePhase next, String reason, long pass) {
        HtmlDomLifecyclePhase previous = lifecyclePhase;
        lifecyclePhase = next;
        lifecycleDepth++;
        client.onLifecycleEvent(new HtmlDomLifecycleEvent(pass, next, reason, true, lifecycleDepth, Thread.currentThread().getName()));
        if (Boolean.getBoolean(TRACE_LIFECYCLE_PROPERTY)) {
            LOG.info(
                    "HtmlDom lifecycle enter pass={} phase={} previous={} reason='{}' depth={} thread='{}'",
                    pass,
                    next,
                    previous,
                    reason,
                    lifecycleDepth,
                    Thread.currentThread().getName()
            );
        }
        return new PhaseScope(pass, reason, previous, next);
    }

    private void leaveLifecyclePhase(long pass, String reason, HtmlDomLifecyclePhase previous, HtmlDomLifecyclePhase finished) {
        lifecycleDepth = Math.max(0, lifecycleDepth - 1);
        lifecyclePhase = lifecycleDepth == 0 ? HtmlDomLifecyclePhase.IDLE : previous;
        client.onLifecycleEvent(new HtmlDomLifecycleEvent(pass, finished, reason, false, lifecycleDepth, Thread.currentThread().getName()));
        if (Boolean.getBoolean(TRACE_LIFECYCLE_PROPERTY)) {
            LOG.info(
                    "HtmlDom lifecycle leave pass={} phase={} restored={} reason='{}' depth={} thread='{}'",
                    pass,
                    finished,
                    lifecyclePhase,
                    reason,
                    lifecycleDepth,
                    Thread.currentThread().getName()
            );
        }
    }

    private void traceForcedLayoutIfNeeded(String reason, boolean cascadeNeeded) {
        if (!cascadeNeeded && layout != null && !layoutDirty) return;
        boolean traceForced = Boolean.getBoolean(TRACE_FORCED_LAYOUT_PROPERTY);
        if (!traceForced && !LOG.debugEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - lastForcedLayoutLogMs < FORCED_LAYOUT_LOG_INTERVAL_MS) return;
        lastForcedLayoutLogMs = now;
        String message = "HtmlDom forced layout/update reason='{}' phase={} cascadeDirty={} layoutDirty={} layoutMissing={} styledVersion={} domVersion={} thread='{}'";
        if (traceForced) {
            LOG.warn(
                    message,
                    reason,
                    lifecyclePhase,
                    cascadeDirty,
                    layoutDirty,
                    layout == null,
                    styledDomVersion,
                    dom.version(),
                    Thread.currentThread().getName()
            );
        } else {
            LOG.debug(
                    message,
                    reason,
                    lifecyclePhase,
                    cascadeDirty,
                    layoutDirty,
                    layout == null,
                    styledDomVersion,
                    dom.version(),
                    Thread.currentThread().getName()
            );
        }
    }

    private final class PhaseScope implements AutoCloseable {
        private final long pass;
        private final String reason;
        private final HtmlDomLifecyclePhase previous;
        private final HtmlDomLifecyclePhase current;
        private boolean closed;

        private PhaseScope(long pass, String reason, HtmlDomLifecyclePhase previous, HtmlDomLifecyclePhase current) {
            this.pass = pass;
            this.reason = reason;
            this.previous = previous;
            this.current = current;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            leaveLifecyclePhase(pass, reason, previous, current);
        }
    }


    @Override public HtmlDomConfig config() {
        return config;
    }

    @Override public void repaintHost() {
        updateStyleLayoutAndEffects("repaint-host", null, true);
        repaint();
    }

    @Override public UiCssLayoutResult layoutResult() {
        return layout;
    }

    @Override public void ensureLayout() {
        updateStyleLayoutAndEffects("ensure", null);
    }

    @Override public HtmlDomHitTestEngine hitTest() {
        return hitTest;
    }

    @Override public HtmlDomScrollController scrollController() {
        return scrollController;
    }

    @Override public HtmlDomScrollbarPaintEngine scrollbarPaintEngine() {
        return scrollbarPaintEngine;
    }

    @Override public HtmlDomFocusController focusController() {
        return focusController;
    }

    public void invalidateLayout() {
        synchronized (runtimeLock) {
            cascadeDirty = true;
            layoutDirty = true;
            animationTargetsDirty = true;
            layout = null;
        }
        repaint();
    }


    @Override public void requestPanelFocus() {
        requestFocusInWindow();
    }

    @Override public void dispatchEvent(String type, UiDomElement target) {
        eventDispatcher.dispatch(type, target);
    }

    @Override public void openDevTools() {
        if (!config.devToolsAllowed()) {
            LOG.debug("HtmlDom DevTools open request ignored by config allowDevTools={}", config.allowDevTools());
            return;
        }
        if (devToolsWindow == null) devToolsWindow = new HtmlDomDevToolsWindow(this, config);
        devToolsWindow.open();
    }

    @Override public ScrollDrag activeScrollDrag() {
        return activeScrollDrag;
    }

    @Override public void setActiveScrollDrag(ScrollDrag drag) {
        activeScrollDrag = drag;
    }

    @Override public UiDomElement hoveredScrollElement() {
        return hoveredScrollElement;
    }

    @Override public ScrollAxis hoveredScrollAxis() {
        return hoveredScrollAxis;
    }

    @Override public void setHoveredScrollbar(UiDomElement element, ScrollAxis axis) {
        hoveredScrollElement = element;
        hoveredScrollAxis = axis;
    }

    @Override public boolean setHoveredElement(UiDomElement element) {
        return pseudoStateController.setHoveredElement(dom.documentElement(), element);
    }

    @Override public boolean setActiveElement(UiDomElement element) {
        return pseudoStateController.setActiveElement(dom.documentElement(), element);
    }

    @Override public boolean clearActiveElement() {
        return pseudoStateController.clearActiveElement(dom.documentElement());
    }

    @Override public void activate(UiDomElement element) {
        if (element == null) return;
        eventDispatcher.dispatch("click", element, domEvent -> {
            if (domEvent.defaultPrevented()) return;
            UiDomElement current = domEvent.currentTarget();
            if (current == null) return;
            String action = current.data("action", "");
            String tab = current.data("tab", "");
            boolean actionable = !action.isBlank()
                    || !tab.isBlank()
                    || "button".equals(current.tagName())
                    || "button".equals(current.attribute("role", ""))
                    || current.classList().contains("button");
            if (!actionable) return;
            if (lua.call("onClick", action, current.id())) {
                domEvent.preventDefault();
                domEvent.stopPropagation();
                return;
            }
            if (!action.isBlank() && lua.dispatchAction(action, current.id())) {
                domEvent.preventDefault();
                domEvent.stopPropagation();
                return;
            }
            if ("theme".equals(action)) toggleTheme();
            else if ("pulse".equals(action)) pulse();
            else if ("dialog".equals(action)) setDialog(true);
            else if ("close-dialog".equals(action)) setDialog(false);
            else if (!tab.isBlank()) activateTab(tab);
            lua.call("afterClick", action, current.id());
            domEvent.preventDefault();
            domEvent.stopPropagation();
        });
    }

    public void scrollIntoView(String elementId, String block, String inline) {
        inputRouter.scrollIntoView(elementId, block, inline);
    }

    private ScrollAnchor captureScrollAnchor() {
        if (layout == null || focusController.focusedElement() == null) return null;
        UiDomElement scroller = nearestScrollContainer(focusController.focusedElement());
        if (scroller == null) return null;
        UiCssScrollBox box = scrollBox(scroller);
        if (box == null) return null;
        Rectangle anchorRect = rect(focusController.focusedElement());
        return new ScrollAnchor(scroller, focusController.focusedElement(), anchorRect.x, anchorRect.y, box.scrollX(), box.scrollY());
    }

    private void restoreScrollAnchor(ScrollAnchor anchor) {
        if (anchor == null || anchor.scroller == null || anchor.element == null) return;
        UiCssScrollBox box = scrollBox(anchor.scroller);
        if (box == null) return;
        Rectangle after = rect(anchor.element);
        float nextX = anchor.scrollX + (after.x - anchor.x);
        float nextY = anchor.scrollY + (after.y - anchor.y);
        scrollController.set(anchor.scroller, box, nextX, nextY);
    }

    @Override public UiDomElement nearestScrollContainer(UiDomElement element) {
        return positioningController.nearestScrollContainer(element, this::scrollBox);
    }

    @Override public UiDomElement firstScrollContainer() {
        if (layout == null) return null;
        for (UiDomElement element : UiDomTraversal.depthFirstElements(dom.documentElement())) {
            if (scrollBox(element) != null) return element;
        }
        return null;
    }

    private void toggleTheme() {
        UiDomElement body = dom.body().orElse(dom.renderRoot());
        boolean light = body.classList().contains("theme-light");
        if (light) {
            body.classList().remove("theme-light");
            body.classList().add("theme-dark");
        } else {
            body.classList().remove("theme-dark");
            body.classList().add("theme-light");
        }
    }

    private void activateTab(String name) {
        for (UiDomElement element : dom.getElementsByTagName("button")) {
            if (!element.data("tab", "").isBlank()) {
                setClass(element, "active", name.equals(element.data("tab", "")));
            }
        }
        for (UiDomElement element : dom.getElementsByClassName("tab-panel")) {
            setClass(element, "active", name.equals(element.data("panel", "")));
        }
    }

    private void pulse() {
        UiDomElement body = dom.body().orElse(dom.renderRoot());
        body.classList().add("pulse");
        UiDomElement toast = dom.getElementById("toast").orElse(null);
        if (toast != null) toast.classList().add("open");
        Timer timer = new Timer(config.transientUiResetDelayMs(), event -> {
            body.classList().remove("pulse");
            if (toast != null) toast.classList().remove("open");
            repaintHost();
        });
        timer.setRepeats(false);
        timer.start();
    }

    @Override public void setDialogOpen(boolean open) {
        setDialog(open);
    }

    private void setDialog(boolean open) {
        dom.getElementById("showcase-dialog").ifPresent(dialog -> setClass(dialog, "open", open));
    }

    @Override public boolean dialogOpen() {
        return dom.getElementById("showcase-dialog").map(dialog -> dialog.classList().contains("open")).orElse(false);
    }

    private void setClass(UiDomElement element, String token, boolean enabled) {
        if (enabled && !element.classList().contains(token)) element.classList().add(token);
        if (!enabled && element.classList().contains(token)) element.classList().remove(token);
    }

    private UiCssStyleImpact applyTransitions(long nowMs, boolean cascadeWasUpdated) {
        HtmlDomTransitionController.TickResult result = transitionController.apply(dom, nowMs, cascadeWasUpdated);
        if (!result.changedProperties().isEmpty()) {
            for (HtmlDomTransitionController.ChangedProperty changed : result.changedProperties()) {
                markRuntimeDirty(changed.element(), changed.propertyName());
            }
        } else {
            for (UiDomElement element : result.changedElements()) markRuntimeDirty(element);
        }
        for (HtmlDomTransitionController.TransitionEndEvent event : transitionController.drainFinishedEvents()) {
            eventDispatcher.dispatchTransitionEnd(event.element(), event.propertyName(), event.elapsedMs(), domEvent -> {
                UiDomElement target = domEvent.target();
                UiDomElement current = domEvent.currentTarget();
                lua.call("onTransitionEnd", domEvent.propertyName(), target == null ? "" : target.id(), current == null ? "" : current.id(), Long.toString(domEvent.elapsedMs()));
            });
        }
        return result.impact();
    }

    private UiCssStyleImpact applyAnimations(long nowMs, boolean cascadeWasUpdated) {
        if (stylesheet.keyframes().isEmpty()) {
            animationsActive = false;
            animationTargets.clear();
            return clearAnimationOverlays();
        }
        if (cascadeWasUpdated || animationTargetsDirty) rebuildAnimationTargets();
        if (animationTargets.isEmpty()) {
            animationsActive = false;
            return clearAnimationOverlays();
        }
        long animationNowMs = Math.max(0L, nowMs - animationEpochMs);
        ArrayList<HtmlDomAnimationFrameExecutor.FrameRequest> requests = new ArrayList<>(animationTargets.size());
        for (AnimationTarget target : animationTargets.values()) {
            if (target.element != null) requests.add(new HtmlDomAnimationFrameExecutor.FrameRequest(target.element, target.animations));
        }
        List<HtmlDomAnimationFrameExecutor.FrameResult> frames = animationFrameExecutor.computeFrames(requests, stylesheet.keyframes(), animationNowMs);
        UiCssStyleImpact impact = UiCssStyleImpact.NONE;
        boolean running = false;
        for (HtmlDomAnimationFrameExecutor.FrameResult frameResult : frames) {
            UiDomElement element = frameResult.element();
            Map<String, String> frame = frameResult.frame();
            Set<String> previous = animationOverlayProperties.get(element.nodeId());
            if (previous != null) {
                Iterator<String> iterator = previous.iterator();
                while (iterator.hasNext()) {
                    String property = iterator.next();
                    if (!frame.containsKey(property) && element.removeAnimatedComputedStyle(property)) {
                        impact = impact.merge(UiCssStyleImpact.of(property));
                        markRuntimeDirty(element, property);
                        iterator.remove();
                    }
                }
            }
            if (!frame.isEmpty() && previous == null) {
                previous = new HashSet<>();
                animationOverlayProperties.put(element.nodeId(), previous);
            }
            for (Map.Entry<String, String> entry : frame.entrySet()) {
                String property = entry.getKey();
                if (element.setAnimatedComputedStyle(property, entry.getValue())) {
                    impact = impact.merge(UiCssStyleImpact.of(property));
                    markRuntimeDirty(element, property);
                }
                previous.add(property);
            }
            if (previous != null && previous.isEmpty()) animationOverlayProperties.remove(element.nodeId());
            if (!frame.isEmpty()) running = true;
            AnimationTarget target = animationTargets.get(element.nodeId());
            if (target != null) {
                for (UiCssAnimationDescriptor animation : target.animations) {
                    if (animationStillRunning(animation, animationNowMs)) {
                        running = true;
                        break;
                    }
                }
            }
        }
        animationsActive = running;
        return impact;
    }

    private void rebuildAnimationTargets() {
        animationTargets.clear();
        animationTargetsDirty = false;
        if (dom == null || dom.rootOptional().isEmpty()) return;
        for (UiDomElement element : UiDomTraversal.depthFirstElements(dom.documentElement())) {
            List<UiCssAnimationDescriptor> animations = animationResolver.resolveAll(element.baseComputedStyle());
            if (hasActiveAnimation(animations) || animationOverlayProperties.containsKey(element.nodeId())) {
                animationTargets.put(element.nodeId(), new AnimationTarget(element, animations));
            }
        }
        if (LOG.debugEnabled()) {
            LOG.debug("HtmlDom animation target cache rebuilt targets={} overlays={}", animationTargets.size(), animationOverlayProperties.size());
        }
    }

    private boolean hasActiveAnimation(List<UiCssAnimationDescriptor> animations) {
        if (animations == null || animations.isEmpty()) return false;
        for (UiCssAnimationDescriptor animation : animations) {
            if (animation != null && animation.active()) return true;
        }
        return false;
    }

    private UiCssStyleImpact clearAnimationOverlays() {
        UiCssStyleImpact impact = UiCssStyleImpact.NONE;
        if (animationOverlayProperties.isEmpty()) return impact;
        for (UiDomElement element : UiDomTraversal.depthFirstElements(dom.documentElement())) {
            Set<String> properties = animationOverlayProperties.remove(element.nodeId());
            if (properties == null) continue;
            for (String property : properties) {
                if (element.removeAnimatedComputedStyle(property)) {
                    impact = impact.merge(UiCssStyleImpact.of(property));
                    markRuntimeDirty(element, property);
                }
            }
        }
        return impact;
    }

    private record AnimationTarget(UiDomElement element, List<UiCssAnimationDescriptor> animations) {
        private AnimationTarget {
            animations = animations == null ? List.of() : List.copyOf(animations);
        }
    }

    private boolean animationStillRunning(UiCssAnimationDescriptor animation, long nowMs) {
        if (animation == null || !animation.active()) return false;
        if (animation.infinite()) return true;
        double iterations = Math.max(1.0, animation.iterationCount());
        long endMs = animation.delayMs() + Math.max(1L, Math.round(animation.durationMs() * iterations));
        return nowMs <= endMs;
    }

    private void updateTransitionTimer(boolean running) {
        if (running && !transitionTimer.isRunning()) transitionTimer.start();
        else if (!running && transitionTimer.isRunning()) transitionTimer.stop();
    }

    private void applyRenderHints(Graphics2D g) {
        boolean aa = config.renderQuality().antialiasing();
        boolean textAa = config.renderQuality().textAntialiasing();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAa ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    private void refreshDevToolsIfDue() {
        if (devToolsWindow == null) return;
        long now = System.currentTimeMillis();
        if (now - lastDevToolsRefreshMs < DEVTOOLS_REFRESH_INTERVAL_MS && (transitionController.active() || animationsActive)) return;
        lastDevToolsRefreshMs = now;
        devToolsWindow.refresh();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            applyRenderHints(g);
            ScrollAnchor anchor = captureScrollAnchor();
            ensurePaintSnapshot("paint-snapshot", anchor);
            try (PhaseScope ignored = enterLifecyclePhase(HtmlDomLifecyclePhase.PAINT, "paint", lifecyclePassSequence)) {
                hitTest.clear();
                paintElement(g, dom.documentElement());
                paintFixedLayer(g);
                paintOverlayElements(g);
                paintDevToolsHighlight(g);
                refreshDevToolsIfDue();
            }
        } finally {
            g.dispose();
        }
    }

    private void paintElement(Graphics2D g, UiDomElement element) {
        if (skip(element)) return;
        if (paintEngine.fixed(element) && !paintEngine.paintingFixedLayer()) return;
        if ("dialog".equals(element.tagName()) || element.classList().contains("toast")) return;
        Rectangle rect = rect(element);
        if (rect.width <= 0 || rect.height <= 0) return;
        float opacity = opacity(element);
        if (opacity <= 0f) return;
        Graphics2D layer = (Graphics2D) g.create();
        try {
            transformEngine.apply(layer, element, rect);
            if (opacity < 0.999f) layer.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            paintBackground(layer, element, rect);
            paintBorder(layer, element, rect);
            paintContent(layer, element, rect);
            paintOutline(layer, element, rect);
            paintScrollbars(layer, element, rect);
        } finally {
            layer.dispose();
        }
    }

    private void paintFixedLayer(Graphics2D g) {
        paintEngine.paintFixedLayer(g, dom, this::paintElement);
    }

    private void paintBackground(Graphics2D g, UiDomElement element, Rectangle r) {
        paintEngine.paintBackground(g, element, r);
    }


    private void paintBorder(Graphics2D g, UiDomElement element, Rectangle r) {
        paintEngine.paintBorder(g, element, r);
    }


    private void paintContent(Graphics2D g, UiDomElement element, Rectangle r) {
        Graphics2D content = (Graphics2D) g.create();
        Shape pushedClip = null;
        try {
            if (clickable(element)) hitTest.addHit(g, element, r);
            UiCssScrollBox scroll = layout == null ? null : scrollBox(element);
            if (scroll != null) hitTest.addScrollHit(g, element, r);
            if (clipsContents(element)) {
                Shape clip = clipShape(element, r);
                pushedClip = g.getTransform().createTransformedShape(clip);
                hitTest.pushClip(pushedClip);
                content.clip(clip);
            }
            if (scroll != null && (scroll.scrollX() > 0f || scroll.scrollY() > 0f)) content.translate(-scroll.scrollX(), -scroll.scrollY());
            paintSpecialOrText(content, element, r);
            paintNormalDescendants(content, element);
            paintPositionedDescendants(content, element, r);
        } finally {
            hitTest.popClip(pushedClip);
            content.dispose();
        }
    }

    private void paintNormalDescendants(Graphics2D g, UiDomElement element) {
        for (UiDomElement childElement : paintTreeBuilder.orderedChildren(element)) {
            if (paintEngine.fixed(childElement) && !paintEngine.paintingFixedLayer()) continue;
            if (!positioned(childElement)) paintElement(g, childElement);
        }
    }

    private void paintPositionedDescendants(Graphics2D g, UiDomElement element, Rectangle parentRect) {
        for (UiDomElement childElement : paintTreeBuilder.orderedChildren(element)) {
            if (paintEngine.fixed(childElement) && !paintEngine.paintingFixedLayer()) continue;
            if (positioned(childElement)) paintElement(g, childElement);
        }
    }

    private void paintOutline(Graphics2D g, UiDomElement element, Rectangle r) {
        paintEngine.paintOutline(g, element, r, element == focusController.focusedElement());
    }


    private void paintDevToolsHighlight(Graphics2D g) {
        if (devToolsHighlightedNodeId <= 0 || layout == null) return;
        UiDomElement element = null;
        for (UiDomElement candidate : UiDomTraversal.depthFirstElements(dom.documentElement())) {
            if (candidate.nodeId() == devToolsHighlightedNodeId) {
                element = candidate;
                break;
            }
        }
        if (element == null || skip(element)) return;
        Rectangle borderBox = rect(element);
        if (borderBox.width <= 0 || borderBox.height <= 0) return;

        DevToolsInsets margin = cssInsets(element, "margin", 0);
        DevToolsInsets border = cssInsets(element, "border", fallbackBorderWidth(element));
        DevToolsInsets padding = cssInsets(element, "padding", 0);
        Rectangle marginBox = expand(borderBox, margin);
        Rectangle paddingBox = inset(borderBox, border);
        Rectangle contentBox = inset(paddingBox, padding);
        int radius = Math.max(0, cssPixels(firstStyle(element, "border-radius", "border-top-left-radius"), 18, Math.min(borderBox.width, borderBox.height)));

        Composite oldComposite = g.getComposite();
        Stroke oldStroke = g.getStroke();
        Font oldFont = g.getFont();
        Color oldColor = g.getColor();
        Paint oldPaint = g.getPaint();
        try {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            fillRounded(g, marginBox, radius + 10, new Color(245, 197, 91, 64));
            fillRounded(g, borderBox, radius, new Color(124, 179, 66, 92));
            fillRounded(g, paddingBox, Math.max(0, radius - Math.max(border.top(), border.left())), new Color(74, 222, 128, 58));
            fillRounded(g, contentBox, Math.max(0, radius - Math.max(border.top() + padding.top(), border.left() + padding.left())), new Color(90, 132, 255, 72));

            paintDevToolsHatch(g, marginBox, new Color(117, 91, 22, 54));
            paintDevToolsChildOverlays(g, element, marginBox);

            g.setStroke(new BasicStroke(1.15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g.setColor(new Color(177, 75, 255, 220));
            drawRounded(g, paddingBox, Math.max(0, radius - 2));
            drawRounded(g, contentBox, Math.max(0, radius - 4));

            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(0, 136, 255, 245));
            drawRounded(g, marginBox, radius + 10);

            drawDevToolsLabel(g, element, borderBox, marginBox);
        } finally {
            g.setComposite(oldComposite);
            g.setStroke(oldStroke);
            g.setFont(oldFont);
            g.setColor(oldColor);
            g.setPaint(oldPaint);
        }
    }

    private void paintDevToolsChildOverlays(Graphics2D g, UiDomElement element, Rectangle clip) {
        Stroke oldStroke = g.getStroke();
        Shape oldClip = g.getClip();
        try {
            g.clip(clip);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 3f}, 0f));
            for (UiDomElement child : paintTreeBuilder.orderedChildren(element)) {
                if (skip(child)) continue;
                Rectangle r = rect(child);
                if (r.width <= 0 || r.height <= 0 || !r.intersects(clip)) continue;
                g.setColor(new Color(168, 85, 247, 38));
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(new Color(168, 85, 247, 205));
                g.drawRect(r.x, r.y, Math.max(0, r.width - 1), Math.max(0, r.height - 1));
            }
        } finally {
            g.setClip(oldClip);
            g.setStroke(oldStroke);
        }
    }

    private void paintDevToolsHatch(Graphics2D g, Rectangle r, Color color) {
        if (r.width <= 0 || r.height <= 0) return;
        Shape oldClip = g.getClip();
        Stroke oldStroke = g.getStroke();
        try {
            g.clip(r);
            g.setColor(color);
            g.setStroke(new BasicStroke(1f));
            int step = 8;
            for (int x = r.x - r.height; x < r.x + r.width + r.height; x += step) {
                g.drawLine(x, r.y + r.height, x + r.height, r.y);
            }
        } finally {
            g.setClip(oldClip);
            g.setStroke(oldStroke);
        }
    }

    private void drawDevToolsLabel(Graphics2D g, UiDomElement element, Rectangle borderBox, Rectangle anchorBox) {
        String selectorText = selector(element);
        String dimensionText = borderBox.width + " × " + borderBox.height;
        Font mono = HtmlDomDevToolsFonts.mono(12f);
        Font monoBold = mono.deriveFont(Font.BOLD);
        Font icon = HtmlDomDevToolsFonts.icon(12f);
        g.setFont(monoBold);
        FontMetrics selectorMetrics = g.getFontMetrics();
        int selectorW = selectorMetrics.stringWidth(selectorText);
        g.setFont(mono);
        FontMetrics textMetrics = g.getFontMetrics();
        int dimW = textMetrics.stringWidth(dimensionText);
        int labelW = Math.min(getWidth() - 16, 31 + selectorW + 12 + dimW + 12);
        int labelH = 26;
        int labelX = clamp(anchorBox.x, 8, Math.max(8, getWidth() - labelW - 8));
        int labelY = anchorBox.y - labelH - 7;
        if (labelY < 8) labelY = anchorBox.y + anchorBox.height + 7;
        if (labelY + labelH > getHeight() - 8) labelY = Math.max(8, getHeight() - labelH - 8);

        g.setColor(new Color(0, 0, 0, 55));
        g.fillRoundRect(labelX + 2, labelY + 3, labelW, labelH, 6, 6);
        g.setColor(new Color(255, 255, 255, 248));
        g.fillRoundRect(labelX, labelY, labelW, labelH, 6, 6);
        g.setColor(new Color(218, 220, 224));
        g.drawRoundRect(labelX, labelY, labelW - 1, labelH - 1, 6, 6);
        g.setFont(icon);
        g.setColor(new Color(66, 133, 244));
        g.drawString("\uf121", labelX + 8, labelY + 17);
        g.setFont(monoBold);
        g.setColor(new Color(124, 58, 237));
        g.drawString(selectorText, labelX + 28, labelY + 17);
        g.setFont(mono);
        g.setColor(new Color(31, 41, 55));
        g.drawString(dimensionText, labelX + 28 + selectorW + 12, labelY + 17);
    }

    private void fillRounded(Graphics2D g, Rectangle r, int radius, Color color) {
        if (r.width <= 0 || r.height <= 0) return;
        g.setColor(color);
        if (radius > 0) g.fill(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, radius * 2f, radius * 2f));
        else g.fillRect(r.x, r.y, r.width, r.height);
    }

    private void drawRounded(Graphics2D g, Rectangle r, int radius) {
        if (r.width <= 0 || r.height <= 0) return;
        if (radius > 0) g.draw(new RoundRectangle2D.Float(r.x, r.y, Math.max(0, r.width - 1), Math.max(0, r.height - 1), radius * 2f, radius * 2f));
        else g.drawRect(r.x, r.y, Math.max(0, r.width - 1), Math.max(0, r.height - 1));
    }

    private Rectangle expand(Rectangle r, DevToolsInsets inset) {
        return new Rectangle(r.x - inset.left(), r.y - inset.top(), Math.max(0, r.width + inset.left() + inset.right()), Math.max(0, r.height + inset.top() + inset.bottom()));
    }

    private Rectangle inset(Rectangle r, DevToolsInsets inset) {
        return new Rectangle(r.x + inset.left(), r.y + inset.top(), Math.max(0, r.width - inset.left() - inset.right()), Math.max(0, r.height - inset.top() - inset.bottom()));
    }

    private DevToolsInsets cssInsets(UiDomElement element, String group, int fallback) {
        String shorthand = "border".equals(group) ? firstStyle(element, "border-width", "border") : firstStyle(element, group);
        DevToolsInsets parsed = parseCssBox(shorthand, fallback, Math.max(1, rect(element).width));
        int top = cssPixels(firstStyle(element, group + "-top" + ("border".equals(group) ? "-width" : "")), parsed.top(), Math.max(1, rect(element).height));
        int right = cssPixels(firstStyle(element, group + "-right" + ("border".equals(group) ? "-width" : "")), parsed.right(), Math.max(1, rect(element).width));
        int bottom = cssPixels(firstStyle(element, group + "-bottom" + ("border".equals(group) ? "-width" : "")), parsed.bottom(), Math.max(1, rect(element).height));
        int left = cssPixels(firstStyle(element, group + "-left" + ("border".equals(group) ? "-width" : "")), parsed.left(), Math.max(1, rect(element).width));
        return new DevToolsInsets(top, right, bottom, left);
    }

    private DevToolsInsets parseCssBox(String raw, int fallback, int reference) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) return new DevToolsInsets(fallback, fallback, fallback, fallback);
        String[] parts = value.split("\\s+");
        int a = cssPixels(parts.length > 0 ? parts[0] : "", fallback, reference);
        int b = cssPixels(parts.length > 1 ? parts[1] : "", a, reference);
        int c = cssPixels(parts.length > 2 ? parts[2] : "", a, reference);
        int d = cssPixels(parts.length > 3 ? parts[3] : "", b, reference);
        return new DevToolsInsets(a, b, c, d);
    }

    private int fallbackBorderWidth(UiDomElement element) {
        String border = firstStyle(element, "border", "border-style", "border-color");
        return border.isBlank() || "none".equalsIgnoreCase(border.trim()) ? 0 : 1;
    }

    private String firstStyle(UiDomElement element, String... names) {
        for (String name : names) {
            String value = element.style(name, "").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private int cssPixels(String raw, int fallback, int reference) {
        String value = raw == null ? "" : raw.trim().toLowerCase();
        if (value.isBlank() || "auto".equals(value) || "none".equals(value)) return fallback;
        if ("thin".equals(value)) return 1;
        if ("medium".equals(value)) return 3;
        if ("thick".equals(value)) return 5;
        String first = value.split("\\s+")[0];
        try {
            if (first.endsWith("px")) return Math.round(Float.parseFloat(first.substring(0, first.length() - 2).trim()));
            if (first.endsWith("%")) return Math.round(Float.parseFloat(first.substring(0, first.length() - 1).trim()) * reference / 100f);
            if (first.endsWith("rem") || first.endsWith("em")) return Math.round(Float.parseFloat(first.substring(0, first.length() - 2).trim()) * 16f);
            return Math.round(Float.parseFloat(first));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record DevToolsInsets(int top, int right, int bottom, int left) { }

    private HtmlDomRenderContext renderContext() {
        return new HtmlDomRenderContext(
                dom,
                layout,
                paintEngine,
                textPaintEngine,
                controlPaintEngine,
                overlayPaintEngine,
                scrollbarPaintEngine,
                hitTest,
                focusController.focusedElement(),
                hoveredScrollElement,
                hoveredScrollAxis,
                activeScrollDrag,
                getWidth(),
                getHeight()
        );
    }

    private HtmlDomScrollbarPaintEngine.Context scrollbarContext() {
        return renderContext().scrollbars();
    }

    private void paintScrollbars(Graphics2D g, UiDomElement element, Rectangle r) {
        scrollbarPaintEngine.paintScrollbars(g, element, r, scrollBox(element), scrollbarContext());
    }


    private boolean clipsContents(UiDomElement element) {
        return paintEngine.clipsContents(element);
    }


    private Shape clipShape(UiDomElement element, Rectangle rect) {
        return paintEngine.clipShape(element, rect);
    }


    private HtmlDomOverlayPaintEngine.Context overlayContext() {
        return renderContext().overlays();
    }

    private void paintOverlayElements(Graphics2D g) {
        overlayPaintEngine.paintOverlays(g, overlayContext());
    }


    private HtmlDomControlPaintEngine.Context controlContext() {
        return renderContext().controls();
    }

    private HtmlDomTextPaintEngine.Context textContext() {
        return renderContext().text();
    }

    private void paintSpecialOrText(Graphics2D g, UiDomElement element, Rectangle r) {
        if (controlPaintEngine.paintControl(g, element, r, controlContext())) return;
        textPaintEngine.paintSpecialOrText(g, element, r, textContext());
    }


    @Override public UiCssScrollBox scrollBox(UiDomElement element) {
        if (layout == null || element == null) return null;
        UiCssScrollBox box = layout.scrollBox(element).orElse(null);
        return box == null ? null : scrollController.resolve(element, box);
    }


    private boolean positioned(UiDomElement element) {
        return positioningController.positioned(element);
    }

    @Override public boolean clickable(UiDomElement element) {
        if (element == null) return false;
        return "button".equals(element.tagName())
                || "button".equals(element.attribute("role", ""))
                || element.classList().contains("button")
                || !element.data("action", "").isBlank()
                || !element.data("tab", "").isBlank();
    }

    private boolean skip(UiDomElement element) {
        String tag = element.tagName();
        if (tag.equals("head") || tag.equals("title") || tag.equals("style") || tag.equals("option")) return true;
        return "none".equals(element.style("display", ""));
    }

    @Override public Rectangle rect(UiDomElement element) {
        return positioningController.rect(element, layout, getWidth(), getHeight(), this::scrollBox);
    }

    private String selector(UiDomElement element) {
        String classes = String.join(" ", element.classList().values());
        return element.tagName() + (element.id().isBlank() ? "" : "#" + element.id()) + (classes.isBlank() ? "" : "." + classes.replace(' ', '.'));
    }


    private float opacity(UiDomElement element) {
        String raw = element.style("opacity", "").trim();
        if (raw.isBlank()) return 1f;
        try { return Math.max(0f, Math.min(1f, Float.parseFloat(raw))); }
        catch (RuntimeException ignored) { return 1f; }
    }


    private record RuntimeUpdateResult(UiCssStyleImpact impact, boolean cascadeUpdated, boolean layoutUpdated, boolean runtimeEffectsUpdated) {
        boolean needsRepaint() {
            return layoutUpdated || cascadeUpdated || (runtimeEffectsUpdated && impact != null && impact.needsPaint());
        }

        boolean requiresFullRepaint() {
            return layoutUpdated || cascadeUpdated || (impact != null && impact.needsLayout());
        }
    }

    private record ScrollAnchor(UiDomElement scroller, UiDomElement element, int x, int y, float scrollX, float scrollY) { }

}
