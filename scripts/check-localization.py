#!/usr/bin/env python3
"""Check bundled language coverage and format contracts without Android or network access."""
from collections import Counter
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
PLACEHOLDER = re.compile(r"%\d+\$[sd]|%%")
TOKEN = re.compile(r"SVAX|SVA\d|SVANEWLINE")


def read(path):
    strings, plurals = {}, {}
    for node in ET.parse(path).getroot():
        if node.get("translatable") == "false":
            continue
        name = node.get("name")
        if node.tag == "string":
            if name in strings:
                raise ValueError(f"Duplicate string: {path}:{name}")
            strings[name] = node.text or ""
        elif node.tag == "plurals":
            plurals[name] = {item.get("quantity"): item.text or "" for item in node}
    return strings, plurals


def main():
    defaults, default_plurals = read(RES / "values/strings.xml")
    tags = [node.get(ANDROID + "name") for node in ET.parse(RES / "xml/locales_config.xml").getroot()]
    errors = []
    if len(tags) != 23 or len(set(tags)) != len(tags):
        errors.append("Expected 23 unique supported languages")
    for tag in tags:
        if tag == "en":
            continue
        qualifier = {"id": "in", "zh-Hans": "b+zh+Hans", "zh-Hant": "b+zh+Hant"}.get(tag, tag)
        path = RES / f"values-{qualifier}/strings.xml"
        if not path.exists():
            errors.append(f"Missing language file: {tag}")
            continue
        strings, plurals = read(path)
        for key in sorted(set(defaults) - set(strings)):
            errors.append(f"{tag}: missing string {key}")
        for key in sorted(set(strings) - set(defaults)):
            errors.append(f"{tag}: unknown string {key}")
        for key in set(defaults) & set(strings):
            value = strings[key]
            if not value.strip('" '):
                errors.append(f"{tag}: empty string {key}")
            if TOKEN.search(value):
                errors.append(f"{tag}: unresolved translation token in {key}")
            if Counter(PLACEHOLDER.findall(value)) != Counter(PLACEHOLDER.findall(defaults[key])):
                errors.append(f"{tag}: mismatched placeholders in {key}")
            if value.count(r"\n") != defaults[key].count(r"\n"):
                errors.append(f"{tag}: changed line breaks or merged labels in {key}")
        for key, quantities in default_plurals.items():
            localized = plurals.get(key, {})
            if "other" not in localized:
                errors.append(f"{tag}: missing plural {key}/other")
            expected = Counter(PLACEHOLDER.findall(quantities["other"]))
            for quantity, value in localized.items():
                if Counter(PLACEHOLDER.findall(value)) != expected or TOKEN.search(value):
                    errors.append(f"{tag}: invalid plural {key}/{quantity}")
    for error in errors:
        print(error)
    print(f"Localization: {len(tags)} languages, {len(defaults)} strings, {len(default_plurals)} plurals; {len(errors)} error(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
