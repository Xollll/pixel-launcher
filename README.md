# Pixel Launcher (Milestone 1)

Kotlin + Jetpack Compose home app. Shows a clock, a MENU button and an app menu popup (all installed apps, search, tap to launch).

## First-time setup
1. Copy `gradlew`, `gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar` from any Flutter project's `android/` folder into this folder. Do NOT copy its `gradle-wrapper.properties`; this project has its own.
2. Create `local.properties` here with your SDK path (run `flutter doctor -v` and look for "Android SDK at ..."):
   - Windows: `sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk`
   - macOS: `sdk.dir=/Users/YOU/Library/Android/sdk`
   - Linux: `sdk.dir=/home/YOU/Android/Sdk`
3. Connect the tablet (`adb devices` should list it).

## Build, install, run
    ./gradlew installDebug        (Windows: gradlew.bat installDebug)
    adb shell am start -n com.example.pixellauncher/.MainActivity

## Make it your Home app
Press the Home button, pick "Pixel Launcher", choose "Always".
To go back: Settings > Apps > Choose default apps > Home app > Samsung One UI Home.

## Usage rings and permissions (Milestone 3)
Usage Access is optional and must be enabled separately on each device. On the home screen, tap **ENABLE USAGE ACCESS**, then select Pixel Launcher in Android's Usage Access settings and allow access. The label and menu location vary by Android version and manufacturer; if Android does not open that page, look under Settings > Apps > Special app access > Usage access. With access enabled, the first three rings show today's usage against each app's daily limit. Long-press a ring to change its limit. Limits are saved locally on that device.

The fourth ring reads the device battery and needs no special permission. Home TODOs are stored on the device and can be added, checked off, and removed. The greeting and status bar use the device name from Android settings. **TODAY** reads calendar events after you grant Calendar access when prompted. **NOW.PLAYING** can open Spotify from the card and control an active Spotify session after Notification access is enabled; if Spotify has no active session, Play opens Spotify so playback can be started there. These permissions are optional and must be enabled separately on each device.

## Lock screen design
When Pixel Launcher is first selected as the Home app, it automatically applies the artwork from `design/Lock.html` and offers Android's **Display over other apps** permission so the interactive presentation can open when the screen wakes. Android still requires the user to grant that permission. A persistent notification shows when its foreground service is running. The lock presentation displays live time, date, Wi-Fi and battery, public notification content (private notification content remains hidden), and a camera shortcut. **SWIPE UP TO UNLOCK** invokes Samsung's secure credential UI. The sample keypad in the HTML mockup is intentionally not used. Normal app updates do not require applying the wallpaper again. Revoking Display over other apps stops the presentation; Android's overlay settings can restore access, and Samsung's wallpaper and lock settings can restore the default look.

## Remove it completely
    adb uninstall com.example.pixellauncher
