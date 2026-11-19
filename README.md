# Glass Launcher

An Android home-screen launcher inspired by the iOS 26 **Liquid Glass** look, built with Jetpack Compose.

## Download

Every push builds a signed APK with GitHub Actions:

* **Releases** → `apk-<branch>` → `GlassLauncher.apk`, or
* **Actions** → latest *Build APK* run → `GlassLauncher-apk` artifact.

Install it, press Home and choose **Glass** (or use the "Set" banner on the home screen).

## Features

* **Real liquid glass** – an AGSL GPU shader bends (refracts) the wallpaper at every glass edge with
  chromatic dispersion, a lens-like magnification, frosting and a specular rim.
* **Light that actually reflects** – highlights follow how you tilt the phone (gravity sensor).
* **Liquid touch** – glass swells, glows where your finger is and stretches with drags; switches and
  slider thumbs turn into glass lenses while pressed.
* **Custom icons** – hand-drawn glyphs for Phone, Messages, Camera, Photos, Settings, Mail, Browser,
  Music, Calendar (live date), Clock (live time), Calculator, Maps, Files, Notes and more. Other apps
  are re-rendered from their adaptive icon layers into the same glass style.
* **Icon styles** – Default, Dark, Clear and Tinted (with a hue slider).
* **Home screen** – pages, 4×6 or 5×7 grid, glass dock, Search pill, page dots, widgets
  (Batteries, Calendar with today's events, analog Clock).
* **Edit mode** – long-press for a context menu (app shortcuts, App Info, Remove, Uninstall) or
  keep holding and drag: icons wiggle, reorder live, move between pages and in/out of the dock,
  and drop one app onto another to make a folder.
* **Folders**, **App Library** (auto-categorised), **Spotlight search** (pull down or tap Search),
  notification **badges** (optional), wallpapers (built-in or from Photos), and smooth spring
  animations throughout, including the app open / return zoom.

Requires Android 8.0+. The full refraction shader runs on Android 13+; older devices get a frosted
glass fallback.

## Building locally

```
./gradlew assembleRelease
```

The APK is signed with the public sideload key in `keystore/` so new builds install over old ones.
Set `GLASS_KEYSTORE`, `GLASS_KEYSTORE_PASSWORD`, `GLASS_KEY_ALIAS` and `GLASS_KEY_PASSWORD` to sign
with your own key instead.

## Larp Vault (`larp/`)

A separate, just-for-fun app: a black "midnight glass" balance screen (frosted cards, blue-teal glow)
with a simple crypto list where every number is made up.
Tap the balance to set the total, the change line and the account name, or tap any token to edit its
name, symbol, amount, value, change and color. Add or delete tokens; everything is saved on the device.
It has no send, receive or network features, and it shows a **DEMO** tag.
CI publishes it as **LarpVault.apk** next to GlassLauncher.apk.
