# Habitify

A customizable Android habit tracker with graphs, a markdown journal, and RPG-style profile stats. Built with Kotlin + Jetpack Compose.

**[⬇️ Download the latest APK](https://github.com/YatesV/habit-tracker/releases/latest/download/Habitify.apk)**

Open it on your phone and allow "Install unknown apps" if asked. New builds install over the old one and keep your data.

## Features

**Setup**
- First-launch walkthrough: name, goals, starter habits, look, add-ons

**📸 Photo proof**
- Turn on per habit: it only counts once you take a photo (camera-only by default, gallery optional)
- Proof gallery and proof rate on each habit page; photos stay on your phone

**🧩 Add-ons & packs (plugins)**
- Add-ons: Celebrations (confetti + haptics), Daily Motivation, Mood Tracker (Pro)
- Habit packs: built-in packs (Morning, Fitness, Study, Mindfulness, Quit, Sleep, Self-care)
- Make your own pack from your habits and share the `.json` file; import packs from others (Pro)

**👑 Habitify Pro**
- Google Play Billing subscription (`habitify_pro`): unlimited habits (free: 5), Mood Tracker, pack imports, premium themes and custom colors
- Purchases only work once the app is published on Google Play with that subscription set up

**Habit types**
- ✅ Yes / No · 🔢 Count (8 glasses) · ⏱️ Timer (30 min, live stopwatch) · 📏 Number with units (5 km) · 🚫 Quit (log slips, track clean days)

**Customize everything**
- Emoji icon and color per habit
- Schedule by weekday (every day, weekdays, custom)
- Tags with filter chips, folders as sections, archive, reorder
- Themes: System / Light / Dark / AMOLED black, 12 theme presets, Material You, 12 accent colors or a custom color
- Card styles (Filled, Outlined, Gradient, Minimal), corner roundness, text size
- Layout: compact cards, week row, grouping, odds display
- Animations: confetti, bouncy check-offs, animated charts and counters, sliding screens

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

## Pack format

```json
{
  "format": "habitify-pack", "formatVersion": 1,
  "id": "my-pack", "name": "My Pack", "emoji": "📦", "author": "you", "description": "...",
  "habits": [{ "name": "Brush teeth", "emoji": "🦷", "type": "CHECK", "requireProof": true }],
  "themes": [{ "name": "Neon", "emoji": "🌈", "seed": "#00E5FF", "mode": "AMOLED" }]
}
```

Habit `type` is one of `CHECK`, `COUNT`, `TIMER`, `NUMBER`, `QUIT` (use `target` + `unit` for count/number/timer).

## Development

Every push to `main` builds the APK with GitHub Actions and publishes it to the `latest` release.

Local tests: `./gradlew testDebugUnitTest` (UI smoke test runs on Robolectric).
