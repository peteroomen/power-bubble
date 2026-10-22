# Power Bubble

A floating software power button for Android, for phones with a broken or missing power key.

A small bubble sits on the edge of the screen above every app. Tap it and four controls fan out:

- **Screenshot**: takes a normal system screenshot, with the bubble hidden so it isn't in the picture.
- **Screen off**: locks the phone exactly like the power button. Fingerprint and face unlock keep working.
- **Restart** and **Shut down**: go straight through [Shizuku](https://shizuku.rikka.app/) when it's running, after a confirmation. Without Shizuku they open the system power menu.

You can also switch tapping to turn the screen off straight away, with a long press for the menu.

The accessibility shortcut (the accessibility button in the navigation bar, the accessibility gesture or the volume-key shortcut) opens the menu by default, so the controls are one press away without finding the bubble; press it again to close the menu. If the bubble is hidden, it appears for the menu and goes again afterwards. Under **When I use the accessibility shortcut**, you can set it to show and hide the bubble, or to turn the screen off, so the button works as a power key even with the bubble hidden. Android keeps a long press of the accessibility button for itself (it chooses which feature the button runs), so apps only hear a press.

The bubble and its buttons use the same liquid glass look as the launcher. On Android 12+ phones that support window blur, the bubble blurs whatever is behind it (about as much as the launcher's Blur panels in Blur, barely in Glass), and opening the menu blurs the whole screen behind it.

On Android 13+, **Glass → True lens** bends what's under the bubble like the launcher's clear glass, using the launcher's own lens shader. Overlays can't see what's behind them, so with True lens on the service listens for the screen changing or scrolling (only the event type, never its content), shows a live blur while things move, and once the screen has been still for about half a second captures it (hiding the bubble for a split second) and keeps only the small patch under the bubble, in memory. A still screen is never recaptured. Over apps that block screenshots it shows plain glass. It's off by default.

To match **Loud**, choose **Look → Neubrutal · match Loud** in Power Bubble. This optional look uses opaque yellow, cyan, pink or paper fills, dark ink outlines and hard offset shadows. The Pop Garden, Baby Blue and Rave combos, from Loud's bolder palettes, also colour the shadow (green with hot pink, baby blue with pink, lime with deep green). Choose **Square Punch** or **Round Paper** corners. The bubble, dismiss target, menu and confirmation controls share the appearance. The menu opens with Loud's stamp-and-punch motion: the centre button is stamped down and the actions punch out like slapped-on stickers. Glass, Blur and Flat keep their existing settings and behavior; Blur remains the default. Appearance is a local user setting and does not require another exported API.

Drag the bubble anywhere and it snaps to the nearest edge. Drag it onto the ✕ to hide it, and bring it back from the **Power bubble** Quick Settings tile. There are also **Screen off** and **Screenshot** tiles, and long-press shortcuts for both on the app icon. The Screenshot tile closes the shade first so it isn't in the picture.

Screenshots taken any other way (from the recent apps screen, say) will include the bubble. Android doesn't let apps hide their windows from those.

## Install

Google Play Protect may block the install with "App blocked to protect your device". It does this for apps from outside the Play Store that ask for accessibility access, which Power Bubble needs. Ways around it, easiest first:

- **Install anyway:** if the warning has a *More details* link, tap it and choose *Install anyway*.
- **Pause Play Protect:** open the Play Store, tap your profile picture, then *Play Protect → ⚙ (settings)*, and turn off *Scan apps with Play Protect*. Install the APK, then turn scanning back on. If Play Protect later suggests removing the app, choose to keep it.
- **Install from a computer:** on the phone, tap *Settings → About phone → Build number* seven times, then turn on *Developer options → USB debugging*. Connect the phone and run `adb install power-bubble-debug.apk`. The block only applies to APKs opened on the phone, not to installs from a computer.

1. Download the APK from the latest run of the **Build** workflow (Actions tab → latest run → *power-bubble-debug*), or build it yourself with `./gradlew assembleDebug`.
2. Open Power Bubble and tap **Open Accessibility settings**, then turn on Power Bubble.
   - On Android 13+ the switch is greyed out for sideloaded apps. Go to *Settings → Apps → Power Bubble → ⋮ → Allow restricted settings*, then try again.
3. Optional, for one-tap Restart and Shut down: install Shizuku, start it with Wireless debugging, then tap **Grant permission** in Power Bubble.
   - Shizuku stops when the phone restarts. Start it again from the Shizuku app afterwards.

## Warning about shutting down

With no power button, a phone that's switched off may not turn back on. Many phones start up when plugged into a charger, but not all. Test that on yours before relying on Shut down.

To wake the screen without the button, turn on double-tap to wake, lift to wake or fingerprint wake in your phone's settings, if it has them.

## For launchers

Other apps can turn the screen off, or open the notification shade, by starting these intents (the accessibility service must be on):

```kotlin
startActivity(Intent("io.github.peteroomen.powerbubble.action.SCREEN_OFF"))
startActivity(Intent("io.github.peteroomen.powerbubble.action.NOTIFICATIONS"))
startActivity(Intent("io.github.peteroomen.powerbubble.action.SCREENSHOT"))
```

[niagara-like-launcher](https://github.com/peteroomen/niagara-like-launcher) uses these for double-tap and swipe down.

## How it works

| Piece | Implementation |
|---|---|
| Bubble, menu, drop target | `TYPE_ACCESSIBILITY_OVERLAY` windows owned by `BubbleService`. No "display over other apps" permission needed. |
| Screen off | `AccessibilityService.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` |
| Screenshot | `GLOBAL_ACTION_TAKE_SCREENSHOT`, after hiding the bubble and closing the menu |
| Restart / Shut down | `IPowerManager.reboot` / `shutdown` through a Shizuku binder (the shell user holds `REBOOT`), falling back to `GLOBAL_ACTION_POWER_DIALOG` |
| Show / hide | `BubbleTileService` Quick Settings tile |
| Settings | `SharedPreferences` (`Prefs.kt`) |

The accessibility service can't read screen content, and subscribes to events only while True lens is on, to learn that the screen changed. It can take screenshots, which it uses only when you switch on True lens, to capture the area under the bubble; those captures are never saved or sent.

## CI signing

Debug builds are signed with a random key per CI machine, so each new APK needs the old one uninstalled first (which also switches the accessibility service off). To avoid that, add a repository secret `DEBUG_KEYSTORE_BASE64` containing a base64-encoded debug keystore:

```sh
keytool -genkeypair -keystore debug.keystore -storepass android -alias androiddebugkey \
  -keypass android -keyalg RSA -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
base64 -w0 debug.keystore   # paste the output into the secret
```

Requires Android 9 or later.
