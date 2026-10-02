#!/usr/bin/env python3
"""
Fail if a locale has drifted from the English source.

Adding a string to values/strings.xml and forgetting to re-run
generate_translations.py leaves that string English in translated locales,
which nothing else catches. This needs no qui checkout, so CI can run
it on every push.

Usage: python tools/check_translations.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path
from xml.etree import ElementTree

from generate_translations import LANGUAGES

FORMAT_ARG = re.compile(r"%\d+\$[sd]")


def read(path: Path):
    root = ElementTree.parse(path).getroot()
    strings, plurals = {}, {}
    for node in root:
        name = node.get("name")
        if node.tag == "string":
            if node.get("translatable") == "false":
                continue
            strings[name] = "".join(node.itertext())
        elif node.tag == "plurals":
            plurals[name] = {
                item.get("quantity"): "".join(item.itertext())
                for item in node.findall("item")
            }
    return strings, plurals


def main() -> int:
    res = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
    base_strings, base_plurals = read(res / "values" / "strings.xml")

    problems: list[str] = []
    # values-night and friends are qualifier folders without translations.
    locales = sorted(d for d in res.glob("values-*") if (d / "strings.xml").exists())
    if not locales:
        print("No translated locales found.")
        return 1

    expected_dirs = {qualifier for _, qualifier in LANGUAGES}
    actual_dirs = {directory.name for directory in locales}
    for missing in sorted(expected_dirs - actual_dirs):
        problems.append(f"{missing}: missing translated strings.xml")
    for extra in sorted(actual_dirs - expected_dirs):
        problems.append(f"{extra}: locale not registered in the translation generator")

    expected_tags = {"en", *(lang for lang, _ in LANGUAGES)}
    locale_config = ElementTree.parse(res / "xml" / "locales_config.xml").getroot()
    registered_tags = {
        node.get("{http://schemas.android.com/apk/res/android}name")
        for node in locale_config.findall("locale")
    }
    if registered_tags != expected_tags:
        problems.append("locales_config.xml: languages differ from the translation generator")

    for locale_dir in locales:
        strings, plurals = read(locale_dir / "strings.xml")
        name = locale_dir.name

        for key, english in base_strings.items():
            if key not in strings:
                problems.append(f"{name}: missing string/{key}")
                continue
            # A translation that drops or invents a %1$s crashes at format time.
            if sorted(FORMAT_ARG.findall(english)) != sorted(FORMAT_ARG.findall(strings[key])):
                problems.append(f"{name}: format arguments differ for string/{key}")

        for key, english_forms in base_plurals.items():
            if key not in plurals:
                problems.append(f"{name}: missing plurals/{key}")
                continue
            if "other" not in plurals[key]:
                problems.append(f"{name}: plurals/{key} has no 'other' quantity")
            for quantity, translated in plurals[key].items():
                english = english_forms.get(quantity, english_forms["other"])
                if sorted(FORMAT_ARG.findall(english)) != sorted(FORMAT_ARG.findall(translated)):
                    problems.append(f"{name}: format arguments differ for plurals/{key}/{quantity}")

        for key in strings.keys() - base_strings.keys():
            problems.append(f"{name}: string/{key} is not in the English source")
        for key in plurals.keys() - base_plurals.keys():
            problems.append(f"{name}: plurals/{key} is not in the English source")

    if problems:
        print(f"{len(problems)} problem(s):")
        for problem in problems:
            print(f"  {problem}")
        print("\nRun: python tools/generate_translations.py <path-to-qui-checkout>")
        return 1

    total = len(base_strings) + len(base_plurals)
    print(f"{len(locales)} locales, {total} strings each - all present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
