# Working conventions for this repo

## Android apps: always pin a fixed debug keystore

Every native Android app in this repo is built as a debug APK by GitHub Actions and
downloaded from the run's artifacts - there's no computer/Android Studio in this workflow,
so `adb install -r` (or just tapping the downloaded APK to update) is how it gets onto the
phone.

**The problem:** GitHub Actions runners are ephemeral. Without an explicit `signingConfigs`
block, the Android Gradle Plugin signs debug builds with `~/.android/debug.keystore`, which
the SDK auto-generates fresh on first use - a *different* keystore (and therefore a
different signing certificate) on every single CI run. Installing a newer APK over an
older install then fails with "App not installed as package conflicts with an existing
package" (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), because Android refuses to update an app
in place unless the new APK is signed with the same certificate as the one already
installed. The workaround of uninstalling first loses all local app data every time.

**The fix, apply it to every new Android app module in this repo from the start:**

1. Generate one fixed debug keystore per app module and commit it to the repo (it's a
   debug-only key with the standard well-known password, not a secret - this is normal
   practice, not a credential leak):
   ```
   keytool -genkeypair -v -keystore debug.keystore -alias androiddebugkey \
     -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10950 \
     -dname "CN=<App Name> Debug, OU=Dev, O=Yaniv, L=Unknown, S=Unknown, C=IL"
   ```
   Place it at the app module root (e.g. `my-app/debug.keystore`, next to `app/`).

2. Wire it into `app/build.gradle.kts` so every build - local or CI - signs with it:
   ```kotlin
   android {
       signingConfigs {
           getByName("debug") {
               storeFile = file("../debug.keystore")
               storePassword = "android"
               keyAlias = "androiddebugkey"
               keyPassword = "android"
           }
       }
       buildTypes {
           debug {
               signingConfig = signingConfigs.getByName("debug")
           }
       }
   }
   ```

3. Confirm the module's `.gitignore` doesn't exclude `*.keystore` (the templates used so
   far in this repo don't, but double-check on any new module).

Do this **before** the first CI build of a new app module, not as a later fix - once an
app has been installed on the phone with an unpinned (randomly-keyed) build, the very next
build that adds this fix will itself conflict with that already-installed copy once, and
the phone will need one manual uninstall to resync. After that one-time reset, every future
build updates cleanly.
