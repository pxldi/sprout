# Sprout

Android habit tracker. Kotlin 2.2.10, AGP 9.3.1, Jetpack Compose with Material 3,
Room, DataStore, Hilt, Navigation Compose. Local-first: no account, no network,
no analytics. The research behind every mechanic is `docs/01-research.md`; the
design it produced is `docs/02-app-design.md`. `CLAUDE.local.md`, when present,
says where the owner keeps the current spec and roadmap.

## The rules that matter

**The numbers mean what they say.** Strength, runs, the recent fraction and
every praise line are computed from the log. A number that looks better than
the behaviour behind it breaks the app's premise. When you change scoring, add
a test that pins a rate (for example, 50% adherence must not score like 100%),
not only a single case.

**No guilt.** No copy that blames, shames or warns about loss. Nothing the
user sees first resets to zero. A miss renders in the neutral grey
(`MissNeutral`), never in red or the error colour. The copy rules are in
`docs/02-app-design.md`; all user-visible strings live in
`core/ui/src/main/res/values/strings.xml` so they can be reviewed in one diff.

**The database is the only copy of the user's data.** Migrations are written
by hand and tested. `fallbackToDestructiveMigration` is never acceptable.

## How to work

- Make reasonable calls, fix what you find. Ask only when a choice changes
  what the product is, or before anything that can lose user data.
- Match the surrounding code's style. Don't add summary `.md` files.
- `./gradlew test detekt lint` before calling anything done. CI runs `test`,
  `detekt` and `assembleFossDebug` but not `lint`, so a green CI does not
  mean lint passes.

## Commands

`JAVA_HOME` must point at a JDK 17 and `ANDROID_HOME` at an SDK with API 37.

| Task | Command |
| --- | --- |
| All unit tests | `./gradlew test` |
| One module | `./gradlew :core:scoring:test` |
| One test class | `./gradlew :core:scoring:test --tests 'dev.sprout.core.scoring.StreakSlackTest'` |
| Static analysis | `./gradlew detekt lint` |
| Debug APK | `./gradlew :app:assembleFossDebug` |
| Install on a device | `./gradlew :app:installFossDebug` |
| Release APK (R8, reproducible) | `./gradlew :app:assembleFossRelease` |

## Layout

- `core/model` — pure Kotlin domain types: `Habit`, `Entry`, `ScheduleRule`, `Reminder`, `Lapse`
- `core/scheduling` — pure Kotlin: `OccasionCalendar` (schedule to occasions), `ReminderCalendar` (next alarm)
- `core/scoring` — pure Kotlin: `HabitScorer` (strength, runs, rest days, repair), `PlantStage`
- `core/database` — Room entities, DAOs and the four repositories; Room types are `internal`
- `core/datastore` — `ShineHistory`, which praise line was said when
- `core/ui` — theme, shared components, the one strings file, reminder permission UX
- `feature/today` — the Today screen, shine lines, milestones
- `feature/habit` — creation wizard, edit, list, detail (curve, heatmap, notes)
- `app` — `MainActivity`, navigation, reminder receivers and `ReminderScheduler`

## Conventions

- `core/model`, `core/scheduling` and `core/scoring` have no Android dependency.
  Keep it that way; their tests are the specification.
- Library modules use `explicitApi()`, so public declarations carry `public`.
- Ids are UUID strings. Every table has `updated_at` and `deleted_at`; rows are
  tombstoned, never deleted. Repositories stamp `updated_at`; callers do not.
- A miss is the absence of an entry. There is no `MISS` status.
- One entry per habit per day, enforced by a unique index. Every entry write
  goes through `EntryRepository`, which holds the write lock, because
  notification actions write from a `BroadcastReceiver` with no ViewModel.
- Time comes from the injected `java.time.Clock`. Never call `LocalDate.now()`
  without it.
- One alarm is registered at a time. `SproutApplication` reschedules when
  habits or reminders change; `SystemEventReceiver` covers boot, update and
  time changes.

## Things that bite

- AGP 9 has built-in Kotlin. Applying `org.jetbrains.kotlin.android` is a hard
  error. Kotlin and KSP versions follow what AGP bundles, not the newest
  release; bump them together after checking AGP's POM.
- `gradle.properties` sets `android.disallowKotlinSourceSets=false` because
  this KSP still registers sources through `kotlin.sourceSets`.
- `compileSdk` is 37 and `targetSdk` 36 on purpose: AndroidX 2026.08 refuses
  to compile against 36.
- `versionCode` is a literal. F-Droid reproducible builds must not derive it.
- Exact alarms are denied by default from Android 14. The inexact
  `setWindow` path is the normal one, not a fallback.

## How to write

Plain English. Say what changed and what it does. Do not give a thing a will
("the scorer decides"), do not use "not X, but Y" for effect, do not finish on
an aphorism, do not use an em dash as the default joint, do not say the same
thing twice in a different shape.

Short declarative sentences, active voice, ordinary words, numbers and file
paths instead of adjectives. Commit subjects are imperative and name the
change. PR bodies say what changed and why, and what test would have failed.
Code comments say why the code is the way it is, in one or two sentences.

Interface text is shorter again. It is read by somebody mid-task, often with
one thumb. Name the thing and what to do. A control label is one to three
words, verb first. An error is at most two sentences.
