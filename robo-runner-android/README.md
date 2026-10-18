# Robo Runner for Android

This is an Android app that runs the Robo Runner game full screen, in landscape and offline. Online
user levels work as long as the phone has internet.

It's a thin native wrapper, a full-screen WebView. The game itself is
`app/src/main/assets/index.html`, the same file as `robo-runner.html`, so everything you build in the web
version carries over: levels, editor, coins, skins and Firebase.

What the app adds on top of the web version:
- **Back button and back gesture:** these close dialogs, leave levels and go up a menu. On the home
  screen, back exits the app.
- **Full screen:** the status and navigation bars are hidden, and the screen stays on while you play.
  The game is kept clear of the camera notch.
- **Landscape everywhere:** tablets and foldables included. The app is marked as a game, which Android 16
  needs before it will keep the landscape lock on big screens.
- **Pausing:** when you switch away, the music stops, and the level freezes for half a second when you come back.
- **Files:** importing level JSON and picking MP3s for level music use Android's file picker. The
  **download** button in the level JSON box opens Android's "save as" screen and writes a real `.json`
  file to Downloads, Drive or wherever you pick. When the keyboard is open, the game shrinks above it.
- **Saves:** progress, coins and your levels are saved on the phone. Uninstalling the app deletes them.

---

## Option A: Android Studio (recommended)

1. Install **Android Studio** (free) from https://developer.android.com/studio.
2. **File > Open** and choose this `robo-runner-android` folder. The first sync downloads Gradle and the
   Android SDK pieces it needs, which takes a few minutes. If Android Studio offers to upgrade the
   Android Gradle Plugin, you can say no.
3. To run it on your phone:
   - On the phone, go to **Settings > About phone** and tap **Build number** 7 times. Then open
     **Developer options** and turn on **USB debugging**.
   - Plug the phone in, pick it in the device list at the top of Android Studio, and press the green ▶ **Run** button.
4. To get an APK file instead, use **Build > Build App Bundle(s) / APK(s) > Build APK(s)**. The file appears
   in `app/build/outputs/apk/debug/app-debug.apk`. Copy it to any Android phone and open it to install.
   The phone will ask you to allow installing apps from that source.

You can also run it on the built-in emulator: **Device Manager > Create device**, any phone, then press Run.

## Option B: Build the APK on GitHub (no Android Studio needed)

1. Create a new repository on GitHub and upload the contents of this folder, including the hidden
   `.github` folder.
2. Open the repository's **Actions** tab. The **Build APK** workflow runs on every push, or you can press
   **Run workflow** to start it by hand.
3. When it finishes (about 3 to 5 minutes), open the run and download **robo-runner-apk** under
   **Artifacts**. Unzip it to get `app-debug.apk`.

### Updates install over the old app

Debug builds are signed with the fixed key in `app/debug.keystore` (Android's standard debug key and
passwords). Every build, from Android Studio or from GitHub, is signed the same way. A new APK
installs over the old one and keeps your progress, coins and levels.

That key is only for testing. The Play Store version uses your own private key (see below).

## Updating the game inside the app

Whenever you change `robo-runner.html`:
1. Copy it over `app/src/main/assets/index.html` (keep the name `index.html`).
2. Rebuild with Run, Build APK or a push to GitHub.

For Play Store updates, also raise `versionCode` by 1 in `app/build.gradle.kts`, and `versionName` if you like.

## Publishing on Google Play

1. **Make a release key.** Use **Build > Generate Signed App Bundle or APK > Android App Bundle** and create
   a new key store. **Back up the `.jks` file and its passwords.** Every future update must be signed
   with it. (Keep it out of GitHub; `.gitignore` already excludes it.)
2. That produces `app/release/app-release.aab`.
3. Create a Google Play Console developer account (one-time fee) and create the app.
   - Fill in the store listing, content rating and data safety form.
   - For data safety: the app stores game data on the device. If you use online levels, it sends level
     data and an anonymous ID to Firebase.
4. Upload the `.aab` to a testing track first.
   - New personal developer accounts must run a closed test with a group of testers for a couple of
     weeks before they can publish publicly. Play Console shows the current numbers.
5. This project already targets Android 16 (API 36), which Google Play requires for new apps and updates
   since August 31, 2026.

The package name is `com.tfishapps.roborunner`. To change it, edit `applicationId` and `namespace` in
`app/build.gradle.kts` before your first Play upload; after that it can never change. Also rename the
`java/com/tfishapps/roborunner` folder and the `package` line in `MainActivity.java` to match.

## Online levels (Firebase)

The app works with your existing Firebase project as-is, because the game loads from a secure https
address inside the app (`https://appassets.androidplatform.net`). Nothing needs adding in the Firebase
console. Players in the app and on the web share the same online levels.

If online levels work on the web but not in the app, check one setting. In the Google Cloud console,
open **APIs & Services > Credentials**, find the "Browser key" for your project, and look at its
restrictions. If it has **HTTP referrer** restrictions, add `https://appassets.androidplatform.net/*`.
New Firebase projects have no restriction there, so usually there's nothing to do.

## Debugging

In a debug build, you can inspect the game from desktop Chrome while it runs on the phone. Open
`chrome://inspect` with the phone plugged in, and you get the console, errors and storage, just like a web page.

## Files

| File | What it is |
|---|---|
| `app/src/main/assets/index.html` | the game |
| `app/src/main/java/.../MainActivity.java` | the wrapper: WebView setup, back button, file picker, full screen, share sheet |
| `app/src/main/AndroidManifest.xml` | landscape, internet permission, app icon and name |
| `app/src/main/res/` | app name, colours, theme and launcher icon (a vector, so it's sharp at every size) |
| `app/build.gradle.kts` | app id, version, SDK levels |
| `.github/workflows/build-apk.yml` | the GitHub build |
