# 003: Reading just the article

**Date:** 2026-10-22 · **Stage:** 2 (MVP: Read + Ask)

## The problem

Ctrl+Alt+P read everything Ctrl+A selected: the site banner, menus, search box, sidebars, cookie notices and footer, before and after the actual article.

## What we built

`PageCleaner` works like a browser's Reader Mode. When you copy from a browser, the clipboard holds two versions: plain text, and HTML that still shows the page's structure. Sayvoy now reads the HTML version too, and:

1. **Removes page furniture:** `nav`, `aside`, `footer`, forms and buttons, ARIA roles like `navigation` and `banner`, elements whose class or id says `menu`, `sidebar`, `cookie`, `share`, `newsletter` and so on, plus Wikipedia's edit links, infoboxes and citation markers.
2. **Finds the article:** first by tags sites use for it (`article`, `main`, `#mw-content-text`), then, for old pages built only from `div`s, by picking the block with the most paragraph text (a simplified version of Mozilla's Readability algorithm).
3. **Keeps the title** (the page's `h1`) and adds a full stop after headings so the voice pauses.

Apps that only copy plain text, like Notepad, still work: they get a light tidy-up (citation markers removed) instead.

## Decisions and why

| Decision | Why | Alternative we skipped |
| --- | --- | --- |
| Use the clipboard's HTML | Already there, no new permissions, works in every browser | Windows UI Automation: reads the screen directly, but much more code and browsers expose it inconsistently |
| jsoup, our own rules | Small, no dependencies, easy to tune and test | Readability4J: closer to Firefox Reader Mode, but pulls in Kotlin and is harder to adjust |
| Keep the article title | You usually want to hear what you are about to listen to | Skipping it: easy to switch if you prefer |
| Drop data tables | Rows of cells read aloud are hard to follow | Reading them cell by cell |

## Concepts to study from this step

- **The DOM:** a web page is a tree of elements. Removing junk is pruning branches; finding the article is choosing the right subtree.
- **CSS selectors:** `nav`, `.infobox`, `#toc` and `[role=navigation]` select elements by tag, class, id and attribute. jsoup's `select()` takes the same selectors as a browser.
- **Heuristics:** rules that are usually right, not always. The tests in `PageCleanerTest` pin down what "right" means for Wikipedia, a news site and an old div-only page.
- **Tree traversal:** `NodeTraversor` visits every node, calling `head` on the way in and `tail` on the way out. That's where paragraph breaks and heading pauses are added.

## Exercises for you

1. Try Ctrl+Alt+P on five sites you read (a news site, a blog, Wikipedia, Medium, a docs page). Note any site where it reads junk or misses content.
2. For one of those sites, right-click the junk in Chrome, choose **Inspect**, and find its class or id. Add a test to `PageCleanerTest` with a small copy of that HTML, then make it pass.
