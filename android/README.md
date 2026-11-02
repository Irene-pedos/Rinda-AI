# Rinda Android app

For Agy. This is the stock Android Studio template (Java + XML); no features are built yet. Open this `android/` folder in Android Studio, not the repo root.

Read `docs/RINDA_PROJECT.md` first, especially §2.2 and §9. The API contract is in `docs/API.md`, including which endpoints are built and which are still drafts.

## Fix these before the first build

The template does not build yet:

1. **Raise `compileSdk` to 36** in `app/build.gradle.kts`. `androidx.activity:1.13.0` requires it. The `android-36` platform is installed on the lab computer. This fix has not been tested, and AGP 8.9.1 may also need a newer version.
2. **Use JDK 21 for Gradle.** The default `java` on the lab computer is JDK 26, which Gradle 8.11.1 cannot run on. Android Studio's bundled JDK works:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```
