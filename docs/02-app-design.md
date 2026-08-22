# App Design: "Sprout" (working title)

*An open-source, local-first Android habit tracker with evidence-based gamification. Kotlin + Jetpack Compose, Material 3, published on F-Droid. Working name only; rename freely.*

## Positioning in one paragraph

Loop Habit Tracker is the F-Droid standard but is Views-based, releases once every two years, and its maintainer rejects gamification. Habitica has the gamification people love but needs a server and Firebase. Finch proves that a gentle companion with zero punishment retains better than Duolingo. Nobody on F-Droid combines: native Material You, a strength score that never resets, streaks with slack, a companion that never suffers, real support for "don't" habits, Health Connect auto-completion, and server-less sync. That gap is the product.

## Design principles (each traceable to the research)

1. **Log in one tap, anywhere.** Widget, notification action, app — logging is the active ingredient (Harkin; Drink Less).
2. **Every habit has a plan.** Creating a habit requires an if-then cue and a coping plan (Gollwitzer; PLOS 2018).
3. **Nothing ever resets to zero.** Strength score (exponential smoothing) is the headline number; streaks are secondary and repairable (Lally; Silverman & Barasch; Loop).
4. **Kind after a miss.** No guilt notifications. The day after a miss is the most rewarded day in the app (StepUp megastudy; Adams & Leary).
5. **Praise, not prizes.** Feedback is specific and informational; no expected tangible rewards; surprise bonuses only (Deci 1999).
6. **Everything gamified has an off switch.** Autonomy is free retention (SDT; Duolingo opt-out).
7. **Identity over outcome.** Habits are phrased as who you are; the profile shows "you're a runner: 38 runs in 90 days".
8. **Tiny by default.** Each habit has a "minimum version" that counts (Fogg; Kaushal & Rhodes consistency β = .21).
9. **Local-first, no account, no tracking.** Data is a file the user owns; sync is optional and self-hosted.

## The gamification system

### Layer 0 — always on: strength, slack and shine

- **Strength score (0–100)** per habit, Loop-style exponential smoothing tuned to the habit's frequency. Daily habit: ~80% after a month of perfect days, 99% after three months. A miss dents it by a few points; it never resets. Shown as a growing plant stage (seed → sprout → sapling → tree → "ingrained" at 66+ days of high strength).
- **Streaks with slack.** Streak definition follows the habit's schedule (3×/week habit → weekly streak). Two **rest days** are banked per habit per fortnight, earned by completing the habit (not bought). A missed day auto-spends a rest day silently. Out of rest days? Streak shows as "paused", and completing the habit within 48 h **repairs** it (effortful earn-back, Duolingo-style). The UI shows "current run · best run · done 24/30 days" together so no single number owns the narrative.
- **Shine.** Checking off triggers haptic + a 600 ms plant-grow animation + one specific line ("Third morning run this week — that's your most consistent week since May"). Lines are generated from real data, rotated with a novelty-decay so they don't repeat within 15 days.
- **Day-7 and day-66 moments.** Milestone cards with share-to-image; never a notification about a loss.

### Layer 1 — the companion (optional, default on)

- A small garden/creature ("Sprout") that reflects the *week's* overall completion, never an individual miss. It grows, gets new scenery and seasonal items from habit completions. It never wilts, dies, or looks sad. Missed days → it simply waits; the copy is "Sprout is here when you're ready."
- Energy model like Finch: each completion gives energy; energy unlocks a short daily "adventure" (a one-line story + a small cosmetic). This is the relatedness driver, the only SDT need gamification moves reliably.

### Layer 2 — growth (optional)

- **XP and levels.** XP for completions (flat per habit, not scaled by streak length), +50% "bounce-back" bonus for completing the day after a miss, small surprise bonuses on ~1 in 12 completions (variable, unexpected). Levels unlock garden cosmetics only.
- **Quests.** Finite, research-shaped challenges: "Show up 4× a week for 6 weeks" (exercise habit threshold), "30 alcohol-free days", "First 72 hours smoke-free", "Morning anchor: 14 days". Each quest has a WOOP setup screen (Wish, Outcome, Obstacle, Plan).
- **Achievements** tied to behaviour patterns worth praising: "Never missed twice" (30 days), "Fresh start" (restarted a dormant habit), "Morning person", "Kind to yourself" (logged a lapse with a note and came back).

### Layer 3 — people (optional, off by default, later phase)

- **Accountability partner**: share one habit's weekly summary with one person (via exported share card or, later, sync). Partners see progress; nobody is damaged by your misses.
- **Small circles** (3–8 people) with a *collaborative* weekly goal, not a leaderboard. Competitive leagues only as an explicit opt-in for users who self-identify as competitive (Hexad "Player/Achiever").
- **Commitment stake** (social): a referee gets your week's card. Money stakes: deliberately out of scope.

### Hexad personalization at onboarding

Three quick questions map to a starting profile: Achiever → quests and levels on; Socialiser → companion and partner prompts; Free Spirit → minimal mode (strength + shine only); Player → XP, surprises; Philanthropist → "your consistency funds a tree" style epic-meaning framing (optional, via partner NGOs later). All are toggles in Settings afterwards.

## Habit types

| Type | Examples | Logging unit | Gamified signal |
|---|---|---|---|
| **Do, boolean** | gym, meditate, read | tap to complete; optional minimum version | strength, streak, shine |
| **Do, measurable** | water 2 L, 8,000 steps, 20 pages | numeric with target; Health Connect auto-fill | partial credit toward target |
| **Avoid (daily check-in)** | no alcohol, no smoking, no nail biting | evening "clean day?" + instance counter during the day | clean-day strength, "days since", % clean last 30, money/health timeline |
| **Reduce** | ≤ 2 coffees, ≤ 30 min Instagram | counter against a ceiling | under-ceiling days count as done |
| **Anchor** | "after I brush my teeth" | not logged; used as cue for stacked habits | stacking chains |

Avoid habits get extra tools: urge timer (5–10 min breathing, "this will pass"), lapse log (trigger tags: stress, conflict, social, boredom, cue; amount; one kind sentence auto-suggested), money and health-milestone counters (WHO timeline for smoking), environment checklist at setup ("remove it from the house, change the route, tell one person"), and a same-day second-lapse guard that shows the coping plan.

## Habit creation flow (the plan is the product)

1. What? (name, icon, type, identity phrasing: "I'm someone who …")
2. How small is the smallest version that still counts?
3. When & where? → if-then cue; anchor picker from existing habits/routines
4. If it goes wrong? → coping plan ("If I miss the morning, then …")
5. Schedule: daily / x per week / specific days / every N days
6. Reminder: time, 30 min before, notification actions Done / Skip / Snooze
7. Optional: pair with a treat (temptation bundling), Health Connect auto-complete rule, quest

Fresh-start nudges: the app suggests creating or restarting habits on Mondays, the 1st, birthdays (if set), and after long gaps, with copy framed as a new chapter, not a failure.

## Screens

- **Today** — checklist sorted by time-of-day, one-tap complete, swipe for skip/note, strength ring per habit, companion strip on top. Empty state teaches the first habit with endowed progress ("Day 1 of 66 — you already showed up").
- **Habit detail** — strength curve, calendar heatmap, current/best run + 30-day %, plan card (cue, coping plan), notes, stats (best time of day, weekday pattern), edit.
- **Garden** — companion, cosmetics, quests in progress, achievements.
- **Insights** — weekly review (Sunday): what went well, one suggestion derived from data ("Your Tuesday gym completion is 30% — move it to Thursday?"), monthly trend charts.
- **Lapse / Urge** — full-screen tools for avoid habits, reachable from notification and widget.
- **Settings** — gamification toggles (companion, XP, quests, social), reminders & exact-alarm permission, backup folder, sync, theme (dynamic color, AMOLED black), data export/import, about/licenses.
- **Widgets (Glance)** — today checklist (tap to complete), single-habit strength tile, heatmap, companion.
- **Notification** — reminder with Done/Skip/Snooze; "streak saver" only if the user opts in, phrased neutrally ("Still time for a 2-minute version?").

## Architecture

- **Modules**: `:app`, `:core:model`, `:core:database` (Room), `:core:datastore`, `:core:scoring` (pure Kotlin: strength, streaks, rest days, XP — 100% unit-tested), `:core:scheduling` (alarm computation, pure Kotlin), `:core:ui` (theme, components), `:feature:today`, `:feature:habit`, `:feature:garden`, `:feature:insights`, `:feature:settings`, `:widget`, `:sync` (opt-in), `:health` (Health Connect adapter).
- **Pattern**: MVVM + unidirectional data flow, ViewModel exposes `StateFlow<UiState>`, repositories over Room DAOs returning `Flow`. Hilt for DI (swap to Koin only if KMP becomes a goal).
- **Data model** (UUID ids, `updated_at`, soft-delete `deleted_at` on every row, for mergeable sync):
  - `habit`: id, name, identity_phrase, type (DO_BOOL, DO_NUMERIC, AVOID, REDUCE, ANCHOR), unit, target, ceiling, schedule (serialized rule), min_version, cue_text, coping_plan, anchor_habit_id, color, icon, position, archived_at, created_at, health_rule (json), bundle_text
  - `entry`: id, habit_id, date (epoch-day), status (DONE, DONE_MIN, SKIP, MISS, LAPSE), value, note, source (MANUAL, WIDGET, NOTIFICATION, HEALTH), created_at
  - `lapse`: id, habit_id, timestamp, triggers (set), amount, note
  - `reminder`: id, habit_id, time, days_mask, lead_minutes, enabled
  - `rest_day_ledger`: habit_id, date, earned/spent
  - `xp_ledger`: id, source, amount, timestamp
  - `quest`, `quest_progress`, `achievement_unlock`, `companion_state`, `settings`
- **Scoring**: strength_t = strength_{t-1} × α + completion × (1 − α), α derived from schedule so a daily habit reaches ~99 after 90 days; skips and rest days hold strength constant; misses decay it. Streak engine works on "scheduled occasions" not calendar days.
- **Reminders**: AlarmManager exact (`SCHEDULE_EXACT_ALARM`, graceful inexact fallback with a banner), rescheduled on boot/timezone/permission change; WorkManager for midnight rollover, widget refresh, backups, Health Connect polling.
- **Health Connect**: `connect-client`; rules like "any ExerciseSession ≥ 20 min" or "steps ≥ target" mark entries with source = HEALTH; user can override; gated by `getSdkStatus()`.
- **Backup & sync**: JSON full backup via Storage Access Framework to a user folder (auto-backup daily, keep 7); Loop CSV/SQLite importer; per-device append-only change logs merged last-writer-wins with tombstones, so a Syncthing/Nextcloud folder is a valid sync transport; later WebDAV transport (dav4jvm) using the same merge engine.
- **Flavors**: `foss` (no INTERNET unless sync enabled, zero Google deps — F-Droid builds this) and `play`.
- **Testing**: JUnit + Turbine for scoring/scheduling, Room migration tests with exported schemas, Compose UI tests on Robolectric, Roborazzi screenshots for widgets and theme.
- **Privacy**: no analytics, no network by default, optional ACRA crash reports by email, privacy policy required anyway for Health Connect.

## Visual identity

Material 3 with dynamic color (Material You) by default, with a calm, organic brand theme fallback (moss green primary, warm sand surfaces, never red for misses — misses are neutral grey). Plant/garden metaphor throughout (growth, seasons) instead of fire/flame streak iconography (Duolingo found the flame misread in India; fire also signals loss when extinguished). Motion: one orchestrated moment per completion; respects reduced-motion.

## Copy rules

- Never: "You broke your streak", "Don't let Sprout down", "You failed".
- Always specific: numbers from the user's own data.
- Misses: "Missed yesterday. One miss changes almost nothing — today does." Lapses: "Logged. Noticing it is the skill. What was going on?" then the coping plan.
- Identity: "You're a reader — 24 sessions this month."

## License and governance

GPL-3.0-or-later (matches Loop/Habitica/Habit-Maker, deters ad-laden Play clones), DCO sign-off instead of CLA, Weblate for translations, Liberapay/GitHub Sponsors for donations, public roadmap in GitHub Projects, fastlane metadata from day one.
