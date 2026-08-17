# Changelog

**English** · [简体中文](CHANGELOG.zh-CN.md)

The section for a tag becomes that release's notes on GitHub, and the app shows the
same text in its update dialog. Add the new version at the top before tagging, in this
file and in [CHANGELOG.zh-CN.md](CHANGELOG.zh-CN.md).

## 0.4.5

### Added
- *Settings → Widgets → Background refresh*: off, 15, 30 or 60 minutes. The platform's
  own widget schedule stops at 30 minutes and cannot go lower, so the widgets no longer
  use it — `updatePeriodMillis` is 0 and a WorkManager job drives them, with 15 minutes
  the floor Android enforces on periodic background work.
- The widgets say why a refresh failed — no network, credentials rejected, server timed
  out, HTTP 502 — instead of reporting everything as an unreachable server.
- Tapping refresh paints a *Refreshing…* state straight away, so the button visibly does
  something.

### Fixed
- After a long spell with the app closed, the widgets reported the server as unreachable
  and the refresh button never helped. The fetch ran inside the broadcast receiver,
  which has about ten seconds before the system counts it as hung — not enough for a
  cold process to build its dependency graph, read DataStore, resolve DNS, complete a
  TLS handshake and then make the request. It now runs in a worker with no deadline,
  which waits for connectivity rather than failing without it and retries once before
  showing an error.
- Two widgets refreshing at once could time each other out: the second one's budget was
  spent waiting for the first one's fetch. It now reuses the result instead.
- The background schedule stops when the last widget is removed from the home screen.

## 0.4.4

### Added
- Simplified Chinese README and changelog. GitHub releases now carry both languages,
  and the app's update dialog shows the half matching the device language.

## 0.4.3

### Added
- A warning strip on the torrent list when a merged response is missing a client that
  failed to answer. Until now that list was silently short.
- The app checks for a newer release when it starts and offers the release notes, with
  *Skip this version* to silence one release and a *Check on launch* switch in
  *Settings → About* to turn the whole thing off.
- *Settings → About* shows the release notes for an available update instead of only
  its version number.

### Fixed
- Adding a widget from *Settings → Widgets* did nothing on launchers that withhold the
  "create home screen shortcut" permission — MIUI and HyperOS grant it separately, and
  the request is dropped silently. The app now waits for the launcher to confirm the
  placement and, if nothing comes back, explains where the permission lives and offers
  the long-press route instead.
- Releases on GitHub carried no notes at all, because the automatic generator lists
  merged pull requests and this repository has none.

## 0.4.2

### Fixed
- The unified view showed thousands of torrents in its header and none in the list.
  qui serialises cross-instance metadata as `instance_id` / `instance_name`, and the
  app only read the camelCase spelling, so every merged row carried a null instance id.
  That changed each row's key from `<instanceId>:<hash>` to a bare hash, so the first
  stream frame that reordered the page matched nothing and cleared the list. The same
  null sent row taps to whichever client happened to be selected and left bulk actions
  in the unified view with no targets at all.
- Signing in with an API key always failed. The key was validated against
  `/api/auth/me`, which answers from the session alone and returns 401 for a perfectly
  good key.
- The torrent list froze after a spell in the background until you switched client. The
  stream had no read timeout, so a socket dropped while the process was frozen never
  threw and the retry loop never ran.

### Changed
- Free space in the unified view waits for the list to arrive, holds its answer for a
  minute, and no longer competes with the first page for the server's attention.
- The dashboard and detail pollers pause while the app is in the background.

## 0.4.1

### Fixed
- Free space in the unified view reported one client's disk. A merged list has no
  single server behind it; the header now shows the smallest of the clients' free
  space, marked `≥`, and expands on tap into a per-client breakdown.

### Added
- *Settings → Widgets*: add any widget to the home screen without the launcher's
  picker, pin the widgets to one client, and choose what the transfer list leads with.
- The stats widget is offered at three sizes in the picker (2×1, 2×2, 4×2) rather than
  one resizable entry.
- Widget torrent names follow the app's incognito switch.

## 0.4.0

### Added
- Two home-screen widgets, replacing the single fixed card: a resizable stats widget
  and a 4×4 list of what is transferring, with progress bars and per-row taps that open
  that torrent.

## 0.3.1

### Fixed
- The nav bar kept showing the previously selected client while the list was unified.
- Ultra-compact rows: the swipe action circles spilled out below the card.
- A swiped-open row now closes when you tap anywhere else or scroll.
- The torrent detail actions were icon-only and now carry labels.
- The tracker breakdown gained a header row.

## 0.3.0

### Added
- Unified cross-client view, share limits, a search sheet with qui's glob and fuzzy
  syntax, swipe actions on the cards, scroll-hiding bars with back-to-top, update
  checking, cache clearing, and the first home-screen widget.

## 0.2.0

### Added
- All nine languages qui ships, matched to the device locale, with an override in
  Settings.
- Dashboard tracker breakdown and global stat cards, the IP-hiding toggle, and
  configurable sections.

## 0.1.0

First release: torrent list, filters, sorting, detail screen, add torrents, all nine
qui themes, and incognito mode.
