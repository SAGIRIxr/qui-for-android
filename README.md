# qui for Android

**English** · [简体中文](README.zh-CN.md)

A native Android client for [qui](https://github.com/autobrr/qui), autobrr's single-binary
qBittorrent web UI. It talks to your qui server's REST and SSE API — the same one the web
UI uses — and reproduces qui's mobile experience as a Compose app.

qui has no official iOS app; on phones it is installed as a PWA. This project is a native
rebuild of that mobile experience for Android.

> Unofficial. Not affiliated with autobrr or the qui maintainers.

## What it does

- **Multi-instance** — switch between every qBittorrent instance your qui server
  manages, or merge them all into qui's **unified** scope, where each row is labelled
  with the client it came from.
- **Live torrent list** over qui's multiplexed SSE stream (`/api/stream`), with REST
  polling as a fallback when the stream drops.
- **Three list densities** — normal, compact and ultra-compact, matching qui's mobile
  view modes, switchable straight from the list.
- **Server-side search, sorting and filtering** — all 34 sort fields and the full
  include/exclude filter set (status, category, tag, tracker) are handed to qui, so the
  results match the web UI exactly. Search keeps a local history and documents qui's
  glob and fuzzy matching.
- **Torrent actions** — resume, pause, recheck, reannounce, queue priority, category,
  tags, speed limits, share limits (ratio / seeding time / inactive seeding time), and
  delete (with or without files), individually or in bulk. Swiping a card left reveals
  resume-or-pause, recheck and delete.
- **Torrent details** — General, Trackers, Peers, Content and HTTP Sources tabs, with
  per-file priority.
- **Dashboard** — qui's global stat tiles, the tracker breakdown merged across clients,
  and per-client cards with counts, transfer totals, disk usage and the
  alternative-speed switch. Which sections appear is configurable.
- **Add torrents** — magnet links, URLs and `.torrent` files, including magnet links and
  files shared from other apps.
- **All nine qui themes** with their colour variations, light/dark/system mode, and
  optional Material You dynamic colour.
- **Nine languages** — the same set qui ships. The device language is matched
  automatically; Settings can override it.
- **Incognito mode** — swaps names, categories, tags and trackers for a deterministic
  Linux-distro vocabulary using the same hash arithmetic as qui, and masks instance
  addresses on the dashboard. Toggleable from the list, as in qui.
- **Two home-screen widgets** — see [Widgets](#widgets).
- **Tracker favicons** from qui's own icon cache.

## Widgets

Four entries appear in the launcher's widget picker. The first three share one provider
and all remain resizable — they are listed separately because most people never discover
that a widget can be dragged bigger:

- **qui speed** — 2×1, the two speeds.
- **qui overview** — 2×2, adds the header, a refresh button and the downloading/seeding
  counts.
- **qui stats** — 4×2, fills in the stopped and errored counts, total size and free space.
- **qui transfers** — 4×4, a list of whatever is transferring right now, with a progress
  bar and speed per row. With nothing active it falls back to the most recently added.
  Tapping a row opens that torrent's detail screen; the `+` opens the add sheet.

Resizing any of the first three re-picks the layout, so a *qui speed* dragged out to four
columns becomes the full stats card.

*Settings → Widgets* adds any of them to the home screen without going through the
launcher's picker, pins them to a single client instead of summing every active one, and
chooses what the transfer list leads with. Torrent names on the widgets follow the app's
incognito switch — a home screen is on show to whoever is standing nearby.

All of them follow the system light/dark setting and borrow the launcher's own corner
radius on Android 12 and above, so they sit flush with stock widgets.

Where several clients are summed, free space is the *smallest* of them — the disk that
fills up first — and is marked `≥` rather than presented as a total, since separate
machines have separate disks. The same rule applies to the torrent list's own header.

### Refreshing

Android will not run a widget's own update schedule more often than every 30 minutes,
and HyperOS, MIUI, EMUI and ColorOS stretch that further or skip it entirely. So the
periodic update is a backstop, not the mechanism. What actually keeps the widgets current:

- opening the app — every dashboard poll pushes fresh numbers to every placed widget;
- the refresh button in the widget's own header.

On a Xiaomi device, *Settings → Apps → qui → Autostart* on and *Battery saver → No
restrictions* is what lets the periodic update fire at all. Without it the widget shows
the last numbers it managed to fetch, timestamped, until you tap refresh.

There is no *超级小部件* (HyperOS's interactive widget format) build: that needs Xiaomi's
MiuiWidget SDK and an app registered through their store review. These are standard
`AppWidgetProvider` widgets, which HyperOS lists and renders normally.

## Languages

The nine locales qui supports: `en`, `cs`, `de`, `fr`, `it`, `ko`, `pt-BR`, `uk`,
`zh-CN`. Android picks the closest match to the device's languages on first launch;
*Settings → Language* overrides it.

Translations are generated by `tools/generate_translations.py`, which reuses qui's own
locale files wherever the same English string appears there — so torrent states, filter
names and actions read identically in both UIs. Only strings with no counterpart
upstream are translated in `tools/translations_overrides.json`. Regenerate with:

```bash
python tools/generate_translations.py ../qui-upstream
```

Do not edit `app/src/main/res/values-*/strings.xml` by hand; they are generated.

## Requirements

- Android 8.0 (API 26) or newer
- A reachable [qui](https://github.com/autobrr/qui) server

## Connecting

Enter your qui server address (e.g. `http://192.168.1.10:7476`) and sign in.

Two authentication methods are supported:

- **Password** — the usual qui login. The session cookie is stored on the device.
- **API key** — create one in qui under *Settings → API keys*. Keys do not expire, so
  the app stays signed in; this is the recommended option.

If your qui server has no account yet, "Test connection" detects that and the form
switches to creating the first account.

Self-signed certificates are rejected by default. The **Trust self-signed certificates**
switch disables verification for that server — only enable it for a server you control.

## Building

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. CI builds it on every push and uploads
it as an artifact.

To run the tests:

```bash
./gradlew testDebugUnitTest
```

## Releases

Pushing a `v*` tag builds a release APK and attaches it to a GitHub release:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

The release notes are the section matching that tag in [CHANGELOG.md](CHANGELOG.md) and
[CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md), published together; the app's update dialog
reads the same text and picks the half matching the device language. So write the new
version's section in both files before tagging.

### Signing

By default the release APK is signed with the Android **debug** key. It installs fine,
but that key is not yours and is the same one every SDK install ships, so treat it as a
convenience rather than a real signature.

To sign with your own key, generate a keystore and add four repository secrets. Only you
ever see the passwords:

```bash
keytool -genkeypair -v -keystore release.jks -alias qui -keyalg RSA -keysize 4096 -validity 10000
```

```bash
base64 -w0 release.jks > release.jks.base64
```

Then under *Settings → Secrets and variables → Actions*, add:

| Secret | Value |
| --- | --- |
| `QUI_KEYSTORE_BASE64` | contents of `release.jks.base64` |
| `QUI_KEYSTORE_PASSWORD` | the keystore password |
| `QUI_KEY_ALIAS` | `qui` |
| `QUI_KEY_PASSWORD` | the key password |

Keep `release.jks` backed up and out of the repository. Android identifies an app by its
signing key: lose it and you cannot ship an update over an installed copy, and switching
keys forces users to uninstall first. The release workflow prints the certificate it
actually signed with, so you can confirm which key was used.

## How it maps to qui

The app is a client for qui's API, not a reimplementation of qui:

| qui | Android |
| --- | --- |
| `GET /api/stream` (SSE) | `QuiStreamClient` — init/update/delta/heartbeat frames |
| `GET /api/instances/{id}/torrents` | `QuiRepository.torrents` |
| `GET /api/torrents/cross-instance` | the unified scope |
| `POST /api/instances/{id}/torrents/bulk-action` | `QuiRepository.bulkAction` |
| `GET /api/tracker-icons` | `TrackerIconStore` |
| `web/src/themes/*.css` | `QuiThemes.kt`, generated with OKLCH → sRGB conversion |
| `web/src/i18n/locales/*` | `values-*/strings.xml`, generated |
| `web/src/lib/utils.ts`, `speedUnits.ts` | `ui/format/Format.kt` |
| `web/src/lib/incognito.ts` | `ui/torrents/Incognito.kt` |
| `TorrentCardsMobile.tsx` | `ui/torrents/TorrentCard.kt` |
| `MobileFooterNav.tsx` | `ui/QuiApp.kt` |
| `contexts/MobileScrollContext.tsx` | `ui/MobileScroll.kt` |
| `pages/Dashboard.tsx` | `ui/dashboard/` |

Theme colours are not eyeballed: qui's CSS custom properties are parsed and the OKLCH
values converted to sRGB, so the palettes are numerically identical to the web UI.

## Translations

English lives in `app/src/main/res/values/strings.xml` and is the only file to edit by
hand. The other eight locales are generated:

```bash
python tools/generate_translations.py ../qui-upstream
```

For every English string the generator looks for the same sentence in qui's own locale
files and reuses that translation, so torrent states, filters and actions read exactly
as they do in the web UI (about three quarters of the catalogue). The rest come from
`tools/translations_overrides.json`, which is hand-written and covers the strings that
only exist in this app — the login form, the dashboard cards, and the Android-specific
settings.

`tools/check_translations.py` runs in CI and fails if a locale is missing a key or if a
translation's `%1$s` placeholders do not match the English source. It needs no qui
checkout.

Adding a language means adding it to `LANGUAGES` in the generator, `SUPPORTED_LANGUAGES`
and `LANGUAGE_NAMES` in `ui/AppLocale.kt`, and `res/xml/locales_config.xml`.

## Not implemented

qui's server-management features are deliberately out of scope — they are better suited
to the web UI, and several are premium-gated:

cross-seed, automations, RSS management, instance backups, orphan/directory scanning,
torrent creation, Jackett/Torznab indexers, *arr integration, and instance CRUD.
Manage those in qui itself; this app is a torrent client for day-to-day use.

Two limits worth knowing about:

- The **widgets** refresh on Android's own schedule, which will not go below 30
  minutes — see [Refreshing](#refreshing).
- **Dashboard section visibility** is stored on the device, not synced through qui's
  `/api/dashboard-settings`, so it does not follow you to the web UI.

## Licence

GPL-2.0-or-later, matching upstream qui. See [LICENSE](LICENSE).

The qui name and logo belong to the autobrr project. The logo is reused under the same
licence from `web/src/components/ui/Logo.tsx`.
