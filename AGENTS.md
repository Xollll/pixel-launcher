# Pixel Terminal Launcher (Android home app)

Instructions for any AI coding agent working in this repo.

A custom Android launcher for a **Samsung Galaxy Tab S7 FE** (2560x1600, landscape, Android 11+). Native Kotlin + Jetpack Compose. Gaming / pixel-terminal style with light animation.

Design reference: the HTML files in the `design/` folder (`Home.html`, `Home-menu-open.html`, `Lock.html`, `Lock-passcode-open.html`). Read them for exact colors, sizes, spacing and animation timings. `Home.html` and `Home-menu-open.html` are the targets for Milestones 1 and 2. Match them closely, but scale sizes to the real screen: the mockups use a 1600x1000 canvas, so do not copy pixel values directly.
Build the static layout first. Add animations as a separate later step, using the CSS keyframes in those files as the reference.

## Design files (in `design/`)
Open these and read the HTML/CSS for exact colors, sizes, spacing and animation timings.

| File | Screen | Use it for |
|---|---|---|
| `Home.html` | Home dashboard with the right-edge rail and MENU circle | **Milestone 2 target.** Rebuild the Home screen to match it. |
| `Home-menu-open.html` | App menu popup, open | **Milestone 2.** Restyle the existing Milestone 1 popup to match it (keep the working app list and search). |
| `Lock.html` | Lock screen | Reference for the lock-screen wallpaper renderer and apply flow. |
| `Lock-passcode-open.html` | Lock screen with passcode pad | Visual reference only; never implement a fake credential pad. Samsung handles secure authentication. |

## Current state
Milestones 1-3 are implemented: app launcher, designed and animated Home screen, usage rings and limits, optional music controls, calendar and device-permission flows. Selecting Pixel Launcher as the Home app applies the `Lock.html` artwork once and offers the user-controlled overlay access needed for the lock presentation. Do NOT recreate the project. Samsung's KeyguardManager remains responsible for PIN/pattern/password authentication; never implement a fake credential pad.

## Stack and setup
- Kotlin, Jetpack Compose (Material3 only where useful; custom drawing preferred), Gradle Kotlin DSL.
- `minSdk 30`, `targetSdk` latest stable, package `com.example.pixellauncher` (ask me before changing).
- Lock orientation to landscape for now.
- Build and install: `./gradlew installDebug` to the connected tablet (wireless debugging). Check `adb devices` first.
- After each milestone: run `./gradlew installDebug` (Windows: `gradlew.bat installDebug`), fix any build errors, then tell me what to look at and stop. Work in small steps and keep every file compiling.

## Safety rules
- The launcher must be reversible: never uninstall or disable other apps. Samsung's launcher must stay usable.
- Hide the launcher's own icon from the app list. Handle the back button on home (do nothing).
- Do not request permissions before the milestone that needs them.

## Theme tokens
| Token | Hex |
|---|---|
| bg | #0A0807 (radial glow #1C100B behind center) |
| panel | #120D0B (windows use ~82% opacity) |
| line | #5A3626 |
| coral (accent) | #E8795A |
| coral dark / shadow | #7A3A24 |
| cream (text) | #F4EDE4 |
| dim text | #9C8678 |
| ring mint | #6FE39A |
| ring yellow | #F2D94E |

Fonts (bundle in `res/font`, both OFL from Google Fonts): **Press Start 2P** (clock, headers, MENU) and **VT323** (body text, 26-38sp). Use hard 4dp offset shadows, 2dp borders, no rounded corners (stair-stepped pixel corners for the rail).

## Milestone 1: working launcher
- Manifest: activity with `MAIN` + `HOME` + `DEFAULT` intent filters, `launchMode="singleTask"`, exported.
- Load launchable apps with `PackageManager.queryIntentActivities` (needs the `<queries>` block for `MAIN`/`LAUNCHER`). Sort by label.
- Launch on tap. Refresh the list when packages change.
- App menu: full-screen popup with a 4x3 grid (paged if more than 12 apps), search field, close button.
- Set as default via Settings > Apps > Default apps > Home app. Document this in README.

## Milestone 2: the look (Home screen)
Landscape layout, 48dp safe margin. Elements:
1. Top bar: `> PIXEL.OS` (Press Start 2P, coral), `S7 FE // ONLINE`, Wi-Fi and battery readout with segmented bar (use real battery level).
2. Left: greeting, large live clock (Press Start 2P ~96sp, cream, coral-dark pixel shadow, blinking colon), date. Below it: pixel mascot (150dp) beside a small terminal window ("> hello, player one_"). Below that: TODO window (placeholder text).
3. Right of that: windows TODAY (placeholder agenda), NOW.PLAYING (animated pixel equalizer, placeholder track), SYS.MONITOR (segmented bars), QUICK.TOGGLE (buttons that open system panels; Android does not allow toggling Wi-Fi/BT directly).
4. Bottom: search bar window ("> SEARCH APPS_"), tapping opens the app menu.
5. **Edge rail** on the right screen edge, flush to the edge: dark pixel-stepped pill with coral 3dp outline (stair-stepped left corners, radius ~32dp, 8dp steps), small coral handle bar on top, four app shortcuts each with a circular progress ring (76dp, 7dp stroke, track #2A1C16) and a percentage label underneath. Below the rail, a coral pixel-circle **MENU** button (92dp) with a 2x2 grid icon that opens the app menu popup.
6. Effects: windows float +/-6dp on a 6s loop with staggered fade-in on launch; drifting pixel dust squares (coral, twinkle); mascot bobs and blinks. Menu popup: blurred dim backdrop, window opens with a stepped scale-in, tiles fade in one after another.
7. Windows are plain Compose boxes with a coral corner mark in the top-left.

Mascot (pixel grid, 14 wide x 12 tall, coral body with darker coral legs, dark `>` `<` eyes that blink):
- Rows 0-8 (columns): row0 3-10; rows1-3 2-11; rows4-5 0-13 (arms); rows6-8 2-11.
- Legs rows 9-11: columns 2-3, 5, 8, 10-11.
- Eyes (dark): left `>` at (4,3),(5,4),(4,5); right `<` at (9,3),(8,4),(9,5).
Draw it with Canvas `drawRect` per cell, no bitmap.

Milestone 2 uses placeholder data for rings (73/21/52/38). Real data comes in Milestone 3.

## Milestone 3 (later, do not start yet)
- Rings = daily usage vs a per-app limit via `UsageStatsManager`. Needs "Usage access" (guide me to Settings > Usage access). Store limits in DataStore. Color: mint under 60%, yellow 60-90%, coral over. Fourth ring = battery. Long-press a ring to edit its limit.
- Real clock/date, music widget via notification listener.
- Do not build a fake passcode screen or claim to replace Android's secure keyguard. The lock presentation uses live system info and requests Samsung's credential UI to unlock. Its foreground service is user-controlled through Display over other apps access.
