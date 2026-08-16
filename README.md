# qui for Android

A native Android client for [qui](https://github.com/autobrr/qui), autobrr's single-binary
qBittorrent web UI. It talks to your qui server's REST and SSE API — the same one the web
UI uses — and reproduces qui's mobile experience as a Compose app.

qui has no official iOS app; on phones it is installed as a PWA. This project is a native
rebuild of that mobile experience for Android.

> Unofficial. Not affiliated with autobrr or the qui maintainers.

## What it does

- **Multi-instance** — switch between every qBittorrent instance your qui server manages.
- **Live torrent list** over qui's multiplexed SSE stream (`/api/stream`), with REST
  polling as a fallback when the stream drops.
- **Three list densities** — normal, compact and ultra-compact, matching qui's mobile
  view modes.
- **Server-side search, sorting and filtering** — all 34 sort fields and the full
  include/exclude filter set (status, category, tag, tracker) are handed to qui, so the
  results match the web UI exactly.
- **Torrent actions** — resume, pause, recheck, reannounce, queue priority, category,
  tags, speed limits, and delete (with or without files), individually or in bulk.
- **Torrent details** — General, Trackers, Peers, Content and HTTP Sources tabs, with
  per-file priority.
- **Add torrents** — magnet links, URLs and `.torrent` files, including magnet links and
  files shared from other apps.
- **All nine qui themes** with their colour variations, light/dark/system mode, and
  optional Material You dynamic colour.
- **Incognito mode** — swaps names, categories, tags and trackers for a deterministic
  Linux-distro vocabulary, using the same hash arithmetic as qui, so a given torrent
  shows the same alias in both clients.

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
| `POST /api/instances/{id}/torrents/bulk-action` | `QuiRepository.bulkAction` |
| `web/src/themes/*.css` | `QuiThemes.kt`, generated with OKLCH → sRGB conversion |
| `web/src/lib/utils.ts`, `speedUnits.ts` | `ui/format/Format.kt` |
| `web/src/lib/incognito.ts` | `ui/torrents/Incognito.kt` |
| `TorrentCardsMobile.tsx` | `ui/torrents/TorrentCard.kt` |

Theme colours are not eyeballed: qui's CSS custom properties are parsed and the OKLCH
values converted to sRGB, so the palettes are numerically identical to the web UI.

## Not implemented

qui's server-management features are deliberately out of scope — they are better suited
to the web UI, and several are premium-gated:

cross-seed, automations, RSS management, instance backups, orphan/directory scanning,
torrent creation, Jackett/Torznab indexers, *arr integration, and instance CRUD.
Manage those in qui itself; this app is a torrent client for day-to-day use.

## Licence

GPL-2.0-or-later, matching upstream qui. See [LICENSE](LICENSE).

The qui name and logo belong to the autobrr project. The logo is reused under the same
licence from `web/src/components/ui/Logo.tsx`.
