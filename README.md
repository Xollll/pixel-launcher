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

## Remove it completely
    adb uninstall com.example.pixellauncher
