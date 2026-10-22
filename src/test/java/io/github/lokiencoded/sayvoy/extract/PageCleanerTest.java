package io.github.lokiencoded.sayvoy.extract;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageCleanerTest {

    private static final String PROSE =
            "Free content is any kind of creative work that people can use, study, share and improve. ";

    /** Shaped like a real Wikipedia article: menus, site tagline, edit links, infobox, citations. */
    private static final String WIKIPEDIA = """
            <html><body>
              <a class="mw-jump-link" href="#content">Jump to content</a>
              <header class="vector-header"><a class="mw-logo">Wikipedia The Free Encyclopedia</a>
                <div class="vector-search-box"><input placeholder="Search Wikipedia"></div></header>
              <nav id="mw-panel"><a>Main page</a><a>Contents</a><a>Donate</a></nav>
              <div id="content">
                <h1 id="firstHeading">Free content</h1>
                <div id="bodyContent">
                  <div id="siteSub">From Wikipedia, the free encyclopedia</div>
                  <div id="mw-content-text"><div class="mw-parser-output">
                    <div class="hatnote">Not to be confused with Free software.</div>
                    <table class="infobox"><tr><td>Type</td><td>Licence</td></tr></table>
                    <p>%s<sup class="reference">[5]</sup></p>
                    <h2>History<span class="mw-editsection">[edit]</span></h2>
                    <p>%s</p>
                    <div class="navbox">Copyright | Licences | Open access</div>
                  </div></div>
                </div>
              </div>
              <footer id="footer">This page was last edited on 1 October 2026. Privacy policy</footer>
            </body></html>
            """.formatted(PROSE.repeat(3), PROSE.repeat(2));

    @Test
    void wikipediaKeepsTitleAndArticle() {
        String text = PageCleaner.fromHtml(WIKIPEDIA);
        assertTrue(text.startsWith("Free content.\n\nFree content is any kind"), text);
        assertTrue(text.contains("History."), text);
    }

    @Test
    void wikipediaDropsPageFurniture() {
        String text = PageCleaner.fromHtml(WIKIPEDIA);
        for (String junk : new String[]{"Jump to content", "The Free Encyclopedia", "Search", "Main page",
                "From Wikipedia", "Not to be confused", "Licence", "[5]", "[edit]", "Open access",
                "last edited", "Privacy"}) {
            assertFalse(text.contains(junk), "should not read \"" + junk + "\" in:\n" + text);
        }
    }

    /** Shaped like a news site: site header, menus, share buttons, sidebar, newsletter box, footer. */
    @Test
    void newsSiteKeepsOnlyTheStory() {
        String html = """
                <body>
                  <header class="site-header"><div class="logo">Daily Planet</div><p>All the news</p></header>
                  <div class="top-menu"><a>World</a><a>Business</a><a>Sport</a></div>
                  <main>
                    <article>
                      <header><h1>City opens new library</h1><p class="byline">By Lois Lane</p></header>
                      <div class="share-buttons"><button>Share</button><a>Tweet</a></div>
                      <p>%s</p>
                      <div class="newsletter-signup"><p>Get our daily newsletter!</p></div>
                      <p>%s</p>
                    </article>
                    <aside><h3>Most read</h3><a>Another story</a></aside>
                  </main>
                  <div id="cookie-consent">We use cookies. Accept?</div>
                  <footer>Copyright Daily Planet</footer>
                </body>
                """.formatted(PROSE.repeat(3), PROSE.repeat(2));

        String text = PageCleaner.fromHtml(html);

        assertTrue(text.startsWith("City opens new library."), text);
        assertTrue(text.contains("By Lois Lane"), text);
        for (String junk : new String[]{"Daily Planet", "All the news", "World", "Share", "Tweet",
                "newsletter", "Most read", "cookies"}) {
            assertFalse(text.contains(junk), "should not read \"" + junk + "\" in:\n" + text);
        }
    }

    /** Old-style pages with only divs: the block with the most paragraph text wins. */
    @Test
    void findsTheArticleWithoutSemanticTags() {
        String html = """
                <body>
                  <div class="links"><p>Home</p><p>About</p><p>Contact</p></div>
                  <div class="wrapper"><div class="text">
                    <p>%s</p><p>%s</p>
                  </div></div>
                  <div class="bottom"><p>Made with love</p></div>
                </body>
                """.formatted(PROSE.repeat(2), PROSE.repeat(2));

        String text = PageCleaner.fromHtml(html);

        assertTrue(text.startsWith("Free content is any kind"), text);
        assertFalse(text.contains("Contact"), text);
        assertFalse(text.contains("Made with love"), text);
    }

    @Test
    void emptyOrMissingHtmlGivesEmptyText() {
        assertEquals("", PageCleaner.fromHtml(null));
        assertEquals("", PageCleaner.fromHtml("  "));
    }

    @Test
    void tidyRemovesBracketNotesAndExtraSpace() {
        assertEquals("Water boils at 100 C. It freezes at 0 C.",
                PageCleaner.tidy("Water boils at 100 C.[1]  It freezes at 0 C.[a][citation needed]"));
    }

    @Test
    void tidyKeepsParagraphBreaks() {
        assertEquals("One.\n\nTwo.", PageCleaner.tidy("  One.  \n\n\n  Two. "));
    }
}
