# Power Bubble

A floating software power button for Android, for phones with a broken or missing power key.

A small bubble sits on the edge of the screen above every app. Tap it and three controls fan out:

- **Screen off**: locks the phone exactly like the power button. Fingerprint and face unlock keep working.
- **Restart** and **Shut down**: go straight through [Shizuku](https://shizuku.rikka.app/) when it's running, after a confirmation. Without Shizuku they open the system power menu.

You can also switch tapping to turn the screen off straight away, with a long press for the menu.

Drag the bubble anywhere and it snaps to the nearest edge. Drag it onto the ✕ to hide it, and bring it back from the **Power bubble** Quick Settings tile. There's also a **Screen off** tile and a long-press shortcut on the app icon.

## Install

1. Download the APK from the latest run of the **Build** workflow (Actions tab → latest run → *power-bubble-debug*), or build it yourself with `./gradlew assembleDebug`.
2. Open Power Bubble and tap **Open Accessibility settings**, then turn on Power Bubble.
   - On Android 13+ the switch is greyed out for sideloaded apps. Go to *Settings → Apps → Power Bubble → ⋮ → Allow restricted settings*, then try again.
3. Optional, for one-tap Restart and Shut down: install Shizuku, start it with Wireless debugging, then tap **Grant permission** in Power Bubble.
   - Shizuku stops when the phone restarts. Start it again from the Shizuku app afterwards.

## Warning about shutting down

With no power button, a phone that's switched off may not turn back on. Many phones start up when plugged into a charger, but not all. Test that on yours before relying on Shut down.

To wake the screen without the button, turn on double-tap to wake, lift to wake or fingerprint wake in your phone's settings, if it has them.

## For launchers

Other apps can turn the screen off by starting this intent (the accessibility service must be on):

```kotlin
startActivity(Intent("io.github.peteroomen.powerbubble.action.SCREEN_OFF"))
```

## How it works

| Piece | Implementation |
|---|---|
| Bubble, menu, drop target | `TYPE_ACCESSIBILITY_OVERLAY` windows owned by `BubbleService`. No "display over other apps" permission needed. |
| Screen off | `AccessibilityService.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` |
| Restart / Shut down | `IPowerManager.reboot` / `shutdown` through a Shizuku binder (the shell user holds `REBOOT`), falling back to `GLOBAL_ACTION_POWER_DIALOG` |
| Show / hide | `BubbleTileService` Quick Settings tile |
| Settings | `SharedPreferences` (`Prefs.kt`) |

The accessibility service doesn't subscribe to any events and can't read screen content.

## CI signing

Debug builds are signed with a random key per CI machine, so each new APK needs the old one uninstalled first (which also switches the accessibility service off). To avoid that, add a repository secret `DEBUG_KEYSTORE_BASE64` containing a base64-encoded debug keystore:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
base64 -w0 debug.keystore   # paste the output into the secret
```

Requires Android 9 or later.
