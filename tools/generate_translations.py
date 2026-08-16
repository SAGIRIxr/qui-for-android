#!/usr/bin/env python3
"""
Generate values-*/strings.xml for every language qui ships.

English is the source of truth (app/src/main/res/values/strings.xml). For each
string we try, in order:

  1. tools/translations_overrides.json  - hand-written, wins over everything
  2. QUI_KEY_MAP below                  - an explicit qui i18n key
  3. an exact English value match        - the same sentence somewhere in qui's
                                          English locale files

Reusing qui's own translations keeps the Android wording identical to the web UI
for the terms that matter (states, actions, filters), and means the eight
non-English locales are as good as upstream's rather than a fresh guess.

Usage: python tools/generate_translations.py <path-to-qui-checkout> [--report]
"""

from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path
from xml.etree import ElementTree

# qui's supported languages; the second element is the Android resource
# qualifier for that language tag.
LANGUAGES = [
    ("cs", "values-cs"),
    ("de", "values-de"),
    ("fr", "values-fr"),
    ("it", "values-it"),
    ("ko", "values-ko"),
    ("pt-BR", "values-b+pt+BR"),
    ("uk", "values-uk"),
    ("zh-CN", "values-zh-rCN"),
]

NAMESPACES = [
    "common", "auth", "settings", "torrents",
    "dashboard", "instances", "search", "rss",
]

# Android key -> "namespace:dotted.key" in qui's locale files. Only needed where
# the English text is ambiguous (several qui keys share it) or differs slightly.
QUI_KEY_MAP = {
    "nav_dashboard": "common:mobileNav.dashboard",
    "nav_clients": "common:mobileNav.clients",
    "nav_settings": "common:mobileNav.settings",
    "nav_no_active_clients": "common:mobileNav.noActiveClients",
    "nav_loading": "common:mobileNav.loading",
    "instances_title": "common:mobileNav.qbittorrentClients",
    "settings_sign_out": "common:mobileNav.logout",
    "settings_appearance": "common:themeToggle.appearance",
    "settings_mode": "common:themeToggle.mode",
    "settings_mode_light": "common:themeToggle.light",
    "settings_mode_dark": "common:themeToggle.dark",
    "settings_mode_system": "common:themeToggle.system",
    "settings_theme": "common:themeToggle.theme",
    "settings_view_mode": "torrents:filterSidebar.viewMode",
    "settings_view_mode_normal": "torrents:filterSidebar.viewModeNormal",
    "settings_view_mode_compact": "torrents:filterSidebar.viewModeCompact",
    "settings_view_mode_ultra": "torrents:filterSidebar.viewModeUltra",
    "filters_status": "torrents:filterSidebar.status",
    "filters_categories": "torrents:filterSidebar.categories",
    "filters_tags": "torrents:filterSidebar.tags",
    "filters_trackers": "torrents:filterSidebar.trackers",
    "filters_uncategorized": "torrents:filterSidebar.uncategorized",
    "filters_untagged": "torrents:filterSidebar.untagged",
    "torrents_ratio": "torrents:mobileCards.ratio",
    "detail_ratio": "torrents:mobileCards.ratio",
    "sort_title": "torrents:mobileCards.sortBy",
    "sort_ascending": "torrents:sort.ascending",
    "sort_descending": "torrents:sort.descending",
    "selection_select_all": "torrents:detailsPanel.selectAll",
    "detail_no_files": "torrents:detailsPanel.emptyStates.noFilesFound",
    "status_stalled_uploading": "torrents:filterSidebar.states.stalledUp",
    "status_stalled_downloading": "torrents:filterSidebar.states.stalledDown",
    "status_errored": "torrents:filterSidebar.states.errored",
    "status_unregistered": "torrents:filterSidebar.states.unregistered",
    "status_tracker_down": "torrents:filterSidebar.states.trackerDown",
    "status_tracker_error": "torrents:filterSidebar.states.trackerError",
}

# Android state_* / status_* keys resolve to torrents:stateLabels.<suffix>.
for _suffix in [
    "downloading", "metaDL", "allocating", "stalledDL", "queuedDL", "checkingDL",
    "forcedDL", "uploading", "stalledUP", "queuedUP", "checkingUP", "forcedUP",
    "pausedDL", "pausedUP", "stoppedDL", "stoppedUP", "error", "missingFiles",
    "checkingResumeData", "moving",
]:
    QUI_KEY_MAP.setdefault(f"state_{_suffix}", f"torrents:stateLabels.{_suffix}")

for _suffix in [
    "downloading", "uploading", "completed", "stopped", "active", "inactive",
    "running", "stalled", "checking", "moving",
]:
    QUI_KEY_MAP.setdefault(f"status_{_suffix}", f"torrents:filterSidebar.states.{_suffix}")


def flatten(obj, prefix=""):
    """Flatten a nested locale dict into {dotted.key: value}."""
    out = {}
    if isinstance(obj, dict):
        for key, value in obj.items():
            out.update(flatten(value, f"{prefix}.{key}" if prefix else key))
    elif isinstance(obj, str):
        out[prefix] = obj
    return out


def load_locale(root: Path, lang: str) -> dict[str, str]:
    """All strings for one language, keyed as "namespace:dotted.key"."""
    strings = {}
    for ns in NAMESPACES:
        path = root / "web" / "src" / "i18n" / "locales" / lang / f"{ns}.json"
        if not path.exists():
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, value in flatten(data).items():
            strings[f"{ns}:{key}"] = value
    return strings


def normalise(text: str) -> str:
    """Fold a string for value matching: case, whitespace and typography."""
    text = unicodedata.normalize("NFKC", text)
    text = text.replace("’", "'").replace("‘", "'")
    text = text.replace("—", "-").replace("–", "-")
    text = re.sub(r"\s+", " ", text)
    return text.strip().lower().rstrip(".:")


def xml_escape(text: str) -> str:
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    # Android needs apostrophes and unpaired quotes escaped inside a resource.
    text = text.replace("'", "\\'").replace('"', '\\"')
    return text


def read_source(res_dir: Path):
    """Parse values/strings.xml, keeping plurals distinct from plain strings."""
    tree = ElementTree.parse(res_dir / "values" / "strings.xml")
    root = tree.getroot()

    plain, plurals, untranslatable = {}, {}, set()
    for node in root:
        name = node.get("name")
        if node.tag == "string":
            if node.get("translatable") == "false":
                untranslatable.add(name)
                continue
            plain[name] = "".join(node.itertext())
        elif node.tag == "plurals":
            plurals[name] = {
                item.get("quantity"): "".join(item.itertext())
                for item in node.findall("item")
            }
    return plain, plurals, untranslatable


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    qui_root = Path(sys.argv[1]).resolve()
    report_only = "--report" in sys.argv
    project = Path(__file__).resolve().parent.parent
    res_dir = project / "app" / "src" / "main" / "res"

    plain, plurals, _ = read_source(res_dir)

    overrides_path = project / "tools" / "translations_overrides.json"
    overrides = {}
    if overrides_path.exists():
        overrides = json.loads(overrides_path.read_text(encoding="utf-8"))

    english = load_locale(qui_root, "en")
    if not english:
        print(f"No qui English locale found under {qui_root}")
        return 1

    # English value -> qui key, but only where the value is unambiguous.
    by_value: dict[str, str | None] = {}
    for key, value in english.items():
        folded = normalise(value)
        if folded in by_value and by_value[folded] != key:
            existing = english.get(by_value[folded] or "", None)
            # Two different keys with the same English text translate the same
            # way in practice, so keep the first rather than dropping both.
            if existing is not None and normalise(existing) == folded:
                continue
        by_value.setdefault(folded, key)

    def resolve(android_key: str, english_text: str, lang_strings: dict[str, str]):
        mapped = QUI_KEY_MAP.get(android_key)
        if mapped and mapped in lang_strings:
            return lang_strings[mapped], "mapped"
        hit = by_value.get(normalise(english_text))
        if hit and hit in lang_strings:
            return lang_strings[hit], "matched"
        return None, "missing"

    stats = {}
    missing_keys: set[str] = set()

    for lang, qualifier in LANGUAGES:
        lang_strings = load_locale(qui_root, lang)
        lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            "<!--",
            "  Copyright (c) 2026 qui-android contributors",
            "  SPDX-License-Identifier: GPL-2.0-or-later",
            "",
            "  Generated by tools/generate_translations.py - do not edit by hand.",
            f"  Language: {lang}",
            "-->",
            "<resources>",
        ]
        counts = {"override": 0, "mapped": 0, "matched": 0, "missing": 0}

        for name, text in plain.items():
            value = overrides.get(name, {}).get(lang)
            source = "override"
            if value is None:
                value, source = resolve(name, text, lang_strings)
            counts[source] += 1
            if value is None:
                missing_keys.add(name)
                continue
            lines.append(f'    <string name="{name}">{xml_escape(value)}</string>')

        for name, forms in plurals.items():
            translated = overrides.get(name, {}).get(lang)
            if not isinstance(translated, dict):
                missing_keys.add(name)
                counts["missing"] += 1
                continue
            counts["override"] += 1
            lines.append(f'    <plurals name="{name}">')
            for quantity, value in translated.items():
                lines.append(
                    f'        <item quantity="{quantity}">{xml_escape(value)}</item>'
                )
            lines.append("    </plurals>")

        lines.append("</resources>")
        stats[lang] = counts

        if not report_only:
            out_dir = res_dir / qualifier
            out_dir.mkdir(parents=True, exist_ok=True)
            (out_dir / "strings.xml").write_text(
                "\n".join(lines) + "\n", encoding="utf-8"
            )

    total = len(plain) + len(plurals)
    print(f"{total} source strings\n")
    for lang, counts in stats.items():
        covered = total - counts["missing"]
        print(
            f"  {lang:6s} {covered:3d}/{total}  "
            f"(override {counts['override']}, qui-key {counts['mapped']}, "
            f"qui-text {counts['matched']})"
        )

    if missing_keys:
        print(f"\n{len(missing_keys)} key(s) with no translation source:")
        for key in sorted(missing_keys):
            print(f"  {key}")
        print("\nAdd them to tools/translations_overrides.json.")
        return 1

    print("\nAll strings translated.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
