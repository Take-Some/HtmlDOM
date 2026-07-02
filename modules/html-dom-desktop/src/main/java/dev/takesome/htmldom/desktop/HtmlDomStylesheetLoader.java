package dev.takesome.htmldom.desktop;

import dev.takesome.htmldom.css.UiCssParser;
import dev.takesome.htmldom.css.UiStylesheet;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceBundle;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceKind;
import dev.takesome.htmldom.desktop.resource.HtmlDomResourceResolver;
import dev.takesome.htmldom.dom.UiDomDocument;
import dev.takesome.htmldom.dom.UiDomElement;
import dev.takesome.htmldom.dom.UiDomTraversal;
import dev.takesome.htmldom.logging.HtmlDomLog;
import dev.takesome.htmldom.logging.HtmlDomLogger;

import java.util.LinkedHashSet;
import java.util.Locale;

/** Resolves author styles from constructor CSS, <style> blocks and <link rel="stylesheet"> tags. */
final class HtmlDomStylesheetLoader {
    private static final HtmlDomLogger LOG = HtmlDomLog.logger(HtmlDomStylesheetLoader.class);
    private static final int MAX_IMPORT_DEPTH = 32;
    private final UiCssParser parser = new UiCssParser();
    private final ClassLoader classLoader;

    HtmlDomStylesheetLoader(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? HtmlDomStylesheetLoader.class.getClassLoader() : classLoader;
    }

    UiStylesheet load(UiDomDocument document, String explicitCss, String sourcePath, String baseResourcePath) {
        return load(document, explicitCss, sourcePath, baseResourcePath, null);
    }

    UiStylesheet load(UiDomDocument document, String explicitCss, String sourcePath, String baseResourcePath, HtmlDomResourceBundle resourceBundle) {
        UiStylesheet stylesheet = UiStylesheet.empty();
        LoadStats stats = new LoadStats();
        String source = safeSource(sourcePath);
        String base = normalizeBase(baseResourcePath, source);
        HtmlDomResourceBundle bundle = resourceBundle == null ? HtmlDomResourceBundle.forDocument(source, base) : resourceBundle;
        HtmlDomResourceResolver resolver = new HtmlDomResourceResolver(classLoader, bundle);

        if (explicitCss != null && !explicitCss.isBlank()) {
            stylesheet = stylesheet.plus(parseChunk("constructor", source + "#constructor-css", explicitCss, base, resolver));
            stats.explicitBlocks++;
        }

        if (document != null && document.rootOptional().isPresent()) {
            for (UiDomElement element : UiDomTraversal.depthFirstElements(document.documentElement())) {
                if ("style".equals(element.tagName())) {
                    String css = element.textContent();
                    if (css == null || css.isBlank()) {
                        LOG.warn("UI CSS inline <style> is empty source='{}' element='{}'", source, selector(element));
                        stats.skippedInlineBlocks++;
                        continue;
                    }
                    stylesheet = stylesheet.plus(parseChunk("inline-style", source + "#" + selector(element), css, base, resolver));
                    stats.inlineBlocks++;
                    continue;
                }
                if ("link".equals(element.tagName())) {
                    stats.linkNodes++;
                    LinkDecision decision = classifyLink(element);
                    if (!decision.shouldLoad()) {
                        LOG.debug("UI CSS link skipped source='{}' element='{}' reason='{}' rel='{}' href='{}' type='{}' media='{}'",
                                source, selector(element), decision.reason(), element.attribute("rel", ""), element.attribute("href", ""), element.attribute("type", ""), element.attribute("media", ""));
                        stats.skippedLinks++;
                        continue;
                    }
                    String href = element.attribute("href", "");
                    String resolved = resolveHref(href, base);
                    LOG.info("UI CSS link discovered source='{}' href='{}' resolved='{}' media='{}'", source, href, resolved, element.attribute("media", ""));
                    String css = readStylesheet(resolved, resolver);
                    if (css == null) {
                        LOG.warn("UI CSS link failed source='{}' href='{}' resolved='{}'", source, href, resolved);
                        stats.failedLinks++;
                        continue;
                    }
                    stylesheet = stylesheet.plus(parseChunk("link", resolved, css, normalizeBase("", resolved), resolver));
                    stats.loadedLinks++;
                }
            }
        }

        LOG.info("UI CSS resolved source='{}' base='{}' explicitBlocks={} inlineBlocks={} skippedInlineBlocks={} linkNodes={} loadedLinks={} skippedLinks={} failedLinks={} rules={} fontFaces={} keyframes={}",
                source,
                base,
                stats.explicitBlocks,
                stats.inlineBlocks,
                stats.skippedInlineBlocks,
                stats.linkNodes,
                stats.loadedLinks,
                stats.skippedLinks,
                stats.failedLinks,
                stylesheet.rules().size(),
                stylesheet.fontFaces().size(),
                stylesheet.keyframes().size());
        return stylesheet;
    }

    private UiStylesheet parseChunk(String kind, String name, String css, String urlBase, HtmlDomResourceResolver resolver) {
        String safeName = name == null || name.isBlank() ? "<inline>" : name;
        long started = System.nanoTime();
        LinkedHashSet<String> importStack = new LinkedHashSet<>();
        String rootKey = normalizePath(safeName);
        if (!rootKey.isBlank()) importStack.add(rootKey);
        String normalizedCss = expandCssImports(css == null ? "" : css, safeName, urlBase, resolver, importStack, 0);
        try {
            UiStylesheet parsed = parser.parse(normalizedCss);
            LOG.info("UI CSS parsed kind='{}' source='{}' chars={} rules={} fontFaces={} keyframes={} elapsedMs={}",
                    kind,
                    safeName,
                    normalizedCss.length(),
                    parsed.rules().size(),
                    parsed.fontFaces().size(),
                    parsed.keyframes().size(),
                    elapsedMs(started));
            return parsed;
        } catch (RuntimeException error) {
            LOG.error("UI CSS parse failed kind='{}' source='{}' chars={}", error, kind, safeName, normalizedCss.length());
            return UiStylesheet.empty();
        }
    }

    private String expandCssImports(String css, String sourceName, String base, HtmlDomResourceResolver resolver, LinkedHashSet<String> importStack, int depth) {
        String source = sourceName == null || sourceName.isBlank() ? "<inline>" : sourceName;
        String safeCss = css == null ? "" : css;
        if (safeCss.isBlank()) return "";
        if (!safeCss.toLowerCase(Locale.ROOT).contains("@import")) return resolveCssUrls(safeCss, base, resolver);
        if (depth >= MAX_IMPORT_DEPTH) {
            LOG.warn("UI CSS @import max depth reached source='{}' base='{}' depth={}", source, base, depth);
            return resolveCssUrls(safeCss, base, resolver);
        }

        StringBuilder out = new StringBuilder(safeCss.length() + 128);
        int index = 0;
        while (index < safeCss.length()) {
            ImportDirective directive = nextImport(safeCss, index);
            if (directive == null) {
                out.append(resolveCssUrls(safeCss.substring(index), base, resolver));
                break;
            }

            out.append(resolveCssUrls(safeCss.substring(index, directive.start()), base, resolver));
            if (!importMediaAccepted(directive.media())) {
                LOG.debug("UI CSS @import skipped by media source='{}' href='{}' media='{}'", source, directive.href(), directive.media());
                index = directive.end();
                continue;
            }

            String resolved = resolveImportHref(directive.href(), base);
            if (resolved.isBlank()) {
                LOG.warn("UI CSS @import skipped because href is blank source='{}' media='{}'", source, directive.media());
                index = directive.end();
                continue;
            }

            String importKey = normalizePath(resolved);
            if (importStack.contains(importKey)) {
                LOG.warn("UI CSS @import cycle skipped source='{}' href='{}' resolved='{}' stack={}", source, directive.href(), resolved, importStack);
                index = directive.end();
                continue;
            }

            String importedCss = readStylesheet(resolved, resolver);
            if (importedCss == null) {
                LOG.warn("UI CSS @import failed source='{}' href='{}' resolved='{}'", source, directive.href(), resolved);
                index = directive.end();
                continue;
            }

            String importBase = normalizeBase("", resolved);
            LOG.info("UI CSS @import expanded source='{}' href='{}' resolved='{}' base='{}' depth={}", source, directive.href(), resolved, importBase, depth + 1);
            importStack.add(importKey);
            out.append('\n')
                    .append(expandCssImports(importedCss, resolved, importBase, resolver, importStack, depth + 1))
                    .append('\n');
            importStack.remove(importKey);
            index = directive.end();
        }
        return out.toString();
    }

    private ImportDirective nextImport(String css, int fromIndex) {
        int index = Math.max(0, fromIndex);
        char quote = 0;
        boolean comment = false;
        while (css != null && index < css.length()) {
            char ch = css.charAt(index);
            if (comment) {
                if (ch == '*' && index + 1 < css.length() && css.charAt(index + 1) == '/') {
                    comment = false;
                    index += 2;
                } else {
                    index++;
                }
                continue;
            }
            if (quote != 0) {
                if (ch == '\\' && index + 1 < css.length()) {
                    index += 2;
                    continue;
                }
                if (ch == quote) quote = 0;
                index++;
                continue;
            }
            if (ch == '/' && index + 1 < css.length() && css.charAt(index + 1) == '*') {
                comment = true;
                index += 2;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                index++;
                continue;
            }
            if (ch == '@' && startsWithIgnoreCase(css, index, "@import") && importKeywordBoundary(css, index + 7)) {
                ImportDirective directive = parseImportDirective(css, index);
                if (directive != null) return directive;
            }
            index++;
        }
        return null;
    }

    private ImportDirective parseImportDirective(String css, int start) {
        int statementEnd = findImportStatementEnd(css, start + 7);
        if (statementEnd < 0) return null;
        String body = css.substring(start + 7, statementEnd).trim();
        ImportToken token = parseImportToken(body);
        if (token == null || token.href().isBlank()) return null;
        return new ImportDirective(start, statementEnd + 1, token.href(), token.media());
    }

    private ImportToken parseImportToken(String body) {
        if (body == null || body.isBlank()) return null;
        String trimmed = body.trim();
        if (startsWithIgnoreCase(trimmed, 0, "url(")) {
            int open = trimmed.indexOf('(');
            int close = matchingParen(trimmed, open);
            if (close < 0) return null;
            return new ImportToken(unquoteCssToken(trimmed.substring(open + 1, close)), trimmed.substring(close + 1).trim());
        }
        char first = trimmed.charAt(0);
        if (first == '\'' || first == '"') {
            int close = closingQuote(trimmed, 1, first);
            if (close < 0) return null;
            return new ImportToken(trimmed.substring(1, close).trim(), trimmed.substring(close + 1).trim());
        }
        return null;
    }

    private int findImportStatementEnd(String css, int fromIndex) {
        int depth = 0;
        char quote = 0;
        boolean comment = false;
        for (int index = Math.max(0, fromIndex); css != null && index < css.length(); index++) {
            char ch = css.charAt(index);
            if (comment) {
                if (ch == '*' && index + 1 < css.length() && css.charAt(index + 1) == '/') {
                    comment = false;
                    index++;
                }
                continue;
            }
            if (quote != 0) {
                if (ch == '\\' && index + 1 < css.length()) {
                    index++;
                    continue;
                }
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '/' && index + 1 < css.length() && css.charAt(index + 1) == '*') {
                comment = true;
                index++;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') depth++;
            if (ch == ')' && depth > 0) depth--;
            if (ch == ';' && depth == 0) return index;
        }
        return -1;
    }

    private String resolveCssUrls(String css, String base, HtmlDomResourceResolver resolver) {
        if (css == null || css.isBlank() || !css.toLowerCase(Locale.ROOT).contains("url(")) return css == null ? "" : css;
        StringBuilder out = new StringBuilder(css.length() + 32);
        int index = 0;
        while (index < css.length()) {
            int start = indexOfUrl(css, index);
            if (start < 0) {
                out.append(css, index, css.length());
                break;
            }
            int bodyStart = start + 4;
            int bodyEnd = css.indexOf(')', bodyStart);
            if (bodyEnd < 0) {
                out.append(css, index, css.length());
                break;
            }
            out.append(css, index, bodyStart);
            String original = css.substring(bodyStart, bodyEnd);
            out.append(resolveCssUrlToken(original, base, resolver));
            index = bodyEnd;
        }
        return out.toString();
    }

    private int indexOfUrl(String css, int fromIndex) {
        String lower = css.toLowerCase(Locale.ROOT);
        return lower.indexOf("url(", Math.max(0, fromIndex));
    }

    private String resolveCssUrlToken(String token, String base, HtmlDomResourceResolver resolver) {
        String raw = token == null ? "" : token.trim();
        if (raw.isBlank()) return token == null ? "" : token;
        char quote = 0;
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            quote = raw.charAt(0);
            raw = raw.substring(1, raw.length() - 1).trim();
        }
        String resolved = resolveCssAssetUrl(raw, base, resolver);
        if (quote == 0) return resolved;
        return quote + resolved + quote;
    }

    private String resolveCssAssetUrl(String url, String base, HtmlDomResourceResolver resolver) {
        String clean = normalizePath(stripQueryAndFragment(url));
        if (clean.isBlank() || externalUrl(clean) || dataUrl(clean) || absoluteFilePath(clean)) return url;
        if (clean.startsWith("/")) return clean;
        if (base == null || base.isBlank()) return clean;
        String absoluteResource = resolver == null ? absoluteResourceUrl(normalizePath(base + "/" + clean)) : resolver.normalizeForCssUrl(clean, base);
        LOG.debug("UI CSS url resolved base='{}' url='{}' resolved='{}'", base, url, absoluteResource);
        return absoluteResource;
    }

    private String resolveImportHref(String href, String base) {
        String cleanHref = normalizePath(stripQueryAndFragment(href));
        if (cleanHref.isBlank() || unsupportedStylesheetUrl(cleanHref) || fileUrl(cleanHref) || absoluteFilePath(cleanHref)) return cleanHref;
        if (cleanHref.startsWith("/")) return cleanHref;
        if (base == null || base.isBlank()) return cleanHref;
        return normalizePath(base + "/" + cleanHref);
    }

    private boolean importMediaAccepted(String media) {
        String normalized = stripImportOptions(media);
        return normalized.isBlank() || mediaAccepted(normalized);
    }

    private String absoluteResourceUrl(String value) {
        String normalized = normalizePath(value);
        if (normalized.isBlank() || absoluteFilePath(normalized) || normalized.startsWith("/")) return normalized;
        return "/" + stripLeadingSlash(normalized);
    }

    private boolean dataUrl(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith("data:");
    }

    private boolean absoluteFilePath(String value) {
        if (value == null || value.isBlank()) return false;
        String normalized = value.trim().replace('\\', '/');
        return normalized.startsWith("//") || normalized.matches("^[A-Za-z]:/.*");
    }

    private String readStylesheet(String resolved, HtmlDomResourceResolver resolver) {
        if (resolved == null || resolved.isBlank()) return null;
        if (unsupportedStylesheetUrl(resolved)) {
            LOG.warn("UI CSS stylesheet URL is not supported in desktop runtime url='{}'", resolved);
            return null;
        }
        return resolver.resolveText(resolved, HtmlDomResourceKind.CSS).orElse(null);
    }

    private LinkDecision classifyLink(UiDomElement element) {
        if (element == null) return LinkDecision.skip("missing-element");
        if (element.hasAttribute("disabled")) return LinkDecision.skip("disabled");
        String rel = element.attribute("rel", "");
        if (!hasRelToken(rel, "stylesheet")) return LinkDecision.skip("rel-not-stylesheet");
        String href = element.attribute("href", "");
        if (href.isBlank()) return LinkDecision.skip("missing-href");
        String type = element.attribute("type", "").trim().toLowerCase(Locale.ROOT);
        if (!type.isBlank() && !"text/css".equals(type)) return LinkDecision.skip("type-not-css");
        String media = element.attribute("media", "");
        if (!mediaAccepted(media)) return LinkDecision.skip("media-not-screen");
        return LinkDecision.loaded();
    }

    private boolean hasRelToken(String rel, String expected) {
        if (rel == null || expected == null || expected.isBlank()) return false;
        for (String token : rel.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (expected.equals(token.trim())) return true;
        }
        return false;
    }

    private boolean mediaAccepted(String media) {
        if (media == null || media.isBlank()) return true;
        String normalized = media.toLowerCase(Locale.ROOT);
        for (String token : normalized.split(",")) {
            String item = token.trim();
            if (item.isBlank() || "all".equals(item) || "screen".equals(item) || item.startsWith("screen ") || item.contains(" screen")) {
                return true;
            }
        }
        return false;
    }

    private String resolveHref(String href, String base) {
        String cleanHref = normalizePath(stripQueryAndFragment(href));
        if (cleanHref.isBlank() || externalUrl(cleanHref)) return cleanHref;
        if (cleanHref.startsWith("/")) return cleanHref;
        if (base == null || base.isBlank()) return cleanHref;
        return normalizePath(base + "/" + cleanHref);
    }

    private String normalizeBase(String explicitBase, String sourcePath) {
        String base = normalizePath(explicitBase);
        if (!base.isBlank()) return trimTrailingSlash(base);
        String source = normalizePath(sourcePath);
        int slash = source.lastIndexOf('/');
        return slash < 0 ? "" : source.substring(0, slash);
    }

    private String safeSource(String sourcePath) {
        String source = normalizePath(sourcePath);
        return source.isBlank() ? "desktop.ui.html" : source;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "";
        return path.trim().replace('\\', '/').replaceAll("/++", "/");
    }

    private String stripQueryAndFragment(String path) {
        if (path == null) return "";
        int query = path.indexOf('?');
        int fragment = path.indexOf('#');
        int cut = -1;
        if (query >= 0) cut = query;
        if (fragment >= 0) cut = cut < 0 ? fragment : Math.min(cut, fragment);
        return cut < 0 ? path : path.substring(0, cut);
    }

    private String stripLeadingSlash(String path) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        while (value.startsWith("/")) value = value.substring(1);
        return value;
    }

    private String trimTrailingSlash(String path) {
        String value = path == null ? "" : path.trim().replace('\\', '/');
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private boolean externalUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")
                || normalized.startsWith("file:")
                || normalized.startsWith("jar:");
    }

    private boolean unsupportedStylesheetUrl(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")
                || normalized.startsWith("jar:");
    }

    private boolean fileUrl(String value) {
        return value != null && value.trim().toLowerCase(Locale.ROOT).startsWith("file:");
    }

    private String stripImportOptions(String media) {
        String value = media == null ? "" : media.trim();
        while (!value.isBlank()) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("layer(")) {
                int close = matchingParen(value, value.indexOf('('));
                if (close < 0) return value;
                value = value.substring(close + 1).trim();
                continue;
            }
            if ("layer".equals(lower)) return "";
            if (lower.startsWith("supports(")) {
                int close = matchingParen(value, value.indexOf('('));
                if (close < 0) return value;
                value = value.substring(close + 1).trim();
                continue;
            }
            return value;
        }
        return "";
    }

    private boolean startsWithIgnoreCase(String source, int offset, String expected) {
        if (source == null || expected == null || offset < 0 || offset + expected.length() > source.length()) return false;
        return source.regionMatches(true, offset, expected, 0, expected.length());
    }

    private boolean importKeywordBoundary(String source, int index) {
        if (source == null || index >= source.length()) return true;
        char ch = source.charAt(index);
        return !Character.isLetterOrDigit(ch) && ch != '-' && ch != '_';
    }

    private int matchingParen(String source, int open) {
        if (source == null || open < 0 || open >= source.length() || source.charAt(open) != '(') return -1;
        int depth = 0;
        char quote = 0;
        for (int index = open; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (quote != 0) {
                if (ch == '\\' && index + 1 < source.length()) {
                    index++;
                    continue;
                }
                if (ch == quote) quote = 0;
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (ch == '(') depth++;
            if (ch == ')') {
                depth--;
                if (depth == 0) return index;
            }
        }
        return -1;
    }

    private int closingQuote(String source, int fromIndex, char quote) {
        for (int index = Math.max(0, fromIndex); source != null && index < source.length(); index++) {
            char ch = source.charAt(index);
            if (ch == '\\' && index + 1 < source.length()) {
                index++;
                continue;
            }
            if (ch == quote) return index;
        }
        return -1;
    }

    private String unquoteCssToken(String token) {
        String value = token == null ? "" : token.trim();
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private String selector(UiDomElement element) {
        if (element == null) return "";
        StringBuilder out = new StringBuilder(element.tagName());
        if (!element.id().isBlank()) out.append('#').append(element.id());
        for (String className : element.classList().values()) out.append('.').append(className);
        return out.toString();
    }

    private static final class LoadStats {
        private int explicitBlocks;
        private int inlineBlocks;
        private int skippedInlineBlocks;
        private int linkNodes;
        private int loadedLinks;
        private int skippedLinks;
        private int failedLinks;
    }

    private record LinkDecision(boolean shouldLoad, String reason) {
        private static LinkDecision loaded() { return new LinkDecision(true, ""); }
        private static LinkDecision skip(String reason) { return new LinkDecision(false, reason == null ? "skipped" : reason); }
    }

    private record ImportDirective(int start, int end, String href, String media) {}
    private record ImportToken(String href, String media) {}
}
