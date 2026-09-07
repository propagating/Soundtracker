# Soundtrack Tracker — Design

Date: 2026-09-06
Status: approved design, awaiting spec review

## Purpose

A RuneLite Plugin Hub plugin that helps a player earn the music cape. It lists
every music track in Old School RuneScape, shows which ones the logged-in
player has unlocked, and opens a quest-guide style page for any track that
says how to unlock it, where it plays, and where that is on the world map.

## Scope of version 1

In:

- Live locked/unlocked state per track, with a progress count toward the cape.
- Search by name; filter to all, locked, or unlocked; sort by name or by location.
- Per-track detail view: unlock hint, location, quest, wiki link, "Show on map".
- One world-map marker for the selected track.
- A wiki fetch script that produces the bundled enrichment file.

Out, deliberately, for later versions:

- Step-by-step walkthroughs per track.
- In-client refresh from the wiki.
- Route planning or "nearest locked track" logic.
- Any overlay in the 3D scene.

## Where the data comes from

Two sources, joined at load time.

### The game cache (authoritative for the catalog and unlock state)

The client ships a music database table (`DBTableID.Music`, table id 44). Each
row carries:

| Column | Used for |
| --- | --- |
| `COL_DISPLAYNAME` | the name shown in the list |
| `COL_SORTNAME` | alphabetical ordering, matching the in-game player |
| `COL_UNLOCKHINT` | the hint line, identical to the in-game right-click hint |
| `COL_MIDI` | the track id, used to join wiki data (`cacheid`) |
| `COL_VARIABLE` | a two-element tuple recording where the unlock bit lives |
| `COL_HIDDEN` | rows the game hides from the player; excluded from the list |
| `COL_HOLIDAY` | holiday tracks; listed, but excluded from the cape count |
| `COL_AUTOMATIC_UNLOCK` | tracks that unlock for everyone; shown as unlocked |

Rows are enumerated with `client.getDBTableRows(DBTableID.Music.ID)` and read
with `client.getDBTableField(row, column, tupleIndex)`. Both are client-thread
calls.

Unlock state is `(client.getVarpValue(varp) >> bit) & 1`, where `varp` and
`bit` are the two elements of `COL_VARIABLE`. The first implementation task
verifies that tuple order and semantics in the dev client against the in-game
music list; if the encoding differs, the catalog's single `isUnlocked` method
is the only place that changes.

Because the catalog is read from the cache, new tracks appear with their hint
the day Jagex ships them, with no plugin update. Unlock state does not require
the music tab to have been opened.

### The wiki (enrichment only)

`tools/fetch_tracks.py` sweeps every page embedding `Template:Infobox Music`
through the wiki API and writes
`src/main/resources/com/soundtracktracker/tracks.json`. Per track it keeps:

| JSON field | Wiki source | Notes |
| --- | --- | --- |
| `cacheid` | `cacheid` | join key to `COL_MIDI` |
| `name` | `name` | fallback join key, case-insensitive, when `cacheid` is absent |
| `location` | `location` | wiki markup stripped to plain text |
| `region` | first link in `location` | used by "sort by location" |
| `quest` | `quest` | plain text; `No`, `None`, `N/A` become absent |
| `unlockdetail` | `unlockdetail` | plain text; shown under the hint when present |
| `members` | `members` | boolean |
| `map` | first `{{Map}}` in `map` | `{x, y, plane}`: centroid of the first polygon, or the pin itself |
| `wikiUrl` | page title | `https://oldschool.runescape.wiki/w/<title>` |

The script has no dependencies beyond the Python standard library, sends a
descriptive `User-Agent`, and is run by hand; the JSON is committed. A track
missing from the JSON still appears in the plugin with its hint from the
cache, just without location, quest, or map.

## Architecture

Package `com.soundtracktracker`, one class per responsibility:

```
SoundtrackTrackerPlugin        lifecycle, event subscriptions, wiring
SoundtrackTrackerConfig        config group "soundtracktracker"
catalog/
  Track                        immutable value: name, sortName, hint, midiId,
                               unlocked, holiday, automatic, + enrichment fields
  TrackEnrichment              parsed tracks.json row
  EnrichmentStore              loads tracks.json with the injected Gson,
                               looks up by midi id then by name
  TrackCatalog                 reads the DB table on login, holds List<Track>,
                               recomputes unlock flags on VarbitChanged, and
                               publishes an immutable snapshot to listeners
  CapeProgress                 pure function: snapshot -> (unlocked, required)
                               counting only non-holiday, non-hidden tracks
  TrackFilter                  pure function: snapshot + query + lock filter
                               + sort mode -> ordered list
ui/
  SoundtrackTrackerPanel       PluginPanel; header, search, filter, list;
                               swaps to the detail view and back
  ProgressHeader               "412 / 869 tracks" plus a progress bar
  TrackListView                scrolling list of TrackRow
  TrackRow                     name, location subtitle, lock icon; click opens detail
  TrackDetailView              quest-guide style card for one track
map/
  TrackMapMarker               owns the single WorldMapPoint; show(track), clear()
```

### Data flow

1. On `GameState.LOGGED_IN`, the plugin asks `TrackCatalog` to load on the
   client thread. It reads every row once, joins enrichment, evaluates unlock
   bits, and hands an immutable snapshot to the panel through
   `SwingUtilities.invokeLater`.
2. On `VarbitChanged`, the catalog checks whether the changed varp is one any
   track uses (a precomputed set). If so it recomputes unlock flags for the
   tracks on that varp and publishes a new snapshot. Cheap, and it is what
   makes a track flip to unlocked the moment the jingle plays.
3. The panel applies `TrackFilter` on the Swing thread whenever the snapshot,
   query, filter, or sort changes, and rebuilds the list.
4. Clicking a row shows `TrackDetailView`. "Show on map" calls
   `TrackMapMarker.show(track)`, which replaces any previous marker and sets
   the world-map pan target so opening the map centres on it. Back returns to
   the list with the scroll position preserved.
5. On `shutDown`, the marker is cleared, the nav button removed, and listeners
   dropped.

### Config

- `showHolidayTracks` (default on): list holiday tracks. They never count
  toward the cape total either way.
- `hideUnlocked` (default off): start the panel on the "locked" filter.

No config item touches a third-party server, so no warning fields.

## Error handling

- `tracks.json` missing or malformed: log a warning once, run with an empty
  enrichment store. The plugin still lists every track with its hint.
- A DB row missing a column (`null` or empty array from `getDBTableField`):
  skip enrichment of that field, never the row. A row with no display name is
  skipped and counted in a debug log line.
- Enrichment row whose `cacheid` matches no game row: ignored; the script's
  summary reports the count so it can be investigated on the wiki side.
- Varp read before login: the catalog is not loaded until `LOGGED_IN`, and the
  panel shows "Log in to load your tracks" until the first snapshot arrives.
- World map closed when "Show on map" is clicked: the marker is still added;
  the pan target applies when the map is next opened.

## Testing

- `TrackCatalogTest`: mocked `Client` returning a small table (unlocked,
  locked, hidden, holiday, automatic rows) and varps; asserts the snapshot and
  the recompute-on-varp-change path. Verifies rows with a null name are skipped.
- `CapeProgressTest`: holiday and hidden rows excluded; automatic rows counted
  as unlocked.
- `TrackFilterTest`: query matching is case-insensitive and substring; lock
  filter; sort by name uses `sortName`; sort by location groups tracks with
  no region last.
- `EnrichmentStoreTest`: loads a fixture JSON; lookup by midi id wins over
  name; malformed JSON yields an empty store.
- `fetch_tracks.py` has a small `unittest` module covering wikitext stripping,
  `{{Map}}` centroid parsing, and the `No`/`None`/`N/A` normalisation.
- In-game verification by the user via `./gradlew run`: progress count matches
  the in-game music tab count, a freshly unlocked track flips live, the map
  marker lands on the right spot for a known track such as Sea Shanty 2.

## Repository

`E:\projects\java\SoundtrackTracker`, Plugin Hub template matching the sibling
plugins: `build.gradle` targeting Java 11 with the `run` and `shadowJar` tasks,
`settings.gradle` root name `soundtracktracker`, `runelite-plugin.properties`
with display name "Soundtrack Tracker", BSD-2 licence, `AGENTS.md` copied from
Item Search, a `CLAUDE.md` that points at it, and `docs/superpowers/` for this
spec and its plan.
