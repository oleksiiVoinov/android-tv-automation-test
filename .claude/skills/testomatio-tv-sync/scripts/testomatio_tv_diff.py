#!/usr/bin/env python3
"""
Compare the automated Android TV tests in this repository with
src/main/resources/testomatio-mapping.json and report what has to change in Testomat.io.

Project android-f0d8b, TV root suite 1882fcfb ("TV Regression (Auto and Manual)").

Usage (from the repository root of android-tv-automation-test):
    python3 .claude/skills/testomatio-tv-sync/scripts/testomatio_tv_diff.py
    python3 .claude/skills/testomatio-tv-sync/scripts/testomatio_tv_diff.py --json

Exit code is always 0 - this is a report, not a gate.

What it detects:
  NEW            test method in the code that has no case in the mapping
  REMOVED        mapping entry whose test method no longer exists in the code
  MOVED          @Feature / @Story changed, so the case sits in the wrong suite
  DATA_VALUES    per-protocol cases drifted from the protocol grid of the TV build
  ENUM_DRIFT     Protocols enum holds a constant that is neither in the known grid nor in the mapping
  NO_STORY       test without @Story - must be fixed in the code, see the skill
  TITLE_REVIEW   Objective in @Description no longer resembles the case title (soft signal)
"""

import argparse
import json
import os
import re
import sys

REPO = os.getcwd()
TEST_ROOT = os.path.join(REPO, "src/test/java")
MAIN_ROOT = os.path.join(REPO, "src/main/java")
MAPPING = os.path.join(REPO, "src/main/resources/testomatio-mapping.json")
SUITE_XMLS = ["regression.xml"]
SUITE_DIR = os.path.join(TEST_ROOT, "apps/tv/regression")

# the TV suite XML is the single source of truth - nothing is reported from outside it
EXTRA_CLASSES = []

# Classes whose @Feature is absent on purpose (Allure promotes @Story to Feature level, so the
# Testomat.io tree keeps them as file suites right under the TV root) are detected automatically -
# see collect_code_tests(). Listing them here only pins the intent.
STORY_AS_FEATURE = {
    "apps.tv.regression.MainScreenPageTest",
    "apps.tv.regression.ProtocolsTest",
    "apps.tv.regression.ServerListTest",
}

# Protocols the TV grid actually exposes (verified on the Google TV Streamer, 17.08.2026).
# ProtocolsTest reads its rows from the SCREEN (getProtocols() minus Auto), not from the enum, so the
# enum is NOT the expected set: it also holds constants the build never shows. Keys are the enum
# constant names, because the listener looks a row up by String.valueOf(protocol).
GRID_PROTOCOLS = ["IKEv2", "Super", "OpenVPNTCP", "OpenVPNUDP", "V2Ray"]


# ---------------------------------------------------------------- parsing


def classes_from_xml(path):
    txt = open(path, encoding="utf-8").read()
    out = []
    for tblock in re.finditer(r'<test name="([^"]*)">(.*?)</test>', txt, re.S):
        for c in re.finditer(r'<class name="([^"]*)"', tblock.group(2)):
            out.append({"suite_file": os.path.basename(path),
                        "test_group": tblock.group(1),
                        "fqcn": c.group(1)})
    return out


def read_class(fqcn):
    p = os.path.join(TEST_ROOT, fqcn.replace(".", "/") + ".java")
    return open(p, encoding="utf-8").read() if os.path.exists(p) else None


def string_arg(block, name):
    m = re.search(r"@" + name + r'\(\s*"((?:[^"\\]|\\.)*)"\s*\)', block)
    return m.group(1) if m else None


def text_block(block, name):
    m = re.search(r"@" + name + r'\(\s*"""(.*?)"""\s*\)', block, re.S)
    if m:
        return "\n".join(l.strip() for l in m.group(1).strip("\n").split("\n")).strip()
    m = re.search(r"@" + name + r'\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', block, re.S)
    if m:
        return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))).replace("\\n", "\n").strip()
    return None


def mask_strings(src):
    """
    Replace every string literal and text block with a placeholder.

    Annotation values legitimately contain semicolons, closing brackets and newlines
    (an @Description text block with a numbered step list), which used to break the annotation
    regex and silently drop whole test methods. Masking first makes the structural parsing
    reliable; values are restored per annotation block afterwards.
    """
    strings = []

    def take(match):
        strings.append(match.group(0))
        return f'"__STR{len(strings) - 1}__"'

    masked = re.sub(r'"""(?:.|\n)*?"""', take, src)
    # skip the placeholders produced above, otherwise they get masked again and unmasking
    # would only peel one layer
    masked = re.sub(r'(?<!")"(?!__STR\d+__")(?:[^"\\\n]|\\.)*"(?!")', take, masked)
    return masked, strings


def unmask(text, strings):
    # a placeholder may expand into text that itself contains a placeholder, so repeat until stable
    for _ in range(5):
        restored = re.sub(r'"__STR(\d+)__"', lambda m: strings[int(m.group(1))], text)
        if restored == text:
            return restored
        text = restored
    return text


def strip_comments(masked):
    """
    Drop block and line comments. Must run AFTER mask_strings, otherwise a '//' inside a string
    literal would eat the rest of the line. Commented-out tests must not be reported as NEW
    (ServerListTest#selectServer is commented out on purpose).
    """
    masked = re.sub(r"/\*.*?\*/", "", masked, flags=re.S)
    return re.sub(r"//[^\n]*", "", masked)


CLASS_DECL = re.compile(r"^\s*(?:public\s+|final\s+|abstract\s+)*class\s+(\w+)")
METHOD_DECL = re.compile(
    r"^\s*(?:public|protected|private)\s+(?:static\s+|final\s+|synchronized\s+)*"
    r"[\w<>\[\], .]+\s+(\w+)\s*\(([^)]*)\)\s*(?:throws [\w., ]+\s*)?\{")


def annotations_above(lines, index):
    """
    The annotation block that belongs to the declaration on line `index`, collected by walking
    BACKWARDS line by line.

    Why not one big regex: an annotation argument may contain anything, so a regex like
    @Feature\(...\) happily swallows the class declaration and the annotations of the first method
    when no semicolon separates them - the class @Feature then leaks into the method (or, when a
    field does separate them, silently disappears). Walking back stops at the first line that is
    neither an annotation nor its continuation, which cannot leak. Strings are already masked, so an
    annotation is normally a single line.
    """
    collected, pending, i = [], "", index - 1
    while i >= 0:
        stripped = lines[i].strip()
        if not stripped:
            i -= 1
            continue
        candidate = (stripped + " " + pending).strip() if pending else stripped
        if stripped.startswith("@") and candidate.count("(") == candidate.count(")"):
            collected.append(candidate)
            pending = ""
            i -= 1
            continue
        if candidate.count(")") > candidate.count("("):
            # continuation line of a multi-line annotation
            pending = candidate
            i -= 1
            continue
        break
    return "\n".join(reversed(collected))


def annotation_values(block, strings):
    ann = unmask(block, strings)
    sev = re.search(r"@Severity\(\s*SeverityLevel\.(\w+)", ann)
    desc_attr = re.search(r'description\s*=\s*"((?:[^"\\]|\\.)*)"', ann)
    dp = re.search(r'dataProvider\s*=\s*"([^"]*)"', ann)
    return {
        "feature": string_arg(ann, "Feature"),
        "story": string_arg(ann, "Story"),
        "severity": sev.group(1) if sev else None,
        "description": text_block(ann, "Description"),
        "test_description": desc_attr.group(1) if desc_attr else None,
        "data_provider": dp.group(1) if dp else None,
        "enabled": not re.search(r"enabled\s*=\s*false", ann),
        "is_test": bool(re.search(r"@Test\b", ann)),
    }


def parse_class(fqcn, src):
    """
    Returns (methods, class_level) - class_level holds the @Feature / @Story put on the class itself,
    which is where the TV suite keeps them.
    """
    masked, strings = mask_strings(src)
    masked = strip_comments(masked)
    lines = masked.split("\n")

    class_level = {"feature": None, "story": None}
    for i, line in enumerate(lines):
        if CLASS_DECL.match(line):
            class_level = annotation_values(annotations_above(lines, i), strings)
            break

    methods = []
    for i, line in enumerate(lines):
        m = METHOD_DECL.match(line)
        if not m:
            continue
        values = annotation_values(annotations_above(lines, i), strings)
        if not values["is_test"]:
            continue
        params = m.group(2)
        methods.append({**values,
                        "method": m.group(1),
                        "params": len([p for p in params.split(",") if p.strip()])})
    return methods, class_level


def objective(description):
    if not description:
        return None
    m = re.search(r"Objective:\s*(.+?)(?:\n\s*\n|\nSteps:|\nPre-cond|$)", description, re.S)
    return " ".join(m.group(1).split()) if m else None


def collect_code_tests():
    entries = []
    for xml in SUITE_XMLS:
        path = os.path.join(SUITE_DIR, xml)
        if os.path.exists(path):
            entries += classes_from_xml(path)
    entries += [{"suite_file": "-", "test_group": "-", "fqcn": f} for f in EXTRA_CLASSES]

    # A class may legitimately appear in several suite files (device1 + device2). Parsing it twice
    # would multiply every finding, so keep one entry per class and remember where it came from.
    # A class listed twice inside the *same* file is a config mistake worth surfacing.
    unique, xml_duplicates = {}, []
    for e in entries:
        seen = unique.get(e["fqcn"])
        if seen is None:
            unique[e["fqcn"]] = dict(e)
        elif e["suite_file"] not in seen["suite_file"]:
            seen["suite_file"] += " + " + e["suite_file"]
        else:
            xml_duplicates.append(f'{e["fqcn"]} is listed twice in {e["suite_file"]}')
    entries = list(unique.values())

    tests, missing_files = [], []
    for e in entries:
        src = read_class(e["fqcn"])
        if src is None:
            missing_files.append(e["fqcn"])
            continue
        methods, class_level = parse_class(e["fqcn"], src)
        for m in methods:
            # a method annotation wins over the class annotation (Allure resolves it the same way)
            feature = m["feature"] or class_level["feature"]
            story = m["story"] or class_level["story"]
            # Allure promotes @Story to Feature level when @Feature is absent
            if e["fqcn"] in STORY_AS_FEATURE or (feature is None and story):
                feature, story = story, story
            tests.append({**e, **m, "feature": feature, "story": story,
                          "key": f'{e["fqcn"]}#{m["method"]}/{m["params"]}'})
    return tests, missing_files, xml_duplicates


def enum_protocols():
    """All values of the TV Protocols enum, Auto included."""
    path = os.path.join(MAIN_ROOT, "apps/tv/pages/Protocols.java")
    if not os.path.exists(path):
        return None
    body = open(path, encoding="utf-8").read()
    inner = re.search(r"enum\s+Protocols\s*\{(.*?)\}", body, re.S)
    if not inner:
        return None
    # constants end at the first ';' - everything after it is javadoc / helper methods.
    # each constant may carry a label: IKEv2("IKEv2") -> keep the constant name only
    block = re.sub(r"//.*", "", inner.group(1).split(";")[0])
    out = []
    for raw in block.split(","):
        name = raw.strip().split("(")[0].strip()
        if name:
            out.append(name)
    return out


def filters_out_auto(fqcn):
    """
    True when the class actively drops Auto from the data provider, i.e. it contains a live
    (not commented out) `removeIf(... Auto)` call. TV ProtocolsTest does.
    """
    src = read_class(fqcn)
    if src is None:
        return True
    for line in src.split("\n"):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            continue
        if "removeIf" in stripped and "Auto" in stripped:
            return True
    return False


def expected_protocols(fqcn):
    """
    What the per-value cases should cover: the grid protocols, plus Auto when the class does NOT
    filter it out. The grid list is maintained by hand (GRID_PROTOCOLS) because the real set only
    exists on the screen of the box.
    """
    expected = list(GRID_PROTOCOLS)
    if not filters_out_auto(fqcn):
        expected = ["Auto"] + expected
    return expected


# ---------------------------------------------------------------- diff


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", action="store_true", help="machine readable output")
    args = ap.parse_args()

    if not os.path.exists(MAPPING):
        print(f"mapping not found: {MAPPING}", file=sys.stderr)
        return 0

    mapping = json.load(open(MAPPING, encoding="utf-8"))
    plain = mapping.get("tests", {})
    param = mapping.get("parametrized", {})
    tests, missing_files, xml_duplicates = collect_code_tests()

    report = {"new": [], "removed": [], "moved": [], "data_values": [],
              "no_story": [], "title_review": [], "enum_drift": [],
              "missing_files": missing_files,
              "xml_duplicates": xml_duplicates, "totals": {}}

    code_keys = {t["key"] for t in tests}
    for t in tests:
        entry = plain.get(t["key"])
        values = param.get(t["key"])
        if entry is None and values is None:
            report["new"].append({"key": t["key"], "feature": t["feature"], "story": t["story"],
                                  "severity": t["severity"], "data_provider": t["data_provider"],
                                  "objective": objective(t["description"]),
                                  "suite_file": t["suite_file"], "test_group": t["test_group"],
                                  "enabled": t["enabled"]})
            continue

        if not t["story"]:
            report["no_story"].append(t["key"])

        targets = [entry] if entry else list(values.values())
        for target in targets:
            if t["feature"] and target.get("feature") and target["feature"] != t["feature"]:
                report["moved"].append({"key": t["key"], "id": target.get("id"),
                                        "mapping_feature": target.get("feature"), "code_feature": t["feature"]})
                break
            if t["story"] and target.get("suite_title") and target["suite_title"] != t["story"]:
                report["moved"].append({"key": t["key"], "id": target.get("id"),
                                        "mapping_story": target.get("suite_title"), "code_story": t["story"]})
                break

        if entry:
            obj = objective(t["description"]) or t["test_description"]
            title = entry.get("title") or ""
            if obj:
                norm = lambda s: re.sub(r"[^a-z0-9 ]", "", s.lower())
                a, b = norm(obj), norm(title)
                overlap = len(set(a.split()) & set(b.split()))
                if overlap < max(2, min(len(a.split()), len(b.split())) // 3):
                    report["title_review"].append({"key": t["key"], "id": entry.get("id"),
                                                   "case_title": title, "code_objective": obj})

    for key in list(plain) + list(param):
        if key not in code_keys:
            ids = ([plain[key]["id"]] if key in plain
                   else [v.get("id") for v in param.get(key, {}).values()])
            report["removed"].append({"key": key, "ids": ids})

    enum_values = enum_protocols() or []
    for key, values in param.items():
        expected = None
        if "ProtocolsTest" in key:
            expected = expected_protocols(key.split("#")[0])
        if expected and set(expected) != set(values):
            report["data_values"].append({"key": key, "in_mapping": sorted(values),
                                          "in_code": expected,
                                          "missing_cases": sorted(set(expected) - set(values)),
                                          "stale_cases": sorted(set(values) - set(expected))})

    # enum constants that are neither in the known grid nor already mapped: the build may have
    # started showing them (then add a case), or they may be dead values in the enum
    mapped_values = {v for values in param.values() for v in values}
    unknown = [p for p in enum_values
               if p != "Auto" and p not in GRID_PROTOCOLS and p not in mapped_values]
    if unknown:
        report["enum_drift"] = unknown

    report["totals"] = {
        "code_tests": len(tests),
        "mapping_plain": len(plain),
        "mapping_parametrized_methods": len(param),
        "mapping_parametrized_cases": sum(len(v) for v in param.values()),
        "new": len(report["new"]), "removed": len(report["removed"]),
        "moved": len(report["moved"]), "data_values": len(report["data_values"]),
        "no_story": len(report["no_story"]), "title_review": len(report["title_review"]),
    }

    if args.json:
        print(json.dumps(report, indent=1, ensure_ascii=False))
        return 0

    t = report["totals"]
    print(f"code tests: {t['code_tests']} | mapping: {t['mapping_plain']} plain "
          f"+ {t['mapping_parametrized_cases']} per-value cases\n")
    if missing_files:
        print(f"!! classes listed in a suite XML but missing on disk: {missing_files}\n")
    if xml_duplicates:
        print("!! duplicate <class> entries in a suite XML (TestNG would run them twice):")
        for note in xml_duplicates:
            print("   " + note)
        print()

    def section(title, items, fmt):
        print(f"== {title}: {len(items)}")
        for i in items:
            print("   " + fmt(i))
        print()

    section("NEW (create a case in Testomat.io, then add to the mapping)", report["new"],
            lambda i: f'{i["key"]}\n       feature={i["feature"]!r} story={i["story"]!r} '
                      f'severity={i["severity"]} dp={i["data_provider"]}\n       objective={i["objective"]!r}')
    section("REMOVED (case exists in Testomat.io, method is gone from the code)", report["removed"],
            lambda i: f'{i["key"]}  ids={i["ids"]}')
    section("MOVED (@Feature / @Story changed - move the case)", report["moved"],
            lambda i: json.dumps(i, ensure_ascii=False))
    section("DATA_VALUES (per-value cases drifted from the code)", report["data_values"],
            lambda i: f'{i["key"]}\n       missing={i["missing_cases"]} stale={i["stale_cases"]}')
    section("NO_STORY (add @Story in the code - see the skill)", report["no_story"], lambda i: i)
    section("ENUM_DRIFT (in the Protocols enum, not in the known TV grid - check the box)",
            report["enum_drift"], lambda i: f'{i}  -> if the grid now shows it, add a case and put it in GRID_PROTOCOLS')
    section("TITLE_REVIEW (objective drifted from the case title - check by hand)", report["title_review"],
            lambda i: f'{i["key"]} ({i["id"]})\n       case:  {i["case_title"]}\n       code:  {i["code_objective"]}')

    if not any(report[k] for k in ("new", "removed", "moved", "data_values", "no_story")):
        print("Testomat.io is in sync with the Android TV framework.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
