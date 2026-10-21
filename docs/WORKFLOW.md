# How we work

## Branches

- `main` always builds, passes tests and runs. Nobody commits half-finished work to it.
- Every change happens on a short-lived branch named for what it does:

| Prefix | For | Example |
| --- | --- | --- |
| `feat/` | A new feature | `feat/saved-settings` |
| `fix/` | A bug fix | `fix/stuck-alt-key` |
| `docs/` | Documentation only | `docs/devlog-004` |
| `chore/` | Build, tooling, cleanup | `chore/upgrade-jsoup` |

## Commits

Messages follow [Conventional Commits](https://www.conventionalcommits.org/): a type, a colon, then what the commit does in the imperative.

```
feat: read just the article when reading a whole page
fix: keep shortcuts responsive while reading long pages
docs: devlog 003 on reading just the article
```

The body (after a blank line) explains **why**, especially for fixes.

## The flow for every change

1. Start from an up-to-date `main`:
   ```
   git checkout main
   git pull
   git checkout -b feat/my-change
   ```
2. Commit in small steps. Each commit should build.
3. Push and open a pull request:
   ```
   git push -u origin feat/my-change
   ```
4. **CI** (GitHub Actions, `.github/workflows/build.yml`) builds and tests the PR on Windows. A red X means it must be fixed before merging.
5. Try it on your own machine, then merge on GitHub and click **Delete branch**.
6. Back on your laptop, clean up:
   ```
   git checkout main
   git pull
   git fetch --prune
   git branch -d feat/my-change
   ```

## Each feature also gets

- Tests in `src/test/java`, mirroring the package being changed.
- A devlog entry in `docs/devlog/` explaining the decisions and what to study.
