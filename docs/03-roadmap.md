# Roadmap

*Solo developer, evenings and weekends assumed (~8–10 h/week). Phases are ordered so that each release is useful on its own and F-Droid-ready. Durations are estimates; ship when done, not when scheduled.*

## Phase 0 — Foundation (2–3 weeks)

- Repo, GPL-3.0-or-later, README, CONTRIBUTING with DCO, issue templates, `fastlane/metadata/android/en-US/` skeleton.
- Gradle version catalog, pinned JDK 17, AGP/Kotlin pinned; `foss`/`play` flavors; `dependenciesInfo.includeInApk=false`, `vcsInfo.include=false` (reproducible-build hygiene from commit one); literal `versionCode`.
- Module skeleton; Hilt; Room with exported schemas; DataStore; Navigation; M3 theme with dynamic color + brand fallback + AMOLED.
- `:core:scoring` and `:core:scheduling` as pure-Kotlin modules with tests first (strength, schedule-aware streak, rest-day ledger).
- GitHub Actions: lint, detekt, unit tests, Robolectric Compose tests, release APK on tag.
- Generate and back up the signing key (encrypted, off-device). Decide now whether to register with Google's developer verification so the same key can serve Play/sideload later.

**Exit:** green CI, empty app with theme, scoring library at 100% coverage.

## Phase 1 — MVP: a better Loop (6–8 weeks) → v0.1 on F-Droid

- Habit CRUD for DO (boolean/numeric) and AVOID types; creation flow with cue, smallest version, coping plan, schedule.
- Today screen with one-tap complete, skip, note; habit detail with strength curve + heatmap + runs + 30-day %.
- Strength score, streaks with rest days and 48 h repair, Layer-0 "shine" moment, day-7/day-66 milestone cards.
- Reminders: exact alarms with inexact fallback and permission UX; notification actions Done/Skip/Snooze; boot/timezone rescheduling; `POST_NOTIFICATIONS` in-context request.
- Glance widget: today checklist.
- JSON backup/restore via SAF, daily auto-backup to chosen folder, Loop CSV import.
- Onboarding with endowed progress; copy rules enforced in a single strings file with a review checklist.
- Submit `fdroiddata` merge request with reproducible build and `AllowedAPKSigningKeys`. Expect days–weeks of review, then 24–48 h to appear.

**Exit:** daily-driver quality for yourself; F-Droid listing live.

## Phase 2 — Gamification that the research supports (6–8 weeks) → v0.3

- Companion ("Sprout") with weekly-completion growth, energy, daily adventure, cosmetics; never-suffers rule.
- XP/levels with bounce-back bonus and rare surprise bonuses; quests (4×/week × 6 weeks, 30 AF days, 72 h smoke-free, 14-day morning anchor) with WOOP setup.
- Achievements for patterns ("Never missed twice", "Fresh start", "Kind to yourself").
- Hexad-style onboarding questions → default toggles; every mechanic switchable in Settings.
- Avoid-habit toolkit: urge timer, lapse log with triggers and self-compassion copy, money/health timelines (WHO smoking milestones), environment checklist, same-day guard.
- Weekly review (Insights) with one data-derived suggestion; fresh-start nudges (Monday / 1st / after gaps).
- More widgets: single-habit strength tile, heatmap, companion.

**Exit:** public beta call on r/fdroid, r/androidapps, Mastodon; collect feedback via GitHub Discussions.

## Phase 3 — Integrations and polish (4–6 weeks) → v0.5

- Health Connect auto-completion rules (exercise sessions, steps, sleep, mindfulness), with override and source badge.
- Temptation-bundling field and reminder copy; "minimum version" quick-action from notification ("Do the 2-minute version").
- Accessibility pass (TalkBack, large fonts, contrast), tablet/foldable adaptive layouts (Android 16 requirement), RTL.
- Weblate translations; fastlane screenshots in top locales.
- Performance: baseline profile only if it doesn't break reproducibility; startup < 500 ms.
- Optional Play dual-publish with your own signing key (12 testers × 14 days closed test first, target API 36, health-permissions declaration + privacy policy).

## Phase 4 — Sync and people (8–10 weeks) → v1.0

- Server-less sync: per-device append-only change logs, LWW with tombstones, folder transport (Syncthing/Nextcloud-friendly), then WebDAV transport (dav4jvm); conflict tests.
- Accountability partner via shareable weekly cards (image export) and, with sync, shared single-habit views.
- Opt-in small circles with collaborative weekly goals; competitive leagues as explicit opt-in only.
- v1.0: stability, migration tests, docs site, "science behind Sprout" page linking the research.

## Phase 5 — Later / ideas parked on purpose

- Wear OS standalone app (Data Layer API is Google-only; needs own transport).
- Kotlin Multiplatform / desktop / iOS (architecture keeps DAOs coroutine-only and logic pure-Kotlin so this stays possible).
- Epic-meaning partnership (completions fund trees) — only if a transparent, FOSS-compatible partner exists.
- Money commitment stakes — out of scope by design.

## Measuring whether the gamification works (without tracking users)

- No analytics. Instead: an opt-in, local-only "science mode" that lets users export anonymised aggregate stats (D7/D30 return, completion %, miss-recovery rate) and share them manually in a GitHub Discussion.
- Your own dogfood metrics to watch: % of misses followed by a completion the next day (the number the research says matters most), share of habits with strength > 80 at 90 days, rest-day usage vs repair usage.
- Run feature experiments on yourself and beta testers in 6–10-week windows, not 2-week ones, because of the novelty dip and familiarization rebound.

## Risks

- **Scope creep on gamification.** Mitigation: Layer 0 ships first; everything above it is a toggle and a separate module.
- **Reproducible build breakage** from AGP/R8 updates. Mitigation: pin, build twice in CI and diff, follow F-Droid's known-breakers list.
- **Exact-alarm and battery-optimization fragmentation.** Mitigation: inexact fallback, dontkillmyapp guidance in-app.
- **Google developer verification (from Sept 2026 in first countries).** Mitigation: register the signing key early; reproducible builds keep one key across sources.
- **Burnout as a solo maintainer.** Mitigation: small releases, public roadmap, welcome contributors early (good-first-issue labels), automate releases.
