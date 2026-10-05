# Production access answers (draft)

Brad's answers to Play Console's production-access questionnaire, after the
closed test. They replace the testing provider's suggested answers
([`production-access-questionnaire.pdf`](production-access-questionnaire.pdf))
with what actually happened. Everything in **[BRAD: ...]** is yours to fill
in or pick before pasting.

## 1. How did you recruit users for your closed test?

I used a paid testing provider, Testers Community, to get the required
testers onto the closed track for 14 days, and **[BRAD: say who else, if
anyone: friends, family, people from a gaming community, or delete this
clause]**. The provider's testers played on a range of phones and Android
versions and sent a written report at the end.

## 2. How easy was it to recruit testers for your app?

**[BRAD: pick one: Very easy / Easy / Neither easy nor difficult / Difficult /
Very difficult]**

## 3. Describe the engagement you received from testers during your closed test

The testers played the game across a range of devices and Android versions
and reported back on stability, controls and overall feel. They found no
crashes or bugs on any device. Their feedback was mostly about the first few
minutes (learning the controls), readability, and what keeps people coming
back. **[BRAD: add anything you saw yourself, e.g. crash-free sessions in
Play Console, how many testers stuck around, any comments that came in
through FEEDBACK]**

## 4. Provide a summary of the feedback that you received from testers. Include how you collected the feedback.

The testing provider collected feedback from its testers and sent me a
written report. There were no crashes or bugs. The suggestions were:

- an easy way to rate the game from Settings;
- a walkthrough that teaches new players the controls as they play;
- a font that fits the neon-noir look and reads better, with bigger text
  for accessibility;
- more reason to come back for the daily challenge, and leaderboards;
- smaller ideas: sound and music options, more visual variety, a feedback
  channel and a community space.

## 5. Who is the intended audience for your app?

People who like quick, silly action games on their phone: arcade fans,
stealth fans and casual players who want something they can pick up for a
few minutes. It's rated for ages 13 and up, with cartoon violence and no
gore. There are no ads, no in-app purchases and no accounts, and it plays
completely offline.

## 6. Describe how your app provides value to the users.

'Bout That Action is an endless descent through a neon-noir skyscraper. You
take elevators down, sneak up behind guards, hide in a cardboard box, or go
in guns blazing. Every building is different, with four free heroes, seven
zones down to Hell and the Void, and 1,234 challenges with a new daily pick
each day. It's quick to learn, it plays offline with no ads or purchases,
and it collects no data at all.

## 7. How many installs do you expect your app to have in your first year?

**[BRAD: pick one: 0-10K / 10K-100K / 100K-1M / 1M-10M / 10M-50M / 50M+ (the
provider suggested 10K-100K)]**

## 8. What changes did you make to your app based on what you learned during your closed test?

- **Rate button.** RATE now sits next to SHARE and FEEDBACK in Settings and
  How to play, and opens the game's Play page. I deliberately did not add
  any rating pop-ups or reminders; the game never interrupts play to ask.
- **Interactive walkthrough.** A new player's first run now teaches by
  doing. On the roof it walks through one move at a time (run, jump, sneak
  up for a takedown, hide in the box, take the lift), with a ghost thumb
  showing the gesture, a highlight on what it's about, and a small cheer
  when you get it. Later, a one-time tip explains each new thing the first
  time it comes up (passage doors, the stash, grenades, GUNS HOT / SILENT,
  heat, combos, zones). It can be skipped in one tap and replayed from How
  to play, and returning players never see it.
- **Font and text size.** I replaced the body and HUD font with Chakra
  Petch, which is easier to read at small sizes and fits the look, and added
  a Text size setting (Normal, Large, Larger) on top of the system font size.
- **Daily challenge.** The daily already existed; its card now shows a
  "cleared today" stamp and your best attempt so far. I decided against
  streaks and leaderboards: the game has no internet permission and collects
  no data, and I don't want it pressuring anyone to come back every day.
- **Also:** a How to play screen with an FAQ, a Calm screen option (no screen
  shake, softer flashes), a jukebox to play each zone's music, and better
  screen reader support.

**[BRAD: trim this to fit the form's character limit if it complains]**

## 9. How did you decide that your app is ready for production?

The closed test turned up no crashes or bugs across the testers' devices,
and the automated tests (bot-played runs on every difficulty, fuzzing,
plus emulator runs on phones and tablets) pass on every build. I fixed what
the testers pointed out about the first-run experience and readability, and
played the new walkthrough myself on phones and tablets, as a new and as an
existing player. **[BRAD: add anything else you checked, e.g. the Play
pre-launch report or the Android vitals for the closed track]**

## 10. What did you do differently this time?

**[BRAD: if this is your first app, say so ("This is my first app on Google
Play") and keep the rest; otherwise compare with your last one]** This time
I focused on the first five minutes: a hands-on walkthrough instead of a
wall of instructions, text that's easier to read and can be made bigger, and
small comfort options like Calm screen. I kept the game offline, ad-free and
free of nags.
