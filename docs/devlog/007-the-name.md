# 007: The name, from Sayso to Tellsy

**Date:** 2026-10-22 to 2026-10-23 · **Stage:** 0 (Foundation), finished properly before packaging

## Why we renamed, twice

**Sayso** was taken in our own field. [Sayso](https://craft.co/sayso-ai) (sayso.ai) is a San Francisco speech-AI company, active since 2020 and reportedly [$40M funded](https://www.trysignalbase.com/news/funding/saysoai-secures-40m-to-revolutionize-generative-speech-ai-and-elevate-user-experiences), and a [SaySo](https://benzatine.com/news-room/sayso-the-trustworthy-short-form-news-app-revolutionizing-media-consumption) news app launched in April 2026. Launching a speech product under the same name invites confusion and a legal letter.

**Sayvoy** (say + envoy) came next and looked clean in app stores and company databases. A domain check then found that [sayvoy.com](https://sayvoy.com) had been registered in August 2025 and hosts a "coming soon" page for **Sayvoy, an AI travel concierge**. Another conversational AI product under the same name is too close, so we moved on before any installer shipped.

**Tellsy** is the final name: short, easy to say and spell in any accent, and clearly English.

## How we chose

About 60 names, screened four ways: existing apps and companies, trademarks, .com, and .ai/.app domains.

- **Real English words in voice tech are almost all taken.** Overtone ($18M voice-AI startup), Outloud, Aloudly, Saywise, Backtalk, Talkloop, Sayback, Hearback, Wordloop, Echowise and Sayable each have a product or a registered .ai.
- **Short invented names have their .com taken**, usually by domain investors. That's normal; most new startups launch on .ai or .app plus a "get" .com.
- **Close isn't safe.** Echowise sits next to Echowin (voice AI), Hearsy next to Hearsay (a big software brand), Murmo next to the Murmur voice apps.
- **Tellsy** had no product or company using it, with **tellsy.ai** and **tellsy.app** both free as of 2026-10-23. tellsy.com has been registered since 2012 (use unknown), so the fallback is gettellsy.com.

## What changed in the code

| Before | After |
| --- | --- |
| Package `io.github.lokiencoded.sayvoy` | `io.github.lokiencoded.tellsy` |
| `SayvoyApp`, `SayvoyPaths` | `TellsyApp`, `TellsyPaths` |
| Maven artifact `sayvoy`, `target/sayvoy.jar` | `tellsy`, `target/tellsy.jar` |
| `%USERPROFILE%\.sayvoy` (or `.sayso`) | `%USERPROFILE%\.tellsy`, moved automatically on first run by the app and the install script; the newer `.sayvoy` wins if both exist |
| `SAYVOY_HOME` | `TELLSY_HOME` |

## Still to do by hand

- [ ] Register **tellsy.ai** (and tellsy.app or gettellsy.com) on a registrar before someone else does
- [ ] Check @tellsy on X, Instagram and GitHub
- [ ] Search the Indian ([ipindia.gov.in](https://ipindia.gov.in)) and US ([tmsearch.uspto.gov](https://tmsearch.uspto.gov)) trademark registries in classes 9 and 42; before a public launch, consider a formal search by a trademark attorney
- [ ] Rename the GitHub repo to Tellsy (Settings > General > Repository name); GitHub redirects the old URLs

## Concepts to study from this step

- **Trademark classes:** class 9 covers downloadable software and class 42 software as a service. Conflicts are judged within related classes, which is why a speech-AI Sayso mattered and an unrelated "SAYSO" binder clip didn't.
- **Likelihood of confusion:** similar sound plus related products, even without an exact match (Hearsy versus Hearsay).
- **Check domains early:** Sayvoy passed every search but failed on its .com, which turned out to be an upcoming product. Domains are the cheapest, fastest signal of who else wants a name.
- **Migrations that stack:** `TellsyPaths.resolve` moves data from whichever earlier folder exists, newest first, so users of any past version keep their settings.
