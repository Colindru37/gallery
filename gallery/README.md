# Gallery

A Samsung-Gallery-style photo gallery for the Nothing Phone 2. No ads, no lag, real subfolders.

## Features

- **Pictures** — timeline grouped by day, pinch to change grid density (2–6 columns)
- **Albums** — real nested subfolders (DCIM → Camera, Screenshots… not a flat dump)
- **Viewer** — swipe between items, pinch/double-tap zoom, inline video playback (ExoPlayer)
- **Favourites** — synced to MediaStore (shared with other gallery apps)
- **Trash** — 30-day recycle bin via the system trash, restore / delete forever / empty
- **Hidden albums** — hide any folder from Pictures + Albums, manage from More
- **Search** — filename, folder, "july 2025", with Image/Video/Favourite filters
- **Edit** — crop (free, with handles), rotate 90°, flip; saves as a copy
- **Manage** — multi-select share / move / copy / rename / trash
- Registers as an image/video opener, so other apps can open media in it

## Build (no Android Studio needed)

Same pipeline as volume-boost-spike:

1. Push this folder to a GitHub repo
2. GitHub Actions builds the debug APK (`.github/workflows/build.yml`)
3. Download from the Actions artifact or the `gallery-latest` release
4. Sideload onto the phone

## After installing

1. Grant photo/video access on first launch
2. **Settings → Grant "Media management"** — this is the big one: without it,
   Android shows a confirmation dialog for every delete/favourite/move on files
   owned by other apps, and the Trash can only show items this app trashed.
   With it, the app behaves like a real system gallery.

## Stack

Kotlin · Jetpack Compose (Material 3) · Coil (images, video thumbs, GIFs) · Media3 ExoPlayer · MediaStore (no database of its own — favourites/trash live in Android itself)
