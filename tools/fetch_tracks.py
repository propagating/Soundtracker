#!/usr/bin/env python3
"""Sweep every OSRS wiki page using {{Infobox Music}} and write the plugin's tracks.json.

Standard library only. Run from anywhere:  python tools/fetch_tracks.py [output-path]
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

API = "https://oldschool.runescape.wiki/api.php"
WIKI = "https://oldschool.runescape.wiki/w/"
USER_AGENT = "SoundtrackTracker-RuneLite-plugin/1.0 (data refresh script; contact: propagating@protonmail.com)"
DEFAULT_OUT = Path(__file__).resolve().parents[1] / "src/main/resources/com/soundtracktracker/tracks.json"
ABSENT = {"", "no", "none", "n/a", "unknown"}


# ---------- wikitext primitives ----------

def split_top_level(s, sep="|"):
    """Split on `sep` but not inside {{...}} or [[...]]."""
    parts, buf, depth, i = [], [], 0, 0
    while i < len(s):
        two = s[i:i + 2]
        if two in ("{{", "[["):
            depth += 1
            buf.append(two)
            i += 2
            continue
        if two in ("}}", "]]"):
            depth = max(0, depth - 1)
            buf.append(two)
            i += 2
            continue
        if s[i] == sep and depth == 0:
            parts.append("".join(buf))
            buf = []
            i += 1
            continue
        buf.append(s[i])
        i += 1
    parts.append("".join(buf))
    return parts


def find_template(text, name):
    """Body (between the name and the closing braces) of the first {{name ...}}, or None."""
    m = re.search(r"\{\{\s*" + re.escape(name) + r"\s*(?=[|}\n])", text, re.I)
    if not m:
        return None
    start = i = m.end()
    depth = 1
    while i < len(text):
        if text.startswith("{{", i):
            depth += 1
            i += 2
        elif text.startswith("}}", i):
            depth -= 1
            if depth == 0:
                return text[start:i]
            i += 2
        else:
            i += 1
    return None


def split_params(body):
    params = {}
    for part in split_top_level(body):
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        key = key.strip().lower()
        if key:
            params[key] = value.strip()
    return params


def remove_templates(s):
    out, i, depth = [], 0, 0
    while i < len(s):
        if s.startswith("{{", i):
            depth += 1
            i += 2
        elif s.startswith("}}", i) and depth:
            depth -= 1
            i += 2
        else:
            if depth == 0:
                out.append(s[i])
            i += 1
    return "".join(out)


def debullet(s):
    """Turn a MediaWiki bullet list embedded in a sentence into flowing prose: the first
    bullet just continues the sentence (dropped), later ones read as a comma-separated
    list. Handles a bullet at the very start of the string as well as one after a newline.
    """
    parts = re.split(r"(?:\A[ \t]*|\n[ \t]*)\*+[ \t]*", s)
    if len(parts) == 1:
        return s
    head = parts[0].strip()
    rest = ", ".join(parts[1:])
    return (head + " " + rest) if head else rest


def strip_markup(s):
    if s is None:
        return None
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"<br\s*/?>", ", ", s, flags=re.I)
    s = debullet(s)
    s = remove_templates(s)
    s = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", s)
    s = re.sub(r"'{2,}", "", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = re.sub(r"\s*,(\s*,)+", ",", s)
    s = re.sub(r"\s+,", ",", s)
    s = re.sub(r"\s+", " ", s).strip(" ,")
    return s or None


def normalize_optional(s):
    if s is None:
        return None
    s = s.strip()
    return None if s.lower() in ABSENT else s


def first_link(s):
    m = re.search(r"\[\[([^\]|#]+)", s or "")
    return m.group(1).strip() if m else None


def yes_no(s):
    v = (s or "").strip().lower()
    if v == "yes":
        return True
    if v == "no":
        return False
    return None


# ---------- {{Map}} ----------

_POINT = re.compile(r"^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$")

# Raid dungeons (Theatre of Blood, Tombs of Amascut, Chambers of Xeric) and some boss
# lairs (Cerberus) are instanced: the world map cannot render them, so a marker placed
# there snaps to the map's edge instead of pointing at real ground.
#
# The {{Map}} template's `mapID` parameter looked like the signal to use here (the plugin
# already needs -1, "use the default surface map", to mean "renderable"), but checking it
# does not hold: raw wikitext fetched directly from the wiki (2026-09-06) for every
# Theatre of Blood room track (The Maiden's Sorrow, The Dark Beast Sotetseg, Predator
# Xarpus, Power of the Shadow Realm) and two Tombs of Amascut room tracks (Amascut's
# Promise, Into the Tombs) all carry `mapID=-1`, same as the non-instanced Sea Shanty 2
# and Tempest (Slayer Tower rooftop) fixtures. mapID cannot tell them apart.
#
# What does: every room/boss track inside one of these instances - despite representing
# physically distinct spaces if the raid were real, walkable terrain - collapses onto the
# same tiny few-region coordinate block, because Jagex reuses a handful of unrendered
# "template" chunks for instanced content rather than authoring real terrain for each
# room. That clustering, cross-checked against the wiki's raw {{Map}} values above and the
# previously generated tracks.json for every other track sharing this raid, is the
# evidence for the boxes below (min/max padded out to the nearest region boundary, i.e.
# multiples of 64):
#   Theatre of Blood room instance: x 3136-3391, y 4224-4479 (Maiden's Sorrow, Maiden's
#       Anger, Dance of the Nylocas, Arachnids of Vampyrium, Dark Beast Sotetseg, Power of
#       the Shadow Realm, Predator Xarpus, Last King of the Yarasa, It's not over 'til...,
#       The Fat Lady Sings, The Curtain Closes, Welcome to my Nightmare, The Nightmare
#       Continues all land here; the real, non-instanced Theatre of Blood lobby track
#       "Welcome to the Theatre" instead maps to x=3673, y=3220, well outside this box)
#   Tombs of Amascut room instance (path rooms): x 3552-3967, y 5120-5439 (Amascut's
#       Promise, Beneath Cursed Sands, Ape-ex Predator, Jaws of Gluttony, Laid to Rest,
#       Sands of Time, A Mother's Curse)
#   Tombs of Amascut room instance (Into the Tombs cutscene): x 3328-3391, y 9088-9151
#   Chambers of Xeric room instance: x 3200-3391, y 5264-5759 (Fire in the Deep, Upper
#       Depths, Lower Depths)
#   Cerberus's Lair: x 1216-1343, y 1216-1279 (Maws Jaws & Claws, That Sullen Hall); this
#       also sits below Gielinor's southern edge on the real surface map
_INSTANCE_TEMPLATE_BOXES = (
    (3136, 3391, 4224, 4479),
    (3552, 3967, 5120, 5439),
    (3328, 3391, 9088, 9151),
    (3200, 3391, 5264, 5759),
    (1216, 1343, 1216, 1279),
)


def _in_instance_template(x, y):
    return any(x0 <= x <= x1 and y0 <= y <= y1 for x0, x1, y0, y1 in _INSTANCE_TEMPLATE_BOXES)


def parse_map(value):
    """{x, y, plane} for the first {{Map}}: centroid of a polygon's vertices, else the pin.

    None for a point that falls inside a known instanced-region template block (see
    `_INSTANCE_TEMPLATE_BOXES`) even if the template itself parses cleanly - the world map
    cannot render those coordinates.
    """
    if not value:
        return None
    body = find_template(value, "Map")
    if body is None:
        return None
    points, named, plane, mtype = [], {}, 0, "pin"
    for part in split_top_level(body):
        part = part.strip()
        if not part:
            continue
        if "=" in part:
            key, val = part.split("=", 1)
            key, val = key.strip().lower(), val.strip()
            if key == "plane" and re.fullmatch(r"-?\d+", val):
                plane = int(val)
            elif key == "mtype":
                mtype = val.lower()
            elif key in ("x", "y") and re.fullmatch(r"-?\d+(?:\.\d+)?", val):
                named[key] = float(val)
            continue
        m = _POINT.match(part)
        if m:
            points.append((float(m.group(1)), float(m.group(2))))
    if "x" in named and "y" in named:
        points.insert(0, (named["x"], named["y"]))
    if not points:
        return None
    if mtype in ("polygon", "line") and len(points) > 1:
        x = sum(p[0] for p in points) / len(points)
        y = sum(p[1] for p in points) / len(points)
    else:
        x, y = points[0]
    x, y = int(round(x)), int(round(y))
    if _in_instance_template(x, y):
        return None
    return {"x": x, "y": y, "plane": plane}


# ---------- {{Map:PageName}} transclusion ----------
# Many Infobox Music `map` fields don't inline a {{Map|...}} template; instead they
# transclude a separate "Map:<Name>" wiki page, e.g. `map = {{Map:Brimhaven Dungeon}}`.
# That page defines a {{Music track map|...|x=..|y=..|plane=..|...}} template whose
# top-level x/y (and optional plane, default 0) give a usable marker position.

_MAP_REF = re.compile(r"\{\{\s*Map\s*:\s*([^|}\n]+?)\s*\}\}", re.I)


def find_map_ref(value):
    """Title of the page transcluded by the first {{Map:Title}} in `value`, or None."""
    if not value:
        return None
    m = _MAP_REF.search(value)
    return "Map:" + m.group(1) if m else None


def parse_music_track_map(text):
    """{x, y, plane} from a transcluded {{Music track map}} page's top-level params, or None."""
    body = find_template(text, "Music track map")
    if body is None:
        return None
    p = split_params(body)
    x_raw, y_raw = p.get("x"), p.get("y")
    if not x_raw or not y_raw:
        return None
    if not re.fullmatch(r"-?\d+(?:\.\d+)?", x_raw) or not re.fullmatch(r"-?\d+(?:\.\d+)?", y_raw):
        return None
    plane_raw = p.get("plane", "0")
    plane = int(plane_raw) if re.fullmatch(r"-?\d+", plane_raw) else 0
    return {"x": int(round(float(x_raw))), "y": int(round(float(y_raw))), "plane": plane}


# ---------- records ----------

def build_record(title, wikitext, map_pages=None):
    body = find_template(wikitext, "Infobox Music")
    if body is None:
        return None
    p = split_params(body)
    cacheid_raw = (p.get("cacheid") or "").strip()
    location_raw = p.get("location")
    location = normalize_optional(strip_markup(location_raw))
    map_value = p.get("map")
    m = parse_map(map_value)
    if m is None and map_pages:
        ref = find_map_ref(map_value)
        if ref and ref in map_pages:
            m = parse_music_track_map(map_pages[ref])
    return {
        "cacheid": int(cacheid_raw) if cacheid_raw.isdigit() else None,
        "name": strip_markup(p.get("name")) or title,
        "location": location,
        "region": first_link(location_raw) if location else None,
        "quest": normalize_optional(strip_markup(p.get("quest"))),
        "unlockdetail": normalize_optional(strip_markup(p.get("unlockdetail"))),
        "members": yes_no(p.get("members")),
        "map": m,
        "wikiUrl": WIKI + urllib.parse.quote(title.replace(" ", "_")),
    }


# ---------- cacheid collisions ----------
# The wiki reuses a cacheid on more than one page (typically an old/renamed title
# left behind alongside the current one, with most infobox fields blank). Only one
# row per cacheid can win the cache-id lookup in EnrichmentStore, so pick the
# richest row to hold it and null the cacheid on the rest (they stay matchable by
# name).

_RICHNESS_FIELDS = ("location", "region", "quest", "unlockdetail", "members", "map")


def _richness(row):
    return sum(1 for f in _RICHNESS_FIELDS if row.get(f) is not None)


def _holder_key(row):
    name = row["name"]
    return (-_richness(row), " (" in name, len(name), name.lower())


def dedupe_cacheids(rows):
    """Rows sharing a cacheid keep it only on the richest row; the rest get cacheid=None."""
    groups = {}
    for r in rows:
        if r["cacheid"] is not None:
            groups.setdefault(r["cacheid"], []).append(r)
    losers = set()
    for group in groups.values():
        if len(group) <= 1:
            continue
        ordered = sorted(group, key=_holder_key)
        for r in ordered[1:]:
            losers.add(id(r))
    return [dict(r, cacheid=None) if id(r) in losers else r for r in rows]


# ---------- wiki API ----------

def fetch_pages():
    """Yield (title, wikitext) for every main-namespace page embedding Infobox Music."""
    base = {
        "action": "query",
        "generator": "embeddedin",
        "geititle": "Template:Infobox Music",
        "geinamespace": "0",
        "geilimit": "50",
        "prop": "revisions",
        "rvprop": "content",
        "rvslots": "main",
        "format": "json",
        "formatversion": "2",
    }
    cont = {}
    while True:
        params = dict(base)
        params.update(cont)
        req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
        for page in data.get("query", {}).get("pages", []):
            revs = page.get("revisions") or []
            if revs:
                yield page["title"], revs[0]["slots"]["main"]["content"]
        if "continue" not in data:
            return
        cont = data["continue"]
        time.sleep(0.5)


def fetch_titles(titles):
    """Yield (title, wikitext) for explicit page titles, queried in batches of 50."""
    titles = sorted(titles)
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        params = {
            "action": "query",
            "titles": "|".join(batch),
            "prop": "revisions",
            "rvprop": "content",
            "rvslots": "main",
            "format": "json",
            "formatversion": "2",
        }
        req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.load(resp)
        for page in data.get("query", {}).get("pages", []):
            revs = page.get("revisions") or []
            if revs:
                yield page["title"], revs[0]["slots"]["main"]["content"]
        time.sleep(0.5)


def main(argv):
    out = Path(argv[1]) if len(argv) > 1 else DEFAULT_OUT
    pages = records = 0
    page_texts = list(fetch_pages())
    pages = len(page_texts)

    # Resolve {{Map:Title}} transclusions that {{Map|...}} alone couldn't handle.
    refs = set()
    for _, text in page_texts:
        body = find_template(text, "Infobox Music")
        if body is None:
            continue
        p = split_params(body)
        map_value = p.get("map")
        if parse_map(map_value) is None:
            ref = find_map_ref(map_value)
            if ref:
                refs.add(ref)
    map_pages = dict(fetch_titles(refs)) if refs else {}

    rows = []
    for title, text in page_texts:
        rec = build_record(title, text, map_pages)
        if rec is None:
            continue
        records += 1
        rows.append(rec)
    rows.sort(key=lambda r: r["name"].lower())

    cache_counts = Counter(r["cacheid"] for r in rows if r["cacheid"] is not None)
    collisions = sum(1 for c in cache_counts.values() if c > 1)
    rows = dedupe_cacheids(rows)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(rows, indent=1, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    with_cache = sum(1 for r in rows if r["cacheid"] is not None)
    with_map = sum(1 for r in rows if r["map"])
    no_loc = sum(1 for r in rows if not r["location"])
    print(f"pages {pages}, records {records}, with cacheid {with_cache}, with map {with_map}, "
          f"without location {no_loc}, cacheid collisions resolved {collisions}")
    print(f"wrote {out}")


if __name__ == "__main__":
    main(sys.argv)
