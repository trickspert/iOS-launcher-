# Glass Launcher

An Android home-screen launcher inspired by the iOS 26 **Liquid Glass** look, built with Jetpack Compose.

## Download

Every push builds a signed APK with GitHub Actions:

* **Releases** → `apk-<branch>` → `GlassLauncher.apk`, or
* **Actions** → latest *Build APK* run → `GlassLauncher-apk` artifact.

Install it, press Home and choose **Glass** (or use the "Set" banner on the home screen).

## Features

* **Real liquid glass** – an AGSL GPU shader models each glass shape as a slab with a convex,
  circular-profile rim: light is bent most where the surface is steepest, so whatever is behind the
  glass is squeezed along its edges (with chromatic dispersion) and gently magnified in the middle.
  The body mixes a sharp and a frosted view, the rim catches a hair-thin specular line and every
  slab casts a soft contact shadow.
* **Live backdrops** – menus, folders, search, sheets and anything you drag refract a real-time
  capture of the actual screen behind them (icons included), not just the wallpaper.
* **Light that actually reflects** – highlights follow how you tilt the phone (gravity sensor) and
  the wallpaper shifts with a subtle parallax.
* **Liquid touch** – icons dip and glow under your finger, glass buttons swell and stretch with
  drags, and slider / switch thumbs turn into lenses while pressed.
* **Alive** – icons fly in around you when you come home and dive away from the app you open; the
  Clock icon and widget tick, the Calendar icon shows today, the Music widget follows what's playing.
* **Custom icons** – hand-drawn glass-finish glyphs for Phone, Messages, Camera, Photos, Settings,
  Mail, Browser, Music, Calendar, Clock, Calculator, Maps, Files, Notes and more. Other apps are
  re-rendered from their adaptive icon layers into the same style.
* **Icon styles** – Default, Dark, Clear and Tinted (with a hue slider).
* **Home screen** – pages, 4×6 or 5×7 grid, glass dock aligned to the grid, Search pill, page dots,
  widgets (Batteries, Music / Now Playing, Calendar with today's events, analog Clock).
* **Edit mode** – long-press for a context menu (app shortcuts, App Info, Remove, Uninstall) or
  keep holding and drag: icons wiggle, reorder live, move between pages and in/out of the dock,
  and drop one app onto another to make a folder.
* **Folders**, **App Library** (auto-categorised), **Spotlight search** (pull down or tap Search),
  notification **badges** (optional), iOS-26-style wallpapers or your own photo.

Requires Android 8.0+. The full refraction shader runs on Android 13+; older devices get a frosted
glass fallback.

## Building locally

```
./gradlew assembleRelease
```

The APK is signed with the public sideload key in `keystore/` so new builds install over old ones.
Set `GLASS_KEYSTORE`, `GLASS_KEYSTORE_PASSWORD`, `GLASS_KEY_ALIAS` and `GLASS_KEY_PASSWORD` to sign
with your own key instead.
