package io.github.lokiencoded.tellsy.extract;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a copied web page into just the text worth listening to, like a browser's
 * Reader Mode: the article heading and body, without menus, banners, sidebars,
 * footers, buttons or citation markers.
 *
 * <p>Browsers put both plain text and HTML on the clipboard when you copy. The plain
 * text has lost all structure, but the HTML still says what is a menu (nav), a footer,
 * a sidebar (aside) or the main article (article, main), so we work from the HTML.
 */
public final class PageCleaner {

    /** Never content, whatever the page. */
    private static final String ALWAYS_JUNK = String.join(",",
            "script", "style", "noscript", "template", "svg", "canvas", "iframe", "object",
            "form", "button", "input", "select", "textarea", "label",
            "nav", "aside", "footer", "dialog", "figure",
            "[hidden]", "[aria-hidden=true]",
            "[role=navigation]", "[role=banner]", "[role=contentinfo]", "[role=complementary]",
            "[role=search]", "[role=dialog]", "[role=menu]", "[role=menubar]", "[role=toolbar]",
            // Wikipedia and other wikis
            "sup.reference", ".mw-editsection", ".navbox", ".infobox", ".reflist", ".references",
            ".toc", "#toc", ".hatnote", ".metadata", ".noprint", ".mw-jump-link", "#siteSub",
            "#contentSub", ".shortdescription", ".sistersitebox", ".catlinks");

    /** class or id words that mark page furniture rather than content. */
    private static final Pattern JUNK_NAME = Pattern.compile(
            "(?i)(^|[-_\\s])(nav|navbar|navigation|menu|sidebar|footer|breadcrumbs?|comments?"
                    + "|share|sharing|social|cookies?|consent|banner|advert|advertisement|ads?|sponsored"
                    + "|promo|related|recommended|subscribe|newsletter|popup|modal|masthead|skip"
                    + "|toolbar|signup|login|paywall)([-_\\s]|$)");

    /** Where sites usually keep the article, best guesses first. */
    private static final List<String> CONTENT_ROOTS = List.of(
            "article", "main", "[role=main]", "#mw-content-text", "[itemprop=articleBody]",
            ".post-content", ".entry-content", ".article-body", ".article-content", "#content");

    private static final int MIN_ARTICLE_CHARS = 200;
    private static final int MIN_PARAGRAPH_CHARS = 25;

    private static final Set<String> BLOCKS = Set.of(
            "p", "div", "section", "article", "main", "header", "li", "ul", "ol", "dl", "dt", "dd",
            "blockquote", "pre", "br", "hr", "tr", "table", "figcaption", "address", "details", "summary",
            "h1", "h2", "h3", "h4", "h5", "h6");

    private static final Set<String> HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");

    /** [5], [a], [citation needed], [edit] and friends: noise when read aloud. */
    private static final Pattern BRACKET_NOTES = Pattern.compile(
            "(?i)\\[(\\d+|[a-z]|edit|citation needed|clarification needed|note \\d+|update)]");

    private PageCleaner() {
    }

    /** The readable text of a copied page, or an empty string if nothing readable was found. */
    public static String fromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        removeJunk(doc);

        Element title = doc.selectFirst("h1");
        Element root = findArticle(doc);

        StringBuilder out = new StringBuilder();
        if (title != null && !isInside(title, root)) {
            appendText(title, out);
        }
        appendText(root, out);
        return tidy(out.toString());
    }

    /** Light cleanup for plain text: drops citation markers and tidies spacing. */
    public static String tidy(String text) {
        if (text == null) {
            return "";
        }
        String withoutNotes = BRACKET_NOTES.matcher(text).replaceAll("");
        StringBuilder out = new StringBuilder();
        for (String paragraph : withoutNotes.split("\\n\\s*\\n")) {
            String clean = paragraph.replaceAll("[ \\t\\u00A0]+", " ")
                    .replaceAll(" *\\n *", "\n")
                    .replaceAll(" ([.,;:!?])", "$1")
                    .strip();
            if (!clean.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append("\n\n");
                }
                out.append(clean);
            }
        }
        return out.toString();
    }

    private static void removeJunk(Document doc) {
        doc.select(ALWAYS_JUNK).remove();
        // A <header> is junk when it is the site header, but keep an article header with the title.
        for (Element header : doc.select("header")) {
            if (header.selectFirst("h1") == null) {
                header.remove();
            }
        }
        // Data tables read badly aloud; keep only layout tables that hold paragraphs.
        for (Element table : doc.select("table")) {
            if (table.root() == doc && table.selectFirst("p") == null) {
                table.remove();
            }
        }
        for (Element element : doc.body().select("*")) {
            if (element != doc.body() && element.root() == doc && looksLikeFurniture(element)) {
                element.remove();
            }
        }
    }

    private static boolean looksLikeFurniture(Element element) {
        String names = element.className() + " " + element.id();
        if (names.isBlank() || !JUNK_NAME.matcher(names).find()) {
            return false;
        }
        // Never throw away the main heading or a big block of prose by mistake.
        return element.selectFirst("h1") == null && paragraphChars(element) < MIN_ARTICLE_CHARS * 2;
    }

    private static Element findArticle(Document doc) {
        for (String selector : CONTENT_ROOTS) {
            Element best = null;
            for (Element candidate : doc.select(selector)) {
                if (best == null || candidate.text().length() > best.text().length()) {
                    best = candidate;
                }
            }
            if (best != null && best.text().length() >= MIN_ARTICLE_CHARS) {
                return best;
            }
        }
        Element densest = densestBlock(doc);
        return densest != null ? densest : doc.body();
    }

    /**
     * No semantic tags: score each element by the paragraph text directly inside it
     * (and half for grandchildren), and take the winner. A simplified Readability.
     */
    private static Element densestBlock(Document doc) {
        Map<Element, Integer> scores = new HashMap<>();
        for (Element paragraph : doc.select("p")) {
            int length = paragraph.text().length();
            if (length < MIN_PARAGRAPH_CHARS) {
                continue;
            }
            Element parent = paragraph.parent();
            if (parent != null) {
                scores.merge(parent, length, Integer::sum);
                Element grandparent = parent.parent();
                if (grandparent != null) {
                    scores.merge(grandparent, length / 2, Integer::sum);
                }
            }
        }
        return scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static int paragraphChars(Element element) {
        int total = 0;
        for (Element paragraph : element.select("p")) {
            total += paragraph.text().length();
        }
        return total;
    }

    private static boolean isInside(Element element, Element container) {
        for (Element e = element; e != null; e = e.parent()) {
            if (e == container) {
                return true;
            }
        }
        return false;
    }

    /** Writes an element's text, with a paragraph break around each block and a pause after headings. */
    private static void appendText(Element root, StringBuilder out) {
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode text) {
                    out.append(text.text());
                } else if (node instanceof Element element && BLOCKS.contains(element.normalName())) {
                    out.append("\n\n");
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element element && BLOCKS.contains(element.normalName())) {
                    if (HEADINGS.contains(element.normalName())) {
                        endWithFullStop(out);
                    }
                    out.append("\n\n");
                }
            }
        }, root);
    }

    /** Headings have no full stop, so the voice would run them into the next sentence. */
    private static void endWithFullStop(StringBuilder out) {
        int end = out.length();
        while (end > 0 && Character.isWhitespace(out.charAt(end - 1))) {
            end--;
        }
        if (end > 0 && Character.isLetterOrDigit(out.charAt(end - 1))) {
            out.insert(end, '.');
        }
    }
}
