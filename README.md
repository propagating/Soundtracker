# Soundtracker

A RuneLite plugin for chasing the music cape. It lists every music track, shows
live which ones you have unlocked, and opens a guide page per track with the
unlock hint, where it plays, the related quest, a world-map marker and a wiki
link.

## How it works

- The track list, unlock hints and your unlock state come from the game client
  itself, so new tracks appear the day they are released and nothing needs the
  music tab to be open.
- Locations, quests, map points and wiki links come from the OSRS Wiki, bundled
  as `src/main/resources/com/soundtracker/tracks.json`.
- "Show on map" drops a music-note marker where the track plays. When that spot
  is underground, a second marker goes on the dungeon's surface entrance (taken
  from RuneLite's own world-map dungeon list) and the map opens there instead,
  since the surface map cannot show the underground point.
- The filter beside the search box shows all, locked or unlocked tracks, or
  "Needed for cape": the locked tracks that still count toward the music cape,
  with holiday tracks left out.
- The same file marks the tracks Cecilia in Falador Park can ask for during a
  Treasure Trails step (taken from her wiki page). Tick "Clue tracks only" to
  see just those, with their clue tier and any requirement note; the header
  then counts how many of them you have unlocked.

## Refreshing the wiki data

```
python tools/fetch_tracks.py
python -m unittest discover -s tools
```

The script needs only the Python standard library and prints a summary; commit
the regenerated `tracks.json`. Dungeon entrances come from
`tools/dungeon_entrances.json`, a dump of RuneLite's world-map dungeon list made
by `tools/DumpDungeonEntrances.java` (run it with the RuneLite client jar on the
classpath when RuneLite adds dungeons). `tools/make_icon.py` regenerates the
panel's toolbar icon and the map marker.

## Developing

```
./gradlew test      # unit tests
./gradlew run       # dev client with the plugin loaded
./gradlew sideload  # build the jar and copy it to ~/.runelite/sideloaded-plugins
```

## Configuration

- **Show holiday tracks** (default: on) - list holiday event tracks, which never
  count toward the music cape.
- **Start on locked tracks** (default: off) - open the panel with the filter
  already set to locked tracks only.
