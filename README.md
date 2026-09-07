# Soundtrack Tracker

A RuneLite plugin for chasing the music cape. It lists every music track, shows
live which ones you have unlocked, and opens a guide page per track with the
unlock hint, where it plays, the related quest, a world-map marker and a wiki
link.

## How it works

- The track list, unlock hints and your unlock state come from the game client
  itself, so new tracks appear the day they are released and nothing needs the
  music tab to be open.
- Locations, quests, map points and wiki links come from the OSRS Wiki, bundled
  as `src/main/resources/com/soundtracktracker/tracks.json`.

## Refreshing the wiki data

```
python tools/fetch_tracks.py
python -m unittest discover -s tools
```

The script needs only the Python standard library and prints a summary; commit
the regenerated `tracks.json`.

## Developing

```
./gradlew test      # unit tests
./gradlew run       # dev client with the plugin loaded
```

See `AGENTS.md` for the Plugin Hub rules this code follows.
