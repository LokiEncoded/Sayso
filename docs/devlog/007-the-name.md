# 007: The name, Sayso becomes Sayvoy

**Date:** 2026-10-22 · **Stage:** 0 (Foundation), finished properly before packaging

## Why we renamed

"Sayso" was taken in our own field. [Sayso](https://craft.co/sayso-ai) (sayso.ai) is a San Francisco speech-AI company, active since 2020, and a [SaySo](https://wwwhatsnew.com/2026/04/20/sayso-app-noticias-video-corto-creadores-verificados-confianza/) news app launched in April 2026. Launching a speech product under the same name invites confusion and a legal letter.

## How we chose

More than 40 names, screened by searching for existing apps, companies and trademarks:

- **Real words in voice tech are almost all taken.** Cyrano, Sotto, Oto, Timbre, Tacit, Voca, Hark, Utter and Parley are all voice-AI products already.
- **Close isn't safe.** Ventri had no exact match, but sits next to Ventrilo (voice chat since 2002) and Ventrilo.ai (an AI writing tool). We decided to play it totally safe.
- **Coined names are ownable.** Sayvoy, say + envoy (someone who speaks on your behalf), had zero exact matches anywhere.

The full screening is in the project plan doc.

## What changed in the code

| Before | After |
| --- | --- |
| Package `io.github.lokiencoded.sayso` | `io.github.lokiencoded.sayvoy` |
| `SaysoApp`, `SaysoPaths` | `SayvoyApp`, `SayvoyPaths` |
| Maven artifact `sayso`, `target/sayso.jar` | `sayvoy`, `target/sayvoy.jar` |
| `%USERPROFILE%\.sayso` | `%USERPROFILE%\.sayvoy`, moved automatically on first run (by the app and by the install script), so settings, the API key and voices carry over |
| `SAYSO_HOME` | `SAYVOY_HOME` |

## Still to do by hand

- [ ] Search sayvoy.com, sayvoy.ai and sayvoy.app on a registrar and register one
- [ ] Check @sayvoy on X, Instagram and GitHub
- [ ] Search the Indian ([ipindia.gov.in](https://ipindia.gov.in)) and US ([tmsearch.uspto.gov](https://tmsearch.uspto.gov)) trademark registries in classes 9 and 42; before a public launch, consider a formal search by a trademark attorney
- [ ] Rename the GitHub repo to Sayvoy (Settings > General > Repository name); GitHub redirects the old URLs

## Concepts to study from this step

- **Trademark classes:** class 9 covers downloadable software and class 42 software as a service. Conflicts are judged within related classes, which is why a foster-care "SAYSO" didn't matter but a speech-AI Sayso did.
- **Likelihood of confusion:** similar sound plus related products, even without an exact match (Ventri versus Ventrilo).
- **Migrations:** when you change where data lives, move users' existing data instead of starting them over (`SayvoyPaths.resolve`).
