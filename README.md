# Coding Editor

| Field | Value |
|---|---|
| Package | `code.editor.mon` |
| Version | `1.0` |
| Min SDK | API 24 |
| Language | Kotlin |

## Included Libraries
- **Navigation Component** — Bottom nav with Home/Dashboard/Profile fragments
- **ProGuard** — enabled on release builds

## Getting Started in Android Studio

1. Extract this ZIP
2. Open **Android Studio** → `File > Open` → select `CodingEditor/`
3. Wait for Gradle sync to finish
4. Edit `app/src/main/java/code/editor/mon/MainActivity.kt`
5. Edit the layout at `app/src/main/res/layout/activity_main.xml`
6. Run on emulator or device

## Build via GitHub Actions (No PC Required)

1. Create a new repository on github.com
2. Upload/push the contents of `CodingEditor/` to the `main` branch
3. GitHub Actions will automatically build the APK
4. Download the APK from the **Actions → Artifacts** tab

> **Debug APK**: `CodingEditor-debug`  
> **Release APK**: `CodingEditor-release-unsigned`

## Project Structure

```
CodingEditor/
├── app/src/main/
│   ├── java/code/editor/mon/
│   │   ├── MainActivity.kt
│   │   └── Home/Dashboard/ProfileFragment.kt
│   └── res/layout/activity_main.xml
├── .github/workflows/build.yml
├── .gitignore
└── app/build.gradle
```
