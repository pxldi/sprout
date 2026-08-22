# Contributing to Sprout

## Sign your commits (DCO)

Sprout uses the [Developer Certificate of Origin](https://developercertificate.org/) rather than
a CLA. Every commit must carry a `Signed-off-by` line matching the author:

```
git commit -s -m "Your message"
```

By signing off you certify that you wrote the change, or have the right to submit it under the
project's license.

## Ground rules that are not negotiable

These come from the research, not from taste. A PR that breaks one of them will be asked to
change, however good the code is.

1. **No guilt.** No copy that blames, shames, or warns about loss. Never "You broke your
   streak", "You failed", or "Don't let Sprout down". See the copy rules in
   [docs/02-app-design.md](docs/02-app-design.md).
2. **Nothing resets to zero** as the primary number a user sees.
3. **Every gamified mechanic ships with an off switch.**
4. **Misses render neutral,** never red.
5. **No analytics, no telemetry, no network calls** outside opt-in sync.

## Code

- `:core:scoring` and `:core:scheduling` are pure Kotlin with no Android dependencies. Changes
  there need tests, and the existing tests double as the specification — read them first.
- Run `./gradlew test detekt` before opening a PR.
- User-visible strings live in `app/src/main/res/values/strings.xml` so the copy rules can be
  reviewed in one place.

## Translations

Translations are handled through Weblate (once set up). Please don't send translation PRs by hand.
