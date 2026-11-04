# Easy JS8 test plan

State at 2026-11-03 18:20 is in `HANDOFF.md`, "Test pass state (2026-11-03)". Steps 1 to 17 are done: all 1,007 rows passed twice or are N/A, 0 Failed. W19 (real Hamlib rigctld) passed at 18:16, with `rigctld.exe` started by Rob. W20 to W33 (real rigctld: dial, mode, PTT, split, Fake It, polling, CAT, Test CAT / PTT, rigctld closed and restarted) passed twice at 19:53. W34 to W37 (four screen fixes) and the 26 real-audio rows rerun at drift 0 passed twice by 21:40; 1,025 rows, 0 Failed.

**Rule (Rob, 2026-11-03):** EVERYTHING ON THE PC THAT CAN BE DONE, GETS DONE ON THE PC. THE PHONE AND RADIO ARE THE ABSOLUTELY LAST STEPS.

**Rule (Rob, 2026-11-03): EVERY TEST RUNS WITH UPDATES AND TIMERS.**
1. Before any work: a table, one line per step: step number; scripts and row IDs; estimate in minutes; clock time it should end; clock time of its limit, 10% over. Under it: total time and the clock time it should all be finished.
2. Every test command: its description says "est N min, limit M min"; its `timeout` is exactly N × 1.1 minutes, in seconds.
3. After every step, exactly 5 lines: **Step** (number and name); **Rows** (how many passed twice, which IDs failed, which weren't run); **Time** (minutes taken, estimate, percentage over or under); **Problems** (each on its own line, or "none"); **Next** (next step, its estimate, clock time it ends).
4. Steps over 20 minutes: the same 5-line update at the halfway clock time.
5. Over the limit: stop, give the 5-line update, wait for Rob.
6. Background runs: a timer for halfway and one for the limit. No step ends without its update.

**Left:** the phone and radio.

## How the tests change

- **One layout:** Header, lists, Settings, Station and menus run in Upright only. Every window or menu gets one open check in PC, Pages and FT8Call.
- **All four layouts:** only where a control sits differently on screen, like the waterfall panel.
- **Failed rows only:** no whole section is rerun.
- **One row first:** a changed script must pass on one row before any long run.
- **Failure limit:** a row that fails twice is written on the checklist as Failed, and testing moves on.
- **Time check:** after each step, real time is compared to the estimate. More than 10% over: stop and tell Rob. Long steps also get a check at the halfway point.
- **Time limit in the command:** every run starts with `timeout` set to 10% over its estimate, so it stops itself. Long runs also get a timer for the halfway check.
- **Every row still runs twice**, with an app restart between runs.
- **No settings sweep:** I counted the rows, and it saves about 25 minutes of testing but takes about 30 minutes to write.

## Steps

| # | Step | Rows | Time |
|---|---|---|---|
| 1 | Change the scripts to Upright only, add the hook that blocks four-layout runs, save the rule to memory | none | 30 minutes |
| 2 | Crash records: find the cause of the saved-screen crash, fix it if it is a real fault | none | 45 minutes |
| 3 | Write the missing test scripts | none | 2 hours |
| 4 | Header (H): second run, Upright | 37 controls | 8 minutes |
| 5 | Lists, waterfall, Log QSO (I, J, K): controls never tested, twice | 29 controls | 12 minutes |
| 6 | Settings, Station, menus (L, M, N), twice, skipping runs already passed | 90 controls | 29 minutes |
| 7 | Open checks in PC, Pages and FT8Call | about 15 windows | 7 minutes |
| 8 | Failed screen rows (H, J, K) | 44 rows | 12 minutes |
| 9 | Decoding and on-air rows still open (A, B, D, E, F, G), twice | 56 rows | 3 hours |
| 10 | Rig, API, server and startup rows still open (O, P, Q, R, S), twice | 33 rows | 1 hour 15 minutes |
| 11 | JS8Call 3.0.2 on your PC as the other station (T), twice | 18 rows | 2 hours |
| 12 | Real APRS and JS8NET, genuine traffic only (U), twice | 3 rows | 30 minutes |
| 13 | Remaining Failed rows, one more try | whatever is left | 1 hour |
| 14 | Write scripts for the W rows (checklist section W) | none | 1 hour 30 minutes |
| 15 | W rows, twice | 18 rows | 40 minutes |
| 16 | Sideways rows that were N/A under the one-layout rule (H, I, K, L, M, N), twice, one layout at a time | 359 cells | 2 hours 50 minutes |
| 17 | Failed rows, one more try; docs | whatever is left | 20 minutes |
| 18 | Phone and radio | not on this list | absolutely last |

## What could change the time

- **Step 2:** a real fault needs a fix and a rebuild. Each rebuild takes about 3 minutes, and the fix time is unknown.
- **Step 9:** on-air rows wait for real transmissions and timers, so this is the roughest estimate.
- **Step 11:** the audio link between the app and your PC is not proven yet. It could take longer.
- **A new app fault:** a fix needs two new passes on that row.

## The downside

A fault that shows up only in PC, Pages or FT8Call on a shared screen could be missed. The open check only proves the window opens.
