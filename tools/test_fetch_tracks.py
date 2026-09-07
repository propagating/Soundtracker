import unittest

import fetch_tracks as ft

SEA_SHANTY = """{{Infobox Music
|name = Sea Shanty 2
|number = 107
|members = No
|location = [[Port Sarim]], [[Prifddinas]] docks, [[Tempoross Cove]]
|hint = This track unlocks in Port Sarim.
|quest = No
|map = {{Map|3065,3250|3065,3254|3005,3250|3005,3254|plane=0|mapID=-1|mtype=polygon|height=250|group=1|bucket=yes}}{{Map|2157,3327|2165,3327|plane=0|mtype=polygon}}
|cacheid = 35
|sortname = Sea Shanty 2
|unlockdetail = Unlocked in [[Port Sarim]].
}}
Some article text."""


class SplitTest(unittest.TestCase):
    def test_splits_only_top_level_pipes(self):
        self.assertEqual(ft.split_top_level("a|{{b|c}}|[[d|e]]|f"), ["a", "{{b|c}}", "[[d|e]]", "f"])

    def test_find_template_returns_body_of_nested_template(self):
        body = ft.find_template("x {{Infobox Music\n|a = {{Map|1,2}}\n|b = 3\n}} y", "Infobox Music")
        self.assertEqual(body.strip(), "|a = {{Map|1,2}}\n|b = 3")

    def test_find_template_missing(self):
        self.assertIsNone(ft.find_template("nothing here", "Infobox Music"))

    def test_split_params_lowercases_keys(self):
        self.assertEqual(ft.split_params("\n|Name = X\n|cacheid = 35\n"), {"name": "X", "cacheid": "35"})


class MarkupTest(unittest.TestCase):
    def test_strip_links_bold_refs_and_templates(self):
        self.assertEqual(
            ft.strip_markup("[[Port Sarim]], [[Prifddinas|Prif]] docks<ref>x</ref> '''bold''' {{sic}}<br>more"),
            "Port Sarim, Prif docks bold, more")

    def test_strip_markup_debullets_an_embedded_list(self):
        self.assertEqual(
            ft.strip_markup("Unlocked in the\n* [[Wilderness Slayer Cave]]\n* [[Callisto's Den]]"),
            "Unlocked in the Wilderness Slayer Cave, Callisto's Den")

    def test_debullet_leading_bullet_has_no_sentence_head(self):
        self.assertEqual(ft.debullet("* A\n* B\n* C"), "A, B, C")

    def test_debullet_no_bullets_is_unchanged(self):
        self.assertEqual(ft.debullet("Plain text."), "Plain text.")

    def test_strip_none(self):
        self.assertIsNone(ft.strip_markup(None))
        self.assertIsNone(ft.strip_markup("  "))

    def test_normalize_optional(self):
        for v in ("No", "none", "N/A", "", None):
            self.assertIsNone(ft.normalize_optional(v))
        self.assertEqual(ft.normalize_optional(" Dragon Slayer "), "Dragon Slayer")

    def test_first_link_uses_page_target(self):
        self.assertEqual(ft.first_link("near [[Prifddinas#Docks|the docks]] and [[Port Sarim]]"), "Prifddinas")
        self.assertIsNone(ft.first_link("no links"))

    def test_yes_no(self):
        self.assertIs(ft.yes_no("Yes"), True)
        self.assertIs(ft.yes_no(" no "), False)
        self.assertIsNone(ft.yes_no("N/A"))


class MapTest(unittest.TestCase):
    def test_polygon_centroid_of_first_map_only(self):
        m = ft.parse_map("{{Map|3065,3250|3065,3254|3005,3250|3005,3254|plane=0|mtype=polygon}}{{Map|1,1|mtype=pin}}")
        self.assertEqual(m, {"x": 3035, "y": 3252, "plane": 0})

    def test_pin_with_positional_point(self):
        self.assertEqual(ft.parse_map("{{Map|3222,3218|plane=1}}"), {"x": 3222, "y": 3218, "plane": 1})

    def test_pin_with_named_xy(self):
        self.assertEqual(ft.parse_map("{{Map|x=10|y=20|mtype=pin}}"), {"x": 10, "y": 20, "plane": 0})

    def test_no_map(self):
        self.assertIsNone(ft.parse_map("No"))
        self.assertIsNone(ft.parse_map(None))
        self.assertIsNone(ft.parse_map("{{Map|plane=0}}"))


class InstanceTemplateTest(unittest.TestCase):
    """{{Map}} points inside a raid/lair's unrendered template zone must come back None
    even though the template itself parses cleanly and carries mapID=-1, same as a real,
    renderable point. See the comment above `_INSTANCE_TEMPLATE_BOXES` in fetch_tracks.py
    for the raw-wikitext evidence."""

    def test_theatre_of_blood_room_instance_is_none(self):
        # The Maiden's Sorrow's actual {{Map}} value.
        self.assertIsNone(ft.parse_map(
            "{{Map|3136,4416|3136,4480|3264,4480|3264,4416|plane=0|mapID=-1|mtype=polygon|bucket=yes}}"))

    def test_tombs_of_amascut_room_instance_is_none(self):
        # Amascut's Promise's actual {{Map}} value.
        self.assertIsNone(ft.parse_map(
            "{{Map|3776,5120|3776,5184|3840,5184|3840,5120|mapID=-1|mtype=polygon|bucket=yes|zoom=2}}"))

    def test_cerberus_lair_is_none(self):
        self.assertIsNone(ft.parse_map("{{Map|1248,1248|plane=0}}"))

    def test_real_surface_point_outside_template_zones_is_kept(self):
        # Welcome to the Theatre (the Theatre of Blood's non-instanced lobby) is well
        # outside every template box and must still resolve.
        self.assertEqual(ft.parse_map("{{Map|3673,3220|plane=0}}"), {"x": 3673, "y": 3220, "plane": 0})


class RecordTest(unittest.TestCase):
    def test_build_record_from_sea_shanty(self):
        rec = ft.build_record("Sea Shanty 2", SEA_SHANTY)
        self.assertEqual(rec["cacheid"], 35)
        self.assertEqual(rec["name"], "Sea Shanty 2")
        self.assertEqual(rec["location"], "Port Sarim, Prifddinas docks, Tempoross Cove")
        self.assertEqual(rec["region"], "Port Sarim")
        self.assertIsNone(rec["quest"])
        self.assertEqual(rec["unlockdetail"], "Unlocked in Port Sarim.")
        self.assertIs(rec["members"], False)
        self.assertEqual(rec["map"], {"x": 3035, "y": 3252, "plane": 0})
        self.assertEqual(rec["wikiUrl"], "https://oldschool.runescape.wiki/w/Sea_Shanty_2")

    def test_page_without_infobox_is_skipped(self):
        self.assertIsNone(ft.build_record("Music", "Just an article."))

    def test_missing_cacheid_is_null_and_name_falls_back_to_title(self):
        rec = ft.build_record("Odd Track", "{{Infobox Music\n|location = [[Varrock]]\n}}")
        self.assertIsNone(rec["cacheid"])
        self.assertEqual(rec["name"], "Odd Track")
        self.assertEqual(rec["region"], "Varrock")
        self.assertIsNone(rec["map"])


MAP_PAGE = """{{Music track map
|songs = Pathways, 7th Realm, Karamja Jam, Nether Realm
|files = File:Pathways.ogg, File:7th Realm.ogg, File:Karamja Jam.ogg, File:Nether Realm.ogg
|mapname = Map:Brimhaven Dungeon
|group = {{{group|Brimhaven Dungeon}}}
|x=2688|y=9504|zoom=0|[
[ [2624,9600],[2752,9600],[2752,9408],[2624,9408] ]
]}}"""

SEVENTH_REALM = """{{Infobox Music
|name = 7th Realm
|members = Yes
|location = [[Brimhaven Dungeon]]
|quest = No
|map = {{Map:Brimhaven Dungeon}}{{Map:7th Realm}}
|cacheid = 363
|unlockdetail = Unlocked in the [[Brimhaven Dungeon]].
}}"""


class MapReferenceTest(unittest.TestCase):
    def test_find_map_ref_returns_first_transcluded_map_page_title(self):
        self.assertEqual(ft.find_map_ref("{{Map:Brimhaven Dungeon}}{{Map:7th Realm}}"), "Map:Brimhaven Dungeon")
        self.assertIsNone(ft.find_map_ref("{{Map|1,2}}"))
        self.assertIsNone(ft.find_map_ref(None))

    def test_parse_music_track_map_reads_top_level_xy_and_defaults_plane(self):
        self.assertEqual(ft.parse_music_track_map(MAP_PAGE), {"x": 2688, "y": 9504, "plane": 0})

    def test_parse_music_track_map_reads_plane_when_present(self):
        page = "{{Music track map\n|x=1912|y=8992|plane=1|zoom=1|[\n[ [1968,8960],[1968,9024] ]\n]}}"
        self.assertEqual(ft.parse_music_track_map(page), {"x": 1912, "y": 8992, "plane": 1})

    def test_parse_music_track_map_missing_or_no_template(self):
        self.assertIsNone(ft.parse_music_track_map("no template here"))
        self.assertIsNone(ft.parse_music_track_map("{{Music track map\n|zoom=1}}"))

    def test_build_record_resolves_map_via_transcluded_map_page(self):
        rec = ft.build_record("7th Realm", SEVENTH_REALM, map_pages={"Map:Brimhaven Dungeon": MAP_PAGE})
        self.assertEqual(rec["map"], {"x": 2688, "y": 9504, "plane": 0})

    def test_build_record_without_map_pages_lookup_leaves_map_none(self):
        rec = ft.build_record("7th Realm", SEVENTH_REALM)
        self.assertIsNone(rec["map"])


def _row(name, cacheid, **kwargs):
    row = {
        "cacheid": cacheid,
        "name": name,
        "location": None,
        "region": None,
        "quest": None,
        "unlockdetail": None,
        "members": None,
        "map": None,
        "wikiUrl": "https://oldschool.runescape.wiki/w/" + name.replace(" ", "_"),
    }
    row.update(kwargs)
    return row


class DedupeCacheidsTest(unittest.TestCase):
    def test_richer_row_wins_regardless_of_sort_order(self):
        empty = _row("A Forgettable Puzzle... (Forgettable Tale...)", 12)
        rich = _row(
            "Long Way Home", 12,
            location="Varrock", region="Varrock", quest="No",
            unlockdetail="Unlocked in Varrock.", members=False, map={"x": 1, "y": 2, "plane": 0})
        for rows in ([empty, rich], [rich, empty]):
            by_name = {r["name"]: r for r in ft.dedupe_cacheids(rows)}
            self.assertEqual(by_name["Long Way Home"]["cacheid"], 12)
            self.assertIsNone(by_name["A Forgettable Puzzle... (Forgettable Tale...)"]["cacheid"])

    def test_equal_richness_prefers_non_parenthesised_name(self):
        parenthesised = _row("Some Track (Remix)", 7, location="X")
        plain = _row("Some Track", 7, location="Y")
        by_name = {r["name"]: r for r in ft.dedupe_cacheids([parenthesised, plain])}
        self.assertEqual(by_name["Some Track"]["cacheid"], 7)
        self.assertIsNone(by_name["Some Track (Remix)"]["cacheid"])

    def test_losers_keep_their_record_with_cacheid_none(self):
        loser = _row("Alpha", 3)
        winner = _row("Beta", 3, location="Somewhere")
        by_name = {r["name"]: r for r in ft.dedupe_cacheids([loser, winner])}
        self.assertIsNone(by_name["Alpha"]["cacheid"])
        self.assertEqual(by_name["Alpha"]["name"], "Alpha")
        self.assertEqual(by_name["Alpha"]["wikiUrl"], loser["wikiUrl"])

    def test_rows_with_null_cacheid_are_untouched(self):
        rows = [_row("No Cache", None, location="Somewhere")]
        result = ft.dedupe_cacheids(rows)
        self.assertIsNone(result[0]["cacheid"])
        self.assertEqual(result[0]["location"], "Somewhere")


if __name__ == "__main__":
    unittest.main()
