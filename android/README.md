# Rinda Android app

For Agy. The farmer app (Java + XML): onboarding, phone login, plots, plot detail, alerts, claims and profile. Open this `android/` folder in Android Studio, not the repo root.

Read `docs/RINDA_PROJECT.md` first, especially §2.2 and §9. The API contract is in `docs/API.md`, including which endpoints are built and which are still drafts. **Every new screen follows `docs/ANDROID_UI_GUIDELINES.md`**, which lists the design system, the reusable parts and the screens still to build.

## Building

**Use JDK 17 for Gradle.** Gradle 8.11.1 cannot run on the lab computer's default `java` (JDK 26) or on Android Studio 2026.2's bundled JDK 25. In Android Studio the Gradle JDK is already set to 17 (`.idea/gradle.xml`). From the command line:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat assembleDebug
```
