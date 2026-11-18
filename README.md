# Habitify

A customizable Android habit tracker with graphs, a markdown journal, and RPG-style profile stats. Built with Kotlin + Jetpack Compose.

**[⬇️ Download the latest APK](https://github.com/YatesV/habit-tracker/releases/latest/download/Habitify.apk)**

Open it on your phone and allow "Install unknown apps" if asked. New builds install over the old one and keep your data.

## Features

**Habit types**
- ✅ Yes / No · 🔢 Count (8 glasses) · ⏱️ Timer (30 min, live stopwatch) · 📏 Number with units (5 km) · 🚫 Quit (log slips, track clean days)

**Customize everything**
- Emoji icon and color per habit
- Schedule by weekday (every day, weekdays, custom)
- Tags with filter chips, folders as sections, archive, reorder
- Themes: System / Light / Dark / AMOLED black, Material You or 12 accent colors
- Layout: compact cards, week row, grouping, odds display

**Graphs**
- GitHub-style year heatmap (per habit and overall)
- Success trend lines (weeks or months)
- Day-of-week bars
- Value charts with goal line for count/timer/number habits
- Leaderboard and per-tag breakdown

**Profile**
- ⚡ Willpower score (0–100) from discipline, consistency, resilience, endurance and momentum
- 🎯 Follow-through odds: how likely you are to finish each habit today, from your recent history
- XP, levels and ranks (Rookie → Legend)
- Attribute radar chart and 15 unlockable badges

**Journal (Obsidian-style)**
- Daily markdown notes: headings, bold, checklists, quotes
- `[[Habit name]]` links that open the habit, with backlinks on the habit page
- `#tags`, search and tag filters, a daily template

**Data**
- Everything stays on your phone, with no account needed
- Export/import a JSON backup in Settings

## Development

Every push to `main` builds the APK with GitHub Actions and publishes it to the `latest` release.

Local tests: `./gradlew testDebugUnitTest` (UI smoke test runs on Robolectric).
