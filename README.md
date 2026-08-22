# Sprout

An open-source, local-first Android habit tracker with evidence-based gamification.

Sprout is built on one idea: **tracking is the active ingredient, not the reward.** Everything
in the app is traceable to published evidence — the research digest lives in
[docs/01-research.md](docs/01-research.md), and the design decisions it produced are in
[docs/02-app-design.md](docs/02-app-design.md).

## What makes it different

- **Nothing ever resets to zero.** The headline number is a strength score (exponentially
  smoothed). A miss dents it by a few points; it never wipes.
- **Streaks with slack.** Rest days are *earned* by showing up, never bought, and spent silently.
  Out of slack? The run pauses instead of breaking, and showing up within 48 h earns it back.
- **Kind after a miss.** No guilt notifications, ever. The day after a miss is the most rewarded
  day in the app — the single best intervention of 53 tested in the StepUp megastudy.
- **Praise, not prizes.** Feedback is specific and drawn from your own data.
- **Everything gamified has an off switch.**
- **Local-first.** No account, no analytics, no network by default. Your data is a file you own.

## Status

Pre-alpha, Phase 0. The scoring engine is implemented and tested; the UI is a placeholder.
See [docs/03-roadmap.md](docs/03-roadmap.md).

## Building

Requires JDK 17 and an Android SDK with API 37 platform + build-tools.

```
./gradlew :core:scoring:test        # the scoring engine
./gradlew :app:assembleFossDebug    # the FOSS-flavour debug APK
```

Point Gradle at your SDK by creating `local.properties`:

```
sdk.dir=/path/to/android-sdk
```

## Flavours

| Flavour | Purpose |
|---|---|
| `foss` | Zero Google dependencies. What F-Droid builds. |
| `play` | Play Store build. |

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
