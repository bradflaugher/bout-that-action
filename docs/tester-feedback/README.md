# Closed-test feedback, round 2

Testers Community ran the closed test and sent two documents, kept here as
they arrived:

- [`feedback-report.pdf`](feedback-report.pdf): the test report. No crashes
  or bugs on any device or SDK; four "opportunities" and five extra
  recommendations.
- [`production-access-questionnaire.pdf`](production-access-questionnaire.pdf):
  suggested answers for Play's production-access form. Our own answers, true
  to what actually changed, are in
  [`production-access-answers.md`](production-access-answers.md).

What came of each suggestion:

| # | Suggestion | Outcome |
|---|---|---|
| 1 | "Rate Your App" button in Settings | **Already done** (#41): RATE sits next to SHARE and FEEDBACK in Settings and HOW TO PLAY and opens the Play page. |
| 1 | Prompt for a rating after milestones | **Deliberately not done.** No rating prompts or reminders, ever, and no In-App Review API: the game never interrupts play to ask for anything (AGENTS.md, "Fun, not compulsion"). |
| 2 | Interactive tutorial in the first session | **Done.** The first run's roof is a walkthrough: run, jump, takedown, box, pop out, lift, one at a time. Each step has a prompt, a ghost thumb acting the gesture out, brackets on what it's about and the matching billboard row lit; it waits for the move (or moves on after 25 s) and cheers. SKIP in one tap (or from the pause menu), REPLAY TUTORIAL in HOW TO PLAY, and players who'd already played never see it. HOW TO PLAY and the FIRST TIME HERE? card were already there (#40). |
| 2 | Tooltips / highlights explaining mechanics in context | **Done.** After the roof, a one-time tip for each thing the roof can't teach, the first time it would help: green passage doors, STASH, ducking into a doorway, stepping out, the grenade, GUNS HOT / SILENT, the heat meter, combos and zones. Each points at the thing (in the world or on the HUD) and is taught once per device. |
| 3 | A font that fits the neon-noir theme and reads well | **Done.** Body text and HUD labels moved from Share Tech Mono to **Chakra Petch** Medium (SIL OFL 1.1, from google/fonts; license in `licenses/ChakraPetch-OFL.txt`). Its chamfered corners match the game's cut-corner panels, it reads far better at small sizes than the thin monospace, and it has the ← ↑ → ↓ ▼ glyphs the menus use (Share Tech Mono didn't). Audiowide stays for titles. Compared: Share Tech Mono, Chakra Petch, Saira, Tektur, Exo 2, Oxanium, Rajdhani ([`font-candidates.png`](font-candidates.png)). Before/after: [how to play](font-help-before-after.png), [title](font-title-before-after.png), [HUD](font-hud-before-after.png). |
| 3 | Different font sizes for accessibility | **Done.** Settings → Text size: NORMAL / LARGE / LARGER, on top of the system font size (menus up to 1.5x in all, never shrinking a bigger system size; the HUD's labels and prompts up to 1.3x). Checked for clipping on the smallest phone with the `textsize-*` and `font-*` menu shots. |
| 4 | Daily challenges | **Already done** (#34): TODAY · DAILY on the title, one new pick a day for everyone, 1,234 challenges on the board. |
| 4 | Reward completing them | **Done, without rewards that nag.** The daily card now shows a CLEARED TODAY stamp, and your best try so far with a progress bar. **Deliberately not done:** streaks and daily rewards. AGENTS.md rules out anything that pressures people to come back every day. |
| 4 | Leaderboards | **Deliberately not done.** The game has no network permission (only VIBRATE), no accounts and no analytics, and keeps it that way (`docs/PRIVACY.md`). Seed codes and challenge SHARE messages let friends compare runs through whatever they already use. |
| + | Feedback mechanism | **Already done** (#40): FEEDBACK in Settings and HOW TO PLAY opens a GitHub issue. |
| + | Performance at every difficulty and playstyle | **Kept.** No new per-frame work for returning players (the guide is off once everything's learned); the walkthrough draws a few lines and circles. `BotPlaythroughTest` still plays every preset. |
| + | Sound and music customization | **Done, small.** MUSIC and SOUND FX volumes already existed; Settings now has a JUKEBOX that plays any zone's track you've reached, in your hero's arrangement. Per-zone music switches were skipped: one music volume (0 is off) covers it without eight more toggles. |
| + | More visual effects per floor | **No change this round.** Every zone, special floor (blackout, nap time, payday) and the Void already look different; CALM SCREEN turns the loud ones down. |
| + | Community forums / social groups | **Deliberately not done.** No in-game community features (that would need the network). GitHub issues are the place for feedback for now. |
