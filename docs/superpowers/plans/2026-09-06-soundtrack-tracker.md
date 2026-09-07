# Soundtrack Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A RuneLite Plugin Hub plugin that lists every music track, shows live which ones the player has unlocked, and opens a quest-guide style page per track with the unlock hint, location, quest, wiki link and a world-map marker.

**Architecture:** The track catalog and unlock state are read from the game client's music database table and player variables, so the list never goes stale and needs no music-tab scraping. A bundled `tracks.json`, produced by a standard-library Python script from the OSRS wiki, enriches each track with location, quest, map point and wiki URL, joined on the track's midi id. A Swing side panel renders progress, search, filter, sort, a list, and a detail view; one world-map marker follows the selected track.

**Tech Stack:** Java 11, RuneLite client API (`latest.release`), Lombok 1.18.30, Gson (injected), JUnit 4.12, Mockito 3.12.4, Gradle 8.10 wrapper, Python 3.12 standard library.

**Spec:** `docs/superpowers/specs/2026-09-06-soundtrack-tracker-design.md`

## Global Constraints

- Repo root is `E:\projects\java\SoundtrackTracker`. Sibling `E:\projects\java\ItemSearch` is the template source; never modify it and never commit to `E:\projects\java\runelite`.
- Package `com.soundtracktracker`; config group `soundtracktracker`; display name `Soundtrack Tracker`; `settings.gradle` root name `soundtracktracker`.
- Every Java source file starts with the BSD-2 header below, verbatim.
- `build.gradle` targets Java 11 (`options.release.set(11)`); no dependencies beyond the template (`net.runelite:client`, Lombok, JUnit 4, Mockito 3). No `META-INF/services` file.
- Follow `AGENTS.md` (copied from Item Search): `log.debug` for anything frequent, never block the client thread with IO, `gameval` constants only, `LinkBrowser` for URLs, no reflection.
- Client-thread only: `client.getDBTableRows`, `client.getDBTableField`, `client.getVarpValue`, `client.getWorldMap`. Swing only on the EDT.
- Tests run with `./gradlew test` from the repo root (Git Bash) or `.\gradlew.bat test` (PowerShell). Run the full suite before every commit.
- Commit messages end with a blank line and `Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa`.

BSD-2 header for every Java file:

```java
/*
 * Copyright (c) 2026, propagating <propagating@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
```

---

## File Structure

```
SoundtrackTracker/
  build.gradle, settings.gradle, gradlew, gradlew.bat, gradle/wrapper/*   # template
  runelite-plugin.properties, LICENSE, .gitignore, AGENTS.md, CLAUDE.md, README.md
  tools/
    fetch_tracks.py            # wiki -> tracks.json (stdlib only)
    test_fetch_tracks.py       # unittest for the parsers
    make_icon.py               # writes panel_icon.png (stdlib only)
  src/main/resources/com/soundtracktracker/
    tracks.json                # generated enrichment
    panel_icon.png             # 16x16 music note
    map_marker.png             # copied from Item Search
  src/main/java/com/soundtracktracker/
    SoundtrackTrackerPlugin.java     # lifecycle, events, wiring
    SoundtrackTrackerConfig.java     # two config items
    catalog/Track.java               # immutable track value
    catalog/TrackEnrichment.java     # Gson row of tracks.json
    catalog/EnrichmentStore.java     # loads tracks.json, lookup by midi then name
    catalog/CapeProgress.java        # unlocked/required count
    catalog/TrackFilter.java         # query + lock filter + sort
    catalog/TrackCatalog.java        # DB table read, unlock bits, snapshots
    map/TrackMapMarker.java          # one world-map point
    ui/SoundtrackTrackerPanel.java   # PluginPanel, list <-> detail
    ui/ProgressHeader.java
    ui/TrackListView.java
    ui/TrackRow.java
    ui/TrackDetailView.java
    ui/Sections.java                 # shared label/text-area factories
  src/test/java/com/soundtracktracker/
    SoundtrackTrackerPluginTest.java # dev-client main (template)
    catalog/EnrichmentStoreTest.java
    catalog/CapeProgressTest.java
    catalog/TrackFilterTest.java
    catalog/TrackCatalogTest.java
    map/TrackMapMarkerTest.java
  src/test/resources/com/soundtracktracker/
    tracks-fixture.json
```

---

### Task 1: Scaffold the plugin repository

**Files:**
- Create: `build.gradle`, `settings.gradle`, `runelite-plugin.properties`, `CLAUDE.md`
- Copy from `../ItemSearch`: `gradle/`, `gradlew`, `gradlew.bat`, `.gitignore`, `LICENSE`, `AGENTS.md`
- Create: `src/main/java/com/soundtracktracker/SoundtrackTrackerPlugin.java`
- Create: `src/main/java/com/soundtracktracker/SoundtrackTrackerConfig.java`
- Create: `src/test/java/com/soundtracktracker/SoundtrackTrackerPluginTest.java`

**Interfaces:**
- Produces: `SoundtrackTrackerConfig` with `String GROUP = "soundtracktracker"`, `boolean showHolidayTracks()` (default true), `boolean hideUnlocked()` (default false). `SoundtrackTrackerPlugin extends Plugin` with `@PluginDescriptor`. Later tasks fill the plugin in.

- [ ] **Step 1: Copy the template files**

```bash
cd E:/projects/java/SoundtrackTracker
cp -r ../ItemSearch/gradle . && cp ../ItemSearch/gradlew ../ItemSearch/gradlew.bat ../ItemSearch/.gitignore ../ItemSearch/LICENSE ../ItemSearch/AGENTS.md .
mkdir -p src/main/java/com/soundtracktracker/catalog src/main/java/com/soundtracktracker/map src/main/java/com/soundtracktracker/ui src/main/resources/com/soundtracktracker src/test/java/com/soundtracktracker/catalog src/test/java/com/soundtracktracker/map src/test/resources/com/soundtracktracker tools
```

- [ ] **Step 2: Write `settings.gradle`, `runelite-plugin.properties`, `CLAUDE.md`**

`settings.gradle`:
```groovy
rootProject.name = 'soundtracktracker'
```

`runelite-plugin.properties`:
```properties
displayName=Soundtrack Tracker
author=propagating
description=Track which music tracks you have unlocked and how to unlock the rest for the music cape
tags=music,cape,soundtrack,tracks,unlock
version=
plugins=com.soundtracktracker.SoundtrackTrackerPlugin
build=standard
```

`CLAUDE.md`:
```markdown
@AGENTS.md
```

- [ ] **Step 3: Write `build.gradle`**

Copy `../ItemSearch/build.gradle` verbatim and change exactly two lines:

```groovy
def pluginMainClass = 'com.soundtracktracker.SoundtrackTrackerPluginTest'
```
```groovy
group = 'com.soundtracktracker'
```

Verify no other `itemsearch` text remains: `grep -n -i itemsearch build.gradle` prints nothing.

- [ ] **Step 4: Write the config interface**

`src/main/java/com/soundtracktracker/SoundtrackTrackerConfig.java` (header first):
```java
package com.soundtracktracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SoundtrackTrackerConfig.GROUP)
public interface SoundtrackTrackerConfig extends Config
{
	String GROUP = "soundtracktracker";

	@ConfigItem(
		keyName = "showHolidayTracks",
		name = "Show holiday tracks",
		description = "List holiday event tracks. They are never counted toward the music cape.",
		position = 1
	)
	default boolean showHolidayTracks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUnlocked",
		name = "Start on locked tracks",
		description = "Open the panel with the filter already set to locked tracks only",
		position = 2
	)
	default boolean hideUnlocked()
	{
		return false;
	}
}
```

- [ ] **Step 5: Write the empty plugin and the dev-client main**

`src/main/java/com/soundtracktracker/SoundtrackTrackerPlugin.java`:
```java
package com.soundtracktracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Soundtrack Tracker",
	description = "Track which music tracks you have unlocked and how to unlock the rest for the music cape",
	tags = {"music", "cape", "soundtrack", "tracks", "unlock"}
)
public class SoundtrackTrackerPlugin extends Plugin
{
	@Provides
	SoundtrackTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SoundtrackTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		log.info("Soundtrack Tracker started");
	}

	@Override
	protected void shutDown()
	{
		log.info("Soundtrack Tracker stopped");
	}
}
```

`src/test/java/com/soundtracktracker/SoundtrackTrackerPluginTest.java`:
```java
package com.soundtracktracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SoundtrackTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SoundtrackTrackerPlugin.class);
		RuneLite.main(args);
	}
}
```

- [ ] **Step 6: Build**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL` (no tests yet; compilation of main and test passes).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "chore: scaffold Soundtrack Tracker from the plugin hub template" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 2: Enrichment rows and the store

**Files:**
- Create: `src/main/java/com/soundtracktracker/catalog/TrackEnrichment.java`
- Create: `src/main/java/com/soundtracktracker/catalog/EnrichmentStore.java`
- Create: `src/test/resources/com/soundtracktracker/tracks-fixture.json`
- Test: `src/test/java/com/soundtracktracker/catalog/EnrichmentStoreTest.java`

**Interfaces:**
- Produces: `TrackEnrichment` (Lombok `@Data`) with fields `Integer cacheid`, `String name`, `String location`, `String region`, `String quest`, `String unlockdetail`, `Boolean members`, `MapPoint map`, `String wikiUrl`; nested `@Data static class MapPoint { int x; int y; int plane; }`; constant `TrackEnrichment.EMPTY`.
- Produces: `EnrichmentStore(Gson gson, String resourcePath)`, `void load()`, `void load(Reader reader)`, `TrackEnrichment find(int midiId, String name)` (never null), `int size()`.

- [ ] **Step 1: Write the fixture**

`src/test/resources/com/soundtracktracker/tracks-fixture.json`:
```json
[
  {
    "cacheid": 35,
    "name": "Sea Shanty 2",
    "location": "Port Sarim, Prifddinas docks, Tempoross Cove",
    "region": "Port Sarim",
    "quest": null,
    "unlockdetail": "Unlocked in Port Sarim.",
    "members": false,
    "map": {"x": 3035, "y": 3225, "plane": 0},
    "wikiUrl": "https://oldschool.runescape.wiki/w/Sea_Shanty_2"
  },
  {
    "cacheid": null,
    "name": "Nameless Track",
    "location": "Somewhere",
    "region": "Somewhere",
    "quest": "Some Quest",
    "unlockdetail": null,
    "members": true,
    "map": null,
    "wikiUrl": "https://oldschool.runescape.wiki/w/Nameless_Track"
  }
]
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/soundtracktracker/catalog/EnrichmentStoreTest.java`:
```java
package com.soundtracktracker.catalog;

import com.google.gson.Gson;
import java.io.StringReader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import org.junit.Test;

public class EnrichmentStoreTest
{
	private static final String FIXTURE = "/com/soundtracktracker/tracks-fixture.json";

	private EnrichmentStore loaded()
	{
		EnrichmentStore store = new EnrichmentStore(new Gson(), FIXTURE);
		store.load();
		return store;
	}

	@Test
	public void loadsFixtureRows()
	{
		assertEquals(2, loaded().size());
	}

	@Test
	public void findsByMidiIdFirst()
	{
		TrackEnrichment hit = loaded().find(35, "wrong name");
		assertEquals("Sea Shanty 2", hit.getName());
		assertEquals(3035, hit.getMap().getX());
		assertEquals(0, hit.getMap().getPlane());
		assertEquals(Boolean.FALSE, hit.getMembers());
	}

	@Test
	public void fallsBackToCaseInsensitiveName()
	{
		TrackEnrichment hit = loaded().find(-1, "  nameless TRACK ");
		assertEquals("Some Quest", hit.getQuest());
		assertNull(hit.getMap());
	}

	@Test
	public void unknownTrackYieldsEmpty()
	{
		assertSame(TrackEnrichment.EMPTY, loaded().find(999, "Not There"));
	}

	@Test
	public void missingResourceYieldsEmptyStore()
	{
		EnrichmentStore store = new EnrichmentStore(new Gson(), "/com/soundtracktracker/nope.json");
		store.load();
		assertEquals(0, store.size());
		assertSame(TrackEnrichment.EMPTY, store.find(35, "Sea Shanty 2"));
	}

	@Test
	public void malformedJsonYieldsEmptyStore()
	{
		EnrichmentStore store = new EnrichmentStore(new Gson(), FIXTURE);
		store.load(new StringReader("[{\"cacheid\": 35, "));
		assertEquals(0, store.size());
	}
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.EnrichmentStoreTest'`
Expected: compilation failure, `TrackEnrichment` and `EnrichmentStore` do not exist.

- [ ] **Step 4: Write `TrackEnrichment`**

```java
package com.soundtracktracker.catalog;

import lombok.Data;

/**
 * One row of tracks.json: what the wiki knows about a track that the game cache does not.
 * Field names match the JSON keys so Gson needs no adapters.
 */
@Data
public class TrackEnrichment
{
	/** Stands in for a track the wiki sweep did not cover; every getter returns null. */
	public static final TrackEnrichment EMPTY = new TrackEnrichment();

	private Integer cacheid;
	private String name;
	private String location;
	private String region;
	private String quest;
	private String unlockdetail;
	private Boolean members;
	private MapPoint map;
	private String wikiUrl;

	@Data
	public static class MapPoint
	{
		private int x;
		private int y;
		private int plane;
	}
}
```

- [ ] **Step 5: Write `EnrichmentStore`**

```java
package com.soundtracktracker.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled wiki enrichment and answers lookups by the game's midi id first, then by
 * display name. A missing or broken file leaves the store empty rather than failing the plugin.
 */
@Slf4j
public class EnrichmentStore
{
	private final Gson gson;
	private final String resourcePath;
	private final Map<Integer, TrackEnrichment> byMidi = new HashMap<>();
	private final Map<String, TrackEnrichment> byName = new HashMap<>();
	private int loaded;

	public EnrichmentStore(Gson gson, String resourcePath)
	{
		this.gson = gson;
		this.resourcePath = resourcePath;
	}

	/** Reads the bundled resource. Blocking file IO: call off the client thread. */
	public void load()
	{
		try (InputStream in = getClass().getResourceAsStream(resourcePath))
		{
			if (in == null)
			{
				log.warn("soundtracktracker: {} not found; tracks will show no locations", resourcePath);
				return;
			}
			load(new InputStreamReader(in, StandardCharsets.UTF_8));
		}
		catch (Exception e)
		{
			log.warn("soundtracktracker: failed to read {}", resourcePath, e);
		}
	}

	public void load(Reader reader)
	{
		byMidi.clear();
		byName.clear();
		loaded = 0;
		try
		{
			TrackEnrichment[] rows = gson.fromJson(reader, TrackEnrichment[].class);
			if (rows == null)
			{
				return;
			}
			for (TrackEnrichment row : rows)
			{
				if (row == null)
				{
					continue;
				}
				loaded++;
				if (row.getCacheid() != null)
				{
					byMidi.putIfAbsent(row.getCacheid(), row);
				}
				if (row.getName() != null)
				{
					byName.putIfAbsent(key(row.getName()), row);
				}
			}
		}
		catch (JsonParseException e)
		{
			byMidi.clear();
			byName.clear();
			loaded = 0;
			log.warn("soundtracktracker: tracks.json is malformed; ignoring it", e);
		}
	}

	/** Never null: {@link TrackEnrichment#EMPTY} when the wiki has nothing for this track. */
	public TrackEnrichment find(int midiId, String name)
	{
		TrackEnrichment hit = byMidi.get(midiId);
		if (hit == null && name != null)
		{
			hit = byName.get(key(name));
		}
		return hit == null ? TrackEnrichment.EMPTY : hit;
	}

	public int size()
	{
		return loaded;
	}

	private static String key(String name)
	{
		return name.trim().toLowerCase(Locale.ROOT);
	}
}
```

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.EnrichmentStoreTest'`
Expected: 6 tests pass. (Gson parses a truncated array as a `JsonSyntaxException`, a subclass of `JsonParseException`; if instead `malformedJsonYieldsEmptyStore` fails with an uncaught `java.io.EOFException` wrapped differently, widen the catch to `RuntimeException`.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: load wiki enrichment rows from tracks.json" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 3: Track value and cape progress

**Files:**
- Create: `src/main/java/com/soundtracktracker/catalog/Track.java`
- Create: `src/main/java/com/soundtracktracker/catalog/CapeProgress.java`
- Test: `src/test/java/com/soundtracktracker/catalog/CapeProgressTest.java`

**Interfaces:**
- Consumes: `TrackEnrichment` from Task 2.
- Produces: `Track` (Lombok `@Value @Builder(toBuilder = true)`) with `String name, String sortName, String hint, int midiId, int varp, int bit, boolean unlocked, boolean holiday, boolean automatic, TrackEnrichment enrichment`; methods `boolean countsForCape()`, `boolean hasMap()`.
- Produces: `CapeProgress.Progress` (`@Value`: `int unlocked, int required`, `boolean isComplete()`) and `static Progress CapeProgress.of(Collection<Track>)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/soundtracktracker/catalog/CapeProgressTest.java`:
```java
package com.soundtracktracker.catalog;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CapeProgressTest
{
	static Track track(String name, boolean unlocked, boolean holiday)
	{
		return Track.builder()
			.name(name).sortName(name).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(unlocked).holiday(holiday).automatic(false)
			.enrichment(TrackEnrichment.EMPTY)
			.build();
	}

	@Test
	public void countsOnlyNonHolidayTracks()
	{
		CapeProgress.Progress p = CapeProgress.of(Arrays.asList(
			track("A", true, false),
			track("B", false, false),
			track("Xmas", true, true),
			track("Easter", false, true)));
		assertEquals(1, p.getUnlocked());
		assertEquals(2, p.getRequired());
		assertFalse(p.isComplete());
	}

	@Test
	public void completeWhenEveryRequiredTrackIsUnlocked()
	{
		CapeProgress.Progress p = CapeProgress.of(Arrays.asList(
			track("A", true, false),
			track("Xmas", false, true)));
		assertEquals(1, p.getUnlocked());
		assertEquals(1, p.getRequired());
		assertTrue(p.isComplete());
	}

	@Test
	public void emptyCatalogIsNotComplete()
	{
		CapeProgress.Progress p = CapeProgress.of(Collections.emptyList());
		assertEquals(0, p.getRequired());
		assertFalse(p.isComplete());
	}

	@Test
	public void trackKnowsWhetherItHasAMapPoint()
	{
		TrackEnrichment withMap = new TrackEnrichment();
		withMap.setMap(new TrackEnrichment.MapPoint());
		assertTrue(track("A", false, false).toBuilder().enrichment(withMap).build().hasMap());
		assertFalse(track("A", false, false).hasMap());
	}
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.CapeProgressTest'`
Expected: compilation failure, `Track` and `CapeProgress` do not exist.

- [ ] **Step 3: Write `Track`**

```java
package com.soundtracktracker.catalog;

import lombok.Builder;
import lombok.Value;

/**
 * One music track as the panel sees it: the game's own row plus whatever the wiki adds.
 * Immutable; {@link TrackCatalog} publishes a fresh list whenever an unlock bit flips.
 */
@Value
@Builder(toBuilder = true)
public class Track
{
	String name;
	String sortName;
	/** The in-game right-click hint; empty string when the row has none. */
	String hint;
	/** Track (midi) id; joins to the wiki's cacheid. -1 when unknown. */
	int midiId;
	/** Player variable holding the unlock bit; -1 when the row has none. */
	int varp;
	/** Bit within {@link #varp}; -1 when the row has none. */
	int bit;
	boolean unlocked;
	boolean holiday;
	/** Unlocked for everyone on creation; always shown unlocked. */
	boolean automatic;
	/** Never null; {@link TrackEnrichment#EMPTY} when the wiki has nothing. */
	TrackEnrichment enrichment;

	public boolean countsForCape()
	{
		return !holiday;
	}

	public boolean hasMap()
	{
		return enrichment != null && enrichment.getMap() != null;
	}
}
```

- [ ] **Step 4: Write `CapeProgress`**

```java
package com.soundtracktracker.catalog;

import java.util.Collection;
import lombok.Value;

/** The music cape needs every non-holiday track; this counts how close the player is. */
public final class CapeProgress
{
	@Value
	public static class Progress
	{
		int unlocked;
		int required;

		public boolean isComplete()
		{
			return required > 0 && unlocked >= required;
		}
	}

	private CapeProgress()
	{
	}

	public static Progress of(Collection<Track> tracks)
	{
		int required = 0;
		int unlocked = 0;
		for (Track t : tracks)
		{
			if (!t.countsForCape())
			{
				continue;
			}
			required++;
			if (t.isUnlocked())
			{
				unlocked++;
			}
		}
		return new Progress(unlocked, required);
	}
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.CapeProgressTest'`
Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: track value and music cape progress count" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 4: Search, lock filter and sort

**Files:**
- Create: `src/main/java/com/soundtracktracker/catalog/TrackFilter.java`
- Test: `src/test/java/com/soundtracktracker/catalog/TrackFilterTest.java`

**Interfaces:**
- Consumes: `Track`, `TrackEnrichment`.
- Produces: `TrackFilter.LockFilter { ALL, LOCKED, UNLOCKED }` and `TrackFilter.SortMode { NAME, LOCATION }`, both with a human `toString()`; `static List<Track> TrackFilter.apply(Collection<Track> tracks, String query, LockFilter lock, SortMode sort, boolean showHoliday)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/soundtracktracker/catalog/TrackFilterTest.java`:
```java
package com.soundtracktracker.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static com.soundtracktracker.catalog.TrackFilter.LockFilter;
import static com.soundtracktracker.catalog.TrackFilter.SortMode;

public class TrackFilterTest
{
	private static Track track(String name, String sortName, boolean unlocked, boolean holiday, String region)
	{
		TrackEnrichment e = new TrackEnrichment();
		e.setRegion(region);
		return Track.builder()
			.name(name).sortName(sortName).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(unlocked).holiday(holiday).automatic(false).enrichment(e)
			.build();
	}

	private final List<Track> tracks = Arrays.asList(
		track("The Trade Parade", "Trade Parade, The", false, false, "Grand Exchange"),
		track("Adventure", "Adventure", true, false, "Lumbridge"),
		track("Sea Shanty 2", "Sea Shanty 2", false, false, "Port Sarim"),
		track("Scape Main", "Scape Main", true, false, null),
		track("Jungle Bells", "Jungle Bells", false, true, "Karamja"));

	private static List<String> names(List<Track> in)
	{
		return in.stream().map(Track::getName).collect(Collectors.toList());
	}

	@Test
	public void sortsByGameSortName()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.NAME, true);
		assertEquals(Arrays.asList("Adventure", "Jungle Bells", "Scape Main", "Sea Shanty 2", "The Trade Parade"), names(out));
	}

	@Test
	public void queryIsCaseInsensitiveSubstring()
	{
		List<Track> out = TrackFilter.apply(tracks, "  SHANTY ", LockFilter.ALL, SortMode.NAME, true);
		assertEquals(Arrays.asList("Sea Shanty 2"), names(out));
	}

	@Test
	public void lockFilterKeepsOnlyMatchingState()
	{
		assertEquals(Arrays.asList("Jungle Bells", "Sea Shanty 2", "The Trade Parade"),
			names(TrackFilter.apply(tracks, "", LockFilter.LOCKED, SortMode.NAME, true)));
		assertEquals(Arrays.asList("Adventure", "Scape Main"),
			names(TrackFilter.apply(tracks, "", LockFilter.UNLOCKED, SortMode.NAME, true)));
	}

	@Test
	public void holidayTracksCanBeHidden()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.NAME, false);
		assertEquals(Arrays.asList("Adventure", "Scape Main", "Sea Shanty 2", "The Trade Parade"), names(out));
	}

	@Test
	public void locationSortGroupsByRegionWithUnknownLast()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.LOCATION, true);
		assertEquals(Arrays.asList("The Trade Parade", "Jungle Bells", "Adventure", "Sea Shanty 2", "Scape Main"), names(out));
	}

	@Test
	public void nullQueryMatchesEverything()
	{
		assertEquals(5, TrackFilter.apply(tracks, null, LockFilter.ALL, SortMode.NAME, true).size());
	}
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.TrackFilterTest'`
Expected: compilation failure, `TrackFilter` does not exist.

- [ ] **Step 3: Write `TrackFilter`**

```java
package com.soundtracktracker.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure list shaping for the panel: which tracks to show and in what order. */
public final class TrackFilter
{
	public enum LockFilter
	{
		ALL("All"),
		LOCKED("Locked"),
		UNLOCKED("Unlocked");

		private final String label;

		LockFilter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum SortMode
	{
		NAME("Name"),
		LOCATION("Location");

		private final String label;

		SortMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private static final Comparator<Track> BY_NAME = Comparator
		.comparing((Track t) -> lower(t.getSortName() == null ? t.getName() : t.getSortName()))
		.thenComparing(Track::getName);

	// tracks with no known region sink to the bottom rather than forming a blank group on top
	private static final Comparator<Track> BY_LOCATION = Comparator
		.comparing((Track t) -> t.getEnrichment().getRegion() == null)
		.thenComparing(t -> lower(nullToEmpty(t.getEnrichment().getRegion())))
		.thenComparing(BY_NAME);

	private TrackFilter()
	{
	}

	public static List<Track> apply(Collection<Track> tracks, String query, LockFilter lock,
		SortMode sort, boolean showHoliday)
	{
		String q = query == null ? "" : lower(query.trim());
		List<Track> out = new ArrayList<>();
		for (Track t : tracks)
		{
			if (!showHoliday && t.isHoliday())
			{
				continue;
			}
			if (lock == LockFilter.LOCKED && t.isUnlocked())
			{
				continue;
			}
			if (lock == LockFilter.UNLOCKED && !t.isUnlocked())
			{
				continue;
			}
			if (!q.isEmpty() && !lower(t.getName()).contains(q))
			{
				continue;
			}
			out.add(t);
		}
		out.sort(sort == SortMode.LOCATION ? BY_LOCATION : BY_NAME);
		return out;
	}

	private static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}

	private static String nullToEmpty(String s)
	{
		return s == null ? "" : s;
	}
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.TrackFilterTest'`
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: search, lock filter and sort for the track list" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 5: Track catalog from the game's music table

**Files:**
- Create: `src/main/java/com/soundtracktracker/catalog/TrackCatalog.java`
- Test: `src/test/java/com/soundtracktracker/catalog/TrackCatalogTest.java`

**Interfaces:**
- Consumes: `Client.getDBTableRows(int)`, `Client.getDBTableField(int row, int column, int tupleIndex)`, `Client.getVarpValue(int)`, `DBTableID.Music.*`, `EnrichmentStore.find`, `Track`.
- Produces: `TrackCatalog(Client client, EnrichmentStore enrichment)`; `interface Listener { void onTracks(List<Track> tracks); }`; `addListener`, `removeListener`; `void load()` (client thread), `void onVarpChanged(int varpId)` (client thread), `void reset()`, `List<Track> snapshot()`.

Column semantics being relied on (see spec): `COL_DISPLAYNAME`, `COL_SORTNAME`, `COL_UNLOCKHINT` are strings at tuple 0; `COL_MIDI` is an integer at tuple 0; `COL_VARIABLE` is a two-element tuple, element 0 the varp id and element 1 the bit; `COL_HIDDEN`, `COL_HOLIDAY`, `COL_AUTOMATIC_UNLOCK` are booleans that the cache may surface as `Boolean` or as `Integer` 0/1. Task 6 verifies the varp/bit reading against the in-game count.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/soundtracktracker/catalog/TrackCatalogTest.java`:
```java
package com.soundtracktracker.catalog;

import com.google.gson.Gson;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackCatalogTest
{
	private static final int VARP_A = 20;
	private static final int VARP_B = 21;

	@Mock
	private Client client;

	private TrackCatalog catalog;
	private final List<List<Track>> published = new ArrayList<>();

	/** Rows: 1 locked bit0/varpA, 2 unlocked bit3/varpA, 3 hidden, 4 holiday bit0/varpB, 5 automatic, 6 no name. */
	@Before
	public void setUp()
	{
		lenient().when(client.getDBTableRows(DBTableID.Music.ID)).thenReturn(Arrays.asList(1, 2, 3, 4, 5, 6));
		row(1, "Sea Shanty 2", "Sea Shanty 2", "This track unlocks in Port Sarim.", 35, VARP_A, 0, false, false, false);
		row(2, "The Trade Parade", "Trade Parade, The", "Grand Exchange.", 40, VARP_A, 3, false, false, false);
		row(3, "Hidden One", "Hidden One", "", 41, VARP_A, 4, true, false, false);
		row(4, "Jungle Bells", "Jungle Bells", "Christmas.", 42, VARP_B, 0, false, true, false);
		row(5, "Scape Main", "Scape Main", "Automatic.", 43, -1, -1, false, false, true);
		row(6, null, null, null, 44, VARP_A, 5, false, false, false);
		lenient().when(client.getVarpValue(VARP_A)).thenReturn(1 << 3);
		lenient().when(client.getVarpValue(VARP_B)).thenReturn(0);

		EnrichmentStore store = new EnrichmentStore(new Gson(), "/unused.json");
		store.load(new StringReader("[{\"cacheid\":35,\"name\":\"Sea Shanty 2\",\"region\":\"Port Sarim\"}]"));
		catalog = new TrackCatalog(client, store);
		catalog.addListener(published::add);
	}

	private void row(int id, String display, String sort, String hint, int midi, int varp, int bit,
		boolean hidden, boolean holiday, boolean automatic)
	{
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_DISPLAYNAME, 0))
			.thenReturn(display == null ? new Object[0] : new Object[]{display});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_SORTNAME, 0))
			.thenReturn(sort == null ? null : new Object[]{sort});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_UNLOCKHINT, 0))
			.thenReturn(hint == null ? null : new Object[]{hint});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_MIDI, 0)).thenReturn(new Object[]{midi});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_VARIABLE, 0))
			.thenReturn(varp < 0 ? new Object[0] : new Object[]{varp});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_VARIABLE, 1))
			.thenReturn(bit < 0 ? new Object[0] : new Object[]{bit});
		// booleans arrive as Integer from the cache on some rows and Boolean on others
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_HIDDEN, 0)).thenReturn(new Object[]{hidden ? 1 : 0});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_HOLIDAY, 0)).thenReturn(new Object[]{holiday});
		lenient().when(client.getDBTableField(id, DBTableID.Music.COL_AUTOMATIC_UNLOCK, 0)).thenReturn(new Object[]{automatic ? 1 : 0});
	}

	private Track byName(List<Track> tracks, String name)
	{
		return tracks.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	public void loadSkipsHiddenAndNamelessRows()
	{
		catalog.load();
		List<Track> tracks = catalog.snapshot();
		assertEquals(4, tracks.size());
		assertFalse(tracks.stream().anyMatch(t -> "Hidden One".equals(t.getName())));
	}

	@Test
	public void loadEvaluatesUnlockBits()
	{
		catalog.load();
		List<Track> tracks = catalog.snapshot();
		assertFalse(byName(tracks, "Sea Shanty 2").isUnlocked());
		assertTrue(byName(tracks, "The Trade Parade").isUnlocked());
		assertFalse(byName(tracks, "Jungle Bells").isUnlocked());
		assertTrue(byName(tracks, "Jungle Bells").isHoliday());
		assertTrue(byName(tracks, "Scape Main").isUnlocked());
		assertTrue(byName(tracks, "Scape Main").isAutomatic());
	}

	@Test
	public void loadJoinsEnrichmentAndFillsDefaults()
	{
		catalog.load();
		Track shanty = byName(catalog.snapshot(), "Sea Shanty 2");
		assertEquals("Port Sarim", shanty.getEnrichment().getRegion());
		assertEquals("This track unlocks in Port Sarim.", shanty.getHint());
		Track parade = byName(catalog.snapshot(), "The Trade Parade");
		assertSame(TrackEnrichment.EMPTY, parade.getEnrichment());
		assertEquals("Trade Parade, The", parade.getSortName());
	}

	@Test
	public void loadPublishesOnce()
	{
		catalog.load();
		assertEquals(1, published.size());
		assertEquals(4, published.get(0).size());
	}

	@Test
	public void varpChangeFlipsOnlyAffectedTracks()
	{
		catalog.load();
		when(client.getVarpValue(VARP_A)).thenReturn((1 << 3) | 1);
		catalog.onVarpChanged(VARP_A);
		assertEquals(2, published.size());
		assertTrue(byName(catalog.snapshot(), "Sea Shanty 2").isUnlocked());
		assertFalse(byName(catalog.snapshot(), "Jungle Bells").isUnlocked());
	}

	@Test
	public void varpChangeWithoutFlipDoesNotPublish()
	{
		catalog.load();
		catalog.onVarpChanged(VARP_A);
		catalog.onVarpChanged(999);
		assertEquals(1, published.size());
	}

	@Test
	public void resetPublishesEmptyAndLoadRestores()
	{
		catalog.load();
		catalog.reset();
		assertTrue(catalog.snapshot().isEmpty());
		assertEquals(2, published.size());
		catalog.load();
		assertEquals(4, catalog.snapshot().size());
	}

	@Test
	public void varpChangeBeforeLoadIsIgnored()
	{
		catalog.onVarpChanged(VARP_A);
		assertTrue(published.isEmpty());
	}
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.TrackCatalogTest'`
Expected: compilation failure, `TrackCatalog` does not exist.

- [ ] **Step 3: Write `TrackCatalog`**

```java
package com.soundtracktracker.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

/**
 * The game's own list of music tracks and which of them this player has unlocked.
 *
 * <p>Rows come from the client's music database table and are read once per client run; the
 * unlock bits come from player variables and are re-read on every login and on every change to
 * a variable some track uses. Listeners receive an immutable snapshot each time it changes.
 * All methods except the listener registration must run on the client thread.
 */
@Slf4j
public class TrackCatalog
{
	public interface Listener
	{
		void onTracks(List<Track> tracks);
	}

	private final Client client;
	private final EnrichmentStore enrichment;
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();
	// the table as read, unlock flags all false; the order is the order of snapshots
	private List<Track> rows = Collections.emptyList();
	// varp id -> indexes into rows/tracks of the tracks whose bit lives in that varp
	private final Map<Integer, List<Integer>> indexesByVarp = new HashMap<>();
	private boolean rowsLoaded;
	private List<Track> tracks = Collections.emptyList();

	public TrackCatalog(Client client, EnrichmentStore enrichment)
	{
		this.client = client;
		this.enrichment = enrichment;
	}

	public void addListener(Listener listener)
	{
		listeners.add(listener);
	}

	public void removeListener(Listener listener)
	{
		listeners.remove(listener);
	}

	/** The latest published list; empty until {@link #load()} has run. */
	public List<Track> snapshot()
	{
		return tracks;
	}

	/** Reads the table on the first call, then evaluates every unlock bit and publishes. */
	public void load()
	{
		if (!rowsLoaded)
		{
			readRows();
			rowsLoaded = true;
		}
		List<Track> next = new ArrayList<>(rows.size());
		for (Track row : rows)
		{
			next.add(row.toBuilder().unlocked(isUnlocked(row)).build());
		}
		publish(next);
	}

	/** Re-evaluates the tracks stored in this varp; publishes only if one of them flipped. */
	public void onVarpChanged(int varpId)
	{
		if (tracks.isEmpty())
		{
			return;
		}
		List<Integer> indexes = indexesByVarp.get(varpId);
		if (indexes == null)
		{
			return;
		}
		List<Track> next = null;
		for (int i : indexes)
		{
			Track current = tracks.get(i);
			boolean now = isUnlocked(current);
			if (now != current.isUnlocked())
			{
				if (next == null)
				{
					next = new ArrayList<>(tracks);
				}
				next.set(i, current.toBuilder().unlocked(now).build());
			}
		}
		if (next != null)
		{
			publish(next);
		}
	}

	/** Forgets this player's unlock state (logout); the table itself is kept. */
	public void reset()
	{
		publish(Collections.emptyList());
	}

	private void publish(List<Track> next)
	{
		tracks = Collections.unmodifiableList(next);
		for (Listener listener : listeners)
		{
			listener.onTracks(tracks);
		}
	}

	private void readRows()
	{
		List<Track> out = new ArrayList<>();
		indexesByVarp.clear();
		int skipped = 0;
		for (int row : client.getDBTableRows(DBTableID.Music.ID))
		{
			String name = string(row, DBTableID.Music.COL_DISPLAYNAME);
			if (name == null || name.isEmpty())
			{
				skipped++;
				continue;
			}
			if (bool(row, DBTableID.Music.COL_HIDDEN))
			{
				continue;
			}
			String sortName = string(row, DBTableID.Music.COL_SORTNAME);
			String hint = string(row, DBTableID.Music.COL_UNLOCKHINT);
			int midi = integer(row, DBTableID.Music.COL_MIDI, 0);
			int varp = integer(row, DBTableID.Music.COL_VARIABLE, 0);
			int bit = integer(row, DBTableID.Music.COL_VARIABLE, 1);
			Track track = Track.builder()
				.name(name)
				.sortName(sortName == null ? name : sortName)
				.hint(hint == null ? "" : hint)
				.midiId(midi)
				.varp(varp)
				.bit(bit)
				.unlocked(false)
				.holiday(bool(row, DBTableID.Music.COL_HOLIDAY))
				.automatic(bool(row, DBTableID.Music.COL_AUTOMATIC_UNLOCK))
				.enrichment(enrichment.find(midi, name))
				.build();
			if (varp >= 0 && bit >= 0)
			{
				indexesByVarp.computeIfAbsent(varp, k -> new ArrayList<>()).add(out.size());
			}
			out.add(track);
		}
		rows = out;
		log.debug("soundtracktracker: read {} tracks across {} varps, skipped {} rows without a name",
			out.size(), indexesByVarp.size(), skipped);
	}

	private boolean isUnlocked(Track track)
	{
		if (track.isAutomatic())
		{
			return true;
		}
		if (track.getVarp() < 0 || track.getBit() < 0)
		{
			return false;
		}
		return ((client.getVarpValue(track.getVarp()) >> track.getBit()) & 1) != 0;
	}

	private String string(int row, int column)
	{
		Object[] values = field(row, column, 0);
		return values != null && values.length > 0 && values[0] instanceof String ? (String) values[0] : null;
	}

	/** -1 when the row has no such value. */
	private int integer(int row, int column, int tupleIndex)
	{
		Object[] values = field(row, column, tupleIndex);
		return values != null && values.length > 0 && values[0] instanceof Integer ? (Integer) values[0] : -1;
	}

	private boolean bool(int row, int column)
	{
		Object[] values = field(row, column, 0);
		if (values == null || values.length == 0)
		{
			return false;
		}
		Object v = values[0];
		if (v instanceof Boolean)
		{
			return (Boolean) v;
		}
		return v instanceof Integer && (Integer) v != 0;
	}

	// a row that lacks the column entirely can surface as an exception from the cache reader;
	// treat it the same as an empty value so one odd row cannot take the whole table down
	private Object[] field(int row, int column, int tupleIndex)
	{
		try
		{
			return client.getDBTableField(row, column, tupleIndex);
		}
		catch (RuntimeException e)
		{
			log.debug("soundtracktracker: row {} column {} unreadable", row, column, e);
			return null;
		}
	}
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests 'com.soundtracktracker.catalog.TrackCatalogTest'`
Expected: 8 tests pass.

- [ ] **Step 5: Run the whole suite and commit**

Run: `./gradlew test`
Expected: all tests pass.

```bash
git add -A
git commit -m "feat: read the music table and unlock bits into a track catalog" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 6: Wire the plugin and verify the unlock count in-game

This task ships a minimal panel showing only the progress header so the user can compare the count with the in-game music tab. The unlock-bit reading is the one assumption in the design that only the live client can confirm, so it is verified before any list UI is built on top of it.

**Files:**
- Create: `src/main/java/com/soundtracktracker/ui/ProgressHeader.java`
- Create: `src/main/java/com/soundtracktracker/ui/SoundtrackTrackerPanel.java` (first version)
- Create: `src/main/resources/com/soundtracktracker/panel_icon.png` via `tools/make_icon.py`
- Modify: `src/main/java/com/soundtracktracker/SoundtrackTrackerPlugin.java`

**Interfaces:**
- Consumes: `TrackCatalog`, `EnrichmentStore`, `CapeProgress`, `SoundtrackTrackerConfig`.
- Produces: `ProgressHeader extends JPanel` with `void update(CapeProgress.Progress p)`. `SoundtrackTrackerPanel extends PluginPanel` with constructor `(SoundtrackTrackerConfig config)` for now (Task 9 adds the map marker) and `void setTracks(List<Track> tracks)`.
- Produces in the plugin: `static final String TRACKS_RESOURCE = "/com/soundtracktracker/tracks.json"`.

- [ ] **Step 1: Write the icon generator and run it**

`tools/make_icon.py` (standard library only; writes a 16x16 RGBA music note):
```python
#!/usr/bin/env python3
"""Write src/main/resources/com/soundtracktracker/panel_icon.png: a 16x16 eighth note."""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "src/main/resources/com/soundtracktracker/panel_icon.png"

# '#' is an opaque pixel, '.' transparent
GLYPH = [
    "......###.......",
    "......####......",
    "......##.##.....",
    "......##..##....",
    "......##...##...",
    "......##....##..",
    "......##...##...",
    "......##..##....",
    "......##........",
    "......##........",
    "......##........",
    "...#####........",
    ".#######........",
    ".#######........",
    "..#####.........",
    "................",
]
COLOR = (255, 152, 31)  # RuneLite brand orange


def png_chunk(kind: bytes, data: bytes) -> bytes:
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def main() -> None:
    height = len(GLYPH)
    width = len(GLYPH[0])
    raw = bytearray()
    for row in GLYPH:
        raw.append(0)  # filter type: none
        for ch in row:
            raw.extend(COLOR + ((255,) if ch == "#" else (0,)))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", ihdr) + png_chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + png_chunk(b"IEND", b"")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_bytes(png)
    print(f"wrote {OUT} ({len(png)} bytes)")


if __name__ == "__main__":
    main()
```

Run: `python tools/make_icon.py`
Expected: `wrote .../panel_icon.png (N bytes)` and `file src/main/resources/com/soundtracktracker/panel_icon.png` reports `PNG image data, 16 x 16`.

- [ ] **Step 2: Write `ProgressHeader`**

`src/main/java/com/soundtracktracker/ui/ProgressHeader.java`:
```java
package com.soundtracktracker.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.CapeProgress;
import net.runelite.client.ui.ColorScheme;

/** "412 / 869 tracks" and a bar: how far the player is from the music cape. */
public class ProgressHeader extends JPanel
{
	static final String LOGGED_OUT = "Log in to load your tracks";

	private final JLabel label = new JLabel(LOGGED_OUT);
	private final JProgressBar bar = new JProgressBar();

	public ProgressHeader()
	{
		setLayout(new BorderLayout(0, 4));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(Color.WHITE);
		bar.setPreferredSize(new Dimension(0, 8));
		bar.setBorderPainted(false);
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		add(label, BorderLayout.NORTH);
		add(bar, BorderLayout.CENTER);
	}

	public void update(CapeProgress.Progress progress)
	{
		if (progress.getRequired() == 0)
		{
			label.setText(LOGGED_OUT);
			bar.setMaximum(1);
			bar.setValue(0);
			return;
		}
		label.setText(progress.getUnlocked() + " / " + progress.getRequired() + " tracks for the cape");
		bar.setMaximum(progress.getRequired());
		bar.setValue(progress.getUnlocked());
		bar.setForeground(progress.isComplete()
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
	}
}
```

- [ ] **Step 3: Write the first `SoundtrackTrackerPanel`**

`src/main/java/com/soundtracktracker/ui/SoundtrackTrackerPanel.java`:
```java
package com.soundtracktracker.ui;

import java.awt.BorderLayout;
import java.util.Collections;
import java.util.List;
import com.soundtracktracker.SoundtrackTrackerConfig;
import com.soundtracktracker.catalog.CapeProgress;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** The side panel. Swing thread only. */
public class SoundtrackTrackerPanel extends PluginPanel
{
	private final SoundtrackTrackerConfig config;
	private final ProgressHeader header = new ProgressHeader();
	private List<Track> tracks = Collections.emptyList();

	public SoundtrackTrackerPanel(SoundtrackTrackerConfig config)
	{
		super(false);
		this.config = config;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(header, BorderLayout.NORTH);
	}

	/** A new catalog snapshot; an empty list means logged out. */
	public void setTracks(List<Track> tracks)
	{
		this.tracks = tracks;
		header.update(CapeProgress.of(tracks));
	}
}
```

- [ ] **Step 4: Wire the plugin**

Replace the body of `SoundtrackTrackerPlugin.java` (keep the header and package line):
```java
package com.soundtracktracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import com.soundtracktracker.catalog.EnrichmentStore;
import com.soundtracktracker.catalog.TrackCatalog;
import com.soundtracktracker.ui.SoundtrackTrackerPanel;

@Slf4j
@PluginDescriptor(
	name = "Soundtrack Tracker",
	description = "Track which music tracks you have unlocked and how to unlock the rest for the music cape",
	tags = {"music", "cape", "soundtrack", "tracks", "unlock"}
)
public class SoundtrackTrackerPlugin extends Plugin
{
	static final String TRACKS_RESOURCE = "/com/soundtracktracker/tracks.json";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private Gson gson;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private SoundtrackTrackerConfig config;

	private EnrichmentStore enrichmentStore;
	private TrackCatalog catalog;
	private SoundtrackTrackerPanel panel;
	private NavigationButton navButton;
	// set on the client thread once tracks.json is parsed; the catalog must not read rows
	// before then or every track would be joined to an empty enrichment for the whole run
	private boolean dataReady;

	private final TrackCatalog.Listener toPanel = tracks ->
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setTracks(tracks);
			}
		});

	@Provides
	SoundtrackTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SoundtrackTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		dataReady = false;
		enrichmentStore = new EnrichmentStore(gson, TRACKS_RESOURCE);
		catalog = new TrackCatalog(client, enrichmentStore);
		catalog.addListener(toPanel);

		panel = new SoundtrackTrackerPanel(config);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Soundtrack Tracker")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// parse the bundled JSON off the client thread, then load the catalog if already in game
		final EnrichmentStore store = enrichmentStore;
		final TrackCatalog cat = catalog;
		executor.execute(() ->
		{
			store.load();
			clientThread.invoke(() ->
			{
				if (catalog != cat)
				{
					return; // plugin was stopped while we were parsing
				}
				dataReady = true;
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					cat.load();
				}
			});
		});
	}

	@Override
	protected void shutDown()
	{
		if (catalog != null)
		{
			catalog.removeListener(toPanel);
			catalog = null;
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel = null;
		enrichmentStore = null;
		dataReady = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (catalog == null)
		{
			return;
		}
		if (event.getGameState() == GameState.LOGGED_IN && dataReady)
		{
			catalog.load();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			catalog.reset();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (catalog != null && event.getVarpId() >= 0)
		{
			catalog.onVarpChanged(event.getVarpId());
		}
	}
}
```

- [ ] **Step 5: Build and run the full suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`, all catalog tests still pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: wire the catalog into a panel that shows cape progress" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

- [ ] **Step 7: In-game verification of the unlock encoding (user)**

Do not mark this task done on a clean JVM start. Offer to launch the dev client with `./gradlew run` from the repo root, point the user at https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts for logging in, and ask them to check:

1. Enable "Soundtrack Tracker" in the plugin list; the orange note icon appears in the sidebar.
2. Log in. The header changes from "Log in to load your tracks" to "N / M tracks for the cape".
3. Open the in-game music tab. Its header shows an unlocked count. `N` must equal the in-game unlocked count minus any unlocked holiday tracks, and `M` must equal the in-game total minus holiday tracks. If `N` is wildly off (0, or equal to `M`), the varp/bit tuple order is reversed: swap the tuple indexes in `TrackCatalog.readRows` (`integer(row, COL_VARIABLE, 1)` for varp and `0` for bit), rerun, and re-check.
4. Walk into an area with a locked track. When the unlock message appears in chat, `N` increases by one within a game tick.
5. Log out to the login screen: the header returns to "Log in to load your tracks". Log back in: the count returns.

Record the observed `N / M` and the in-game figures in the commit message of the next task if any adjustment was needed.

---

### Task 7: Wiki fetch script and the bundled tracks.json

**Files:**
- Create: `tools/fetch_tracks.py`
- Create: `tools/test_fetch_tracks.py`
- Create (generated): `src/main/resources/com/soundtracktracker/tracks.json`

**Interfaces:**
- Produces: `tracks.json`, a JSON array of objects with keys exactly `cacheid, name, location, region, quest, unlockdetail, members, map, wikiUrl` matching `TrackEnrichment` (Task 2). `map` is `{"x": int, "y": int, "plane": int}` or `null`.
- Script functions under test: `split_top_level(s)`, `find_template(text, name)`, `split_params(body)`, `strip_markup(s)`, `normalize_optional(s)`, `first_link(s)`, `yes_no(s)`, `parse_map(value)`, `build_record(title, wikitext)`.

- [ ] **Step 1: Write the failing tests**

`tools/test_fetch_tracks.py`:
```python
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


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd tools && python -m unittest test_fetch_tracks -v`
Expected: `ModuleNotFoundError: No module named 'fetch_tracks'`.

- [ ] **Step 3: Write `fetch_tracks.py`**

```python
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


def strip_markup(s):
    if s is None:
        return None
    s = re.sub(r"<!--.*?-->", "", s, flags=re.S)
    s = re.sub(r"<ref[^>]*/>", "", s)
    s = re.sub(r"<ref[^>]*>.*?</ref>", "", s, flags=re.S)
    s = re.sub(r"<br\s*/?>", ", ", s, flags=re.I)
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


def parse_map(value):
    """{x, y, plane} for the first {{Map}}: centroid of a polygon's vertices, else the pin."""
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
    return {"x": int(round(x)), "y": int(round(y)), "plane": plane}


# ---------- records ----------

def build_record(title, wikitext):
    body = find_template(wikitext, "Infobox Music")
    if body is None:
        return None
    p = split_params(body)
    cacheid_raw = (p.get("cacheid") or "").strip()
    location_raw = p.get("location")
    location = normalize_optional(strip_markup(location_raw))
    return {
        "cacheid": int(cacheid_raw) if cacheid_raw.isdigit() else None,
        "name": strip_markup(p.get("name")) or title,
        "location": location,
        "region": first_link(location_raw) if location else None,
        "quest": normalize_optional(strip_markup(p.get("quest"))),
        "unlockdetail": normalize_optional(strip_markup(p.get("unlockdetail"))),
        "members": yes_no(p.get("members")),
        "map": parse_map(p.get("map")),
        "wikiUrl": WIKI + urllib.parse.quote(title.replace(" ", "_")),
    }


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


def main(argv):
    out = Path(argv[1]) if len(argv) > 1 else DEFAULT_OUT
    pages = records = 0
    rows = []
    for title, text in fetch_pages():
        pages += 1
        rec = build_record(title, text)
        if rec is None:
            continue
        records += 1
        rows.append(rec)
    rows.sort(key=lambda r: r["name"].lower())
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(rows, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    with_cache = sum(1 for r in rows if r["cacheid"] is not None)
    with_map = sum(1 for r in rows if r["map"])
    no_loc = sum(1 for r in rows if not r["location"])
    print(f"pages {pages}, records {records}, with cacheid {with_cache}, with map {with_map}, without location {no_loc}")
    print(f"wrote {out}")


if __name__ == "__main__":
    main(sys.argv)
```

- [ ] **Step 4: Run the unit tests**

Run: `cd tools && python -m unittest test_fetch_tracks -v`
Expected: all 16 tests pass.

- [ ] **Step 5: Run the sweep and sanity-check the output**

Run: `python tools/fetch_tracks.py`
Expected: a summary line with `pages` roughly 900, `with cacheid` within a few dozen of `records`, `with map` the large majority.

Then check known rows:
```bash
python - <<'EOF'
import json
rows = json.load(open("src/main/resources/com/soundtracktracker/tracks.json", encoding="utf-8"))
byname = {r["name"]: r for r in rows}
print(byname["Sea Shanty 2"])
print(byname["Scape Main"]["map"], byname["Scape Main"]["cacheid"])
print(len(rows), sum(1 for r in rows if r["cacheid"] is None), "without cacheid")
EOF
```
Expected: Sea Shanty 2 has `cacheid` 35, region `Port Sarim`, a map near x 3040, y 3230, plane 0. If a large share lacks `cacheid`, open two of those pages on the wiki and adjust `split_params` for whatever formatting they use before committing.

- [ ] **Step 6: Run the Java suite (the fixture still loads) and commit**

Run: `./gradlew test`
Expected: pass.

```bash
git add -A
git commit -m "feat: wiki sweep script and bundled tracks.json" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 8: World-map marker

**Files:**
- Copy: `../ItemSearch/src/main/resources/com/itemsearch/map_marker.png` to `src/main/resources/com/soundtracktracker/map_marker.png`
- Create: `src/main/java/com/soundtracktracker/map/TrackMapMarker.java`
- Test: `src/test/java/com/soundtracktracker/map/TrackMapMarkerTest.java`

**Interfaces:**
- Consumes: `Track.hasMap()`, `Track.getEnrichment().getMap()`, `WorldMapPointManager`, `Client.getWorldMap()`, `ClientThread.invokeLater`.
- Produces: `TrackMapMarker(Client client, ClientThread clientThread, WorldMapPointManager manager, BufferedImage icon)`, `void show(Track track)`, `void clear()`. Both EDT-safe.

- [ ] **Step 1: Copy the marker icon**

```bash
cp ../ItemSearch/src/main/resources/com/itemsearch/map_marker.png src/main/resources/com/soundtracktracker/map_marker.png
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/soundtracktracker/map/TrackMapMarkerTest.java`:
```java
package com.soundtracktracker.map;

import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackEnrichment;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackMapMarkerTest
{
	@Mock
	private Client client;
	@Mock
	private ClientThread clientThread;
	@Mock
	private WorldMapPointManager manager;
	@Mock
	private WorldMap worldMap;

	private TrackMapMarker marker;

	private static Track track(String name, Integer x, Integer y, int plane)
	{
		TrackEnrichment e = new TrackEnrichment();
		if (x != null)
		{
			TrackEnrichment.MapPoint p = new TrackEnrichment.MapPoint();
			p.setX(x);
			p.setY(y);
			p.setPlane(plane);
			e.setMap(p);
		}
		return Track.builder().name(name).sortName(name).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(false).holiday(false).automatic(false).enrichment(e).build();
	}

	@Before
	public void setUp()
	{
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		when(client.getWorldMap()).thenReturn(worldMap);
		marker = new TrackMapMarker(client, clientThread, manager,
			new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
	}

	@Test
	public void showAddsOnePointAndPansToIt()
	{
		marker.show(track("Sea Shanty 2", 3035, 3225, 0));
		ArgumentCaptor<WorldMapPoint> point = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(manager).add(point.capture());
		assertEquals(new WorldPoint(3035, 3225, 0), point.getValue().getWorldPoint());
		assertEquals("Sea Shanty 2", point.getValue().getName());
		verify(worldMap).setWorldMapPositionTarget(new WorldPoint(3035, 3225, 0));
	}

	@Test
	public void showReplacesThePreviousPoint()
	{
		marker.show(track("A", 1, 2, 0));
		marker.show(track("B", 3, 4, 1));
		ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(manager, org.mockito.Mockito.times(2)).add(added.capture());
		verify(manager).remove(added.getAllValues().get(0));
	}

	@Test
	public void trackWithoutMapOnlyClears()
	{
		marker.show(track("A", 1, 2, 0));
		marker.show(track("No map", null, null, 0));
		verify(manager, org.mockito.Mockito.times(1)).add(any(WorldMapPoint.class));
		verify(manager, org.mockito.Mockito.times(1)).remove(any(WorldMapPoint.class));
	}

	@Test
	public void clearWithoutShowDoesNothing()
	{
		marker.clear();
		verify(manager, never()).remove(any(WorldMapPoint.class));
	}

	@Test
	public void missingIconSkipsTheMarker()
	{
		TrackMapMarker noIcon = new TrackMapMarker(client, clientThread, manager, null);
		noIcon.show(track("A", 1, 2, 0));
		verify(manager, never()).add(any(WorldMapPoint.class));
	}
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew test --tests 'com.soundtracktracker.map.TrackMapMarkerTest'`
Expected: compilation failure, `TrackMapMarker` does not exist.

- [ ] **Step 4: Write `TrackMapMarker`**

```java
package com.soundtracktracker.map;

import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackEnrichment;

/**
 * Sole owner of this plugin's world-map state: one marker for the track the player is looking
 * at, and the pan target so opening the map centres on it. EDT-safe; the pan target crosses to
 * the client thread.
 */
@Slf4j
public class TrackMapMarker
{
	private final Client client;
	private final ClientThread clientThread;
	private final WorldMapPointManager manager;
	private final BufferedImage icon;
	private WorldMapPoint shown;

	public TrackMapMarker(Client client, ClientThread clientThread, WorldMapPointManager manager,
		BufferedImage icon)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.manager = manager;
		this.icon = icon;
	}

	/** Replaces any earlier marker with this track's, or just clears when it has no map point. */
	public void show(Track track)
	{
		clear();
		TrackEnrichment.MapPoint map = track.getEnrichment().getMap();
		if (map == null)
		{
			return;
		}
		if (icon == null)
		{
			log.warn("soundtracktracker: marker icon missing, not marking map");
			return;
		}
		WorldPoint target = new WorldPoint(map.getX(), map.getY(), map.getPlane());
		WorldMapPoint point = WorldMapPoint.builder()
			.worldPoint(target)
			.image(icon)
			.snapToEdge(true)
			.jumpOnClick(true)
			.name(track.getName())
			.tooltip(map.getPlane() > 0 ? track.getName() + " (floor " + map.getPlane() + ")" : track.getName())
			.build();
		manager.add(point);
		shown = point;
		clientThread.invokeLater(() ->
		{
			WorldMap worldMap = client.getWorldMap();
			if (worldMap != null)
			{
				worldMap.setWorldMapPositionTarget(target);
			}
		});
	}

	public void clear()
	{
		if (shown != null)
		{
			manager.remove(shown);
			shown = null;
		}
	}
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew test --tests 'com.soundtracktracker.map.TrackMapMarkerTest'`
Expected: 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: world-map marker for the selected track" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 9: Track list with search, filter and sort

**Files:**
- Create: `src/main/java/com/soundtracktracker/ui/Sections.java`
- Create: `src/main/java/com/soundtracktracker/ui/TrackRow.java`
- Create: `src/main/java/com/soundtracktracker/ui/TrackListView.java`
- Modify: `src/main/java/com/soundtracktracker/ui/SoundtrackTrackerPanel.java`
- Modify: `src/main/java/com/soundtracktracker/SoundtrackTrackerPlugin.java` (construct the marker, pass it to the panel, clear on shutdown, refresh on config change)

**Interfaces:**
- Consumes: `TrackFilter.apply`, `TrackFilter.LockFilter`, `TrackFilter.SortMode`, `CapeProgress`, `TrackMapMarker`, `SoundtrackTrackerConfig`.
- Produces: `Sections.smallLabel(String text, Color color)`, `Sections.wrappedText(String text)` (read-only `JTextArea`). `TrackRow(Track track, Consumer<Track> onClick)`. `TrackListView(Consumer<Track> onClick)` with `void setTracks(List<Track> tracks)`. `SoundtrackTrackerPanel(SoundtrackTrackerConfig config, TrackMapMarker mapMarker)` with `setTracks(List<Track>)`, `void refreshFromConfig()`, and package-private `void showDetail(Track track)` which Task 10 implements (this task leaves it logging at debug).

No unit tests for Swing composition; the logic they use (`TrackFilter`, `CapeProgress`) is already tested. Verification is in-game at the end of Task 10.

- [ ] **Step 1: Write `Sections`**

```java
package com.soundtracktracker.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import net.runelite.client.ui.ColorScheme;

/** Small factories so every row and section in the panel reads the same. */
final class Sections
{
	private Sections()
	{
	}

	static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1f));
		label.setForeground(color);
		return label;
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	/** Body text that wraps to the sidebar width; a JLabel would clip instead. */
	static JTextArea wrappedText(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setFont(UIManager.getFont("Label.font"));
		area.setForeground(Color.WHITE);
		area.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(true);
		area.setBorder(null);
		return area;
	}
}
```

- [ ] **Step 2: Write `TrackRow`**

```java
package com.soundtracktracker.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/** One line of the list: name coloured by unlock state, location underneath, click for detail. */
class TrackRow extends JPanel
{
	TrackRow(Track track, Consumer<Track> onClick)
	{
		setLayout(new BorderLayout(4, 0));
		setBorder(new EmptyBorder(5, 8, 5, 8));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(track.getName());
		name.setForeground(track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE);
		String where = track.getEnrichment().getLocation();
		JLabel sub = Sections.smallLabel(where == null ? " " : where, ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		text.add(name, BorderLayout.NORTH);
		text.add(sub, BorderLayout.SOUTH);
		add(text, BorderLayout.CENTER);

		JLabel state = Sections.smallLabel(track.isUnlocked() ? "Unlocked" : "Locked",
			track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		add(state, BorderLayout.EAST);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		setToolTipText(track.getHint().isEmpty() ? null : track.getHint());

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.accept(track);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}
}
```

- [ ] **Step 3: Write `TrackListView`**

```java
package com.soundtracktracker.ui;

import java.util.List;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/** The scrolling column of {@link TrackRow}s. */
class TrackListView extends JPanel
{
	private final Consumer<Track> onClick;

	TrackListView(Consumer<Track> onClick)
	{
		this.onClick = onClick;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
	}

	void setTracks(List<Track> tracks)
	{
		removeAll();
		if (tracks.isEmpty())
		{
			JLabel none = Sections.smallLabel("No tracks match.", ColorScheme.LIGHT_GRAY_COLOR);
			none.setBorder(new EmptyBorder(8, 8, 8, 8));
			add(none);
		}
		for (Track track : tracks)
		{
			add(new TrackRow(track, onClick));
			add(Box.createVerticalStrut(2));
		}
		revalidate();
		repaint();
	}
}
```

- [ ] **Step 4: Rewrite `SoundtrackTrackerPanel` with the list page**

```java
package com.soundtracktracker.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import com.soundtracktracker.SoundtrackTrackerConfig;
import com.soundtracktracker.catalog.CapeProgress;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackFilter;
import com.soundtracktracker.catalog.TrackFilter.LockFilter;
import com.soundtracktracker.catalog.TrackFilter.SortMode;
import com.soundtracktracker.map.TrackMapMarker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** The side panel: progress, controls and the list, swapping to a detail page on click. Swing thread only. */
@Slf4j
public class SoundtrackTrackerPanel extends PluginPanel
{
	private final SoundtrackTrackerConfig config;
	private final TrackMapMarker mapMarker;

	private final ProgressHeader header = new ProgressHeader();
	private final JTextField search = new JTextField();
	private final JComboBox<LockFilter> lockFilter = new JComboBox<>(LockFilter.values());
	private final JComboBox<SortMode> sortMode = new JComboBox<>(SortMode.values());
	private final TrackListView list = new TrackListView(this::showDetail);
	private final JScrollPane listScroll;
	private final JPanel listPage = new JPanel(new BorderLayout());
	// typing rebuilds up to ~900 rows; coalesce keystrokes rather than rebuild on each
	private final Timer debounce;

	private List<Track> tracks = Collections.emptyList();

	public SoundtrackTrackerPanel(SoundtrackTrackerConfig config, TrackMapMarker mapMarker)
	{
		super(false);
		this.config = config;
		this.mapMarker = mapMarker;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBorder(new EmptyBorder(6, 8, 6, 8));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		search.setToolTipText("Search tracks by name");
		controls.add(search);
		JPanel combos = new JPanel(new GridLayout(1, 2, 4, 0));
		combos.setOpaque(false);
		combos.setBorder(new EmptyBorder(4, 0, 0, 0));
		combos.add(lockFilter);
		combos.add(sortMode);
		controls.add(combos);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header, BorderLayout.NORTH);
		top.add(controls, BorderLayout.SOUTH);

		// BorderLayout.NORTH inside the viewport keeps rows at their preferred height
		JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listWrapper.add(list, BorderLayout.NORTH);
		listScroll = new JScrollPane(listWrapper);
		listScroll.setBorder(null);
		listScroll.getVerticalScrollBar().setUnitIncrement(16);
		listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		listPage.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listPage.add(top, BorderLayout.NORTH);
		listPage.add(listScroll, BorderLayout.CENTER);
		add(listPage, BorderLayout.CENTER);

		lockFilter.setSelectedItem(config.hideUnlocked() ? LockFilter.LOCKED : LockFilter.ALL);
		debounce = new Timer(150, e -> rebuild());
		debounce.setRepeats(false);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				debounce.restart();
			}
		});
		lockFilter.addActionListener(e -> rebuild());
		sortMode.addActionListener(e -> rebuild());
		rebuild();
	}

	/** A new catalog snapshot; an empty list means logged out. */
	public void setTracks(List<Track> tracks)
	{
		this.tracks = tracks;
		header.update(CapeProgress.of(tracks));
		rebuild();
	}

	/** A config item changed; re-apply the holiday visibility. */
	public void refreshFromConfig()
	{
		rebuild();
	}

	private void rebuild()
	{
		list.setTracks(TrackFilter.apply(tracks, search.getText(),
			(LockFilter) lockFilter.getSelectedItem(), (SortMode) sortMode.getSelectedItem(),
			config.showHolidayTracks()));
	}

	void showDetail(Track track)
	{
		log.debug("soundtracktracker: detail for {}", track.getName());
	}

	void showList(int scrollValue)
	{
		removeAll();
		add(listPage, BorderLayout.CENTER);
		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> listScroll.getVerticalScrollBar().setValue(scrollValue));
	}

	int listScrollValue()
	{
		return listScroll.getVerticalScrollBar().getValue();
	}
}
```

- [ ] **Step 5: Give the plugin the marker and config listener**

In `SoundtrackTrackerPlugin.java`:

Add imports:
```java
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.soundtracktracker.map.TrackMapMarker;
```

Add fields:
```java
	@Inject
	private WorldMapPointManager worldMapPointManager;

	private TrackMapMarker mapMarker;
```

In `startUp`, replace `panel = new SoundtrackTrackerPanel(config);` with:
```java
		BufferedImage markerIcon;
		try
		{
			markerIcon = ImageUtil.loadImageResource(getClass(), "map_marker.png");
		}
		catch (Exception e)
		{
			log.warn("soundtracktracker: failed to load map marker icon", e);
			markerIcon = null;
		}
		mapMarker = new TrackMapMarker(client, clientThread, worldMapPointManager, markerIcon);
		panel = new SoundtrackTrackerPanel(config, mapMarker);
```

In `shutDown`, before the nav button block:
```java
		if (mapMarker != null)
		{
			mapMarker.clear();
			mapMarker = null;
		}
```

Add the subscriber:
```java
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SoundtrackTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshFromConfig();
			}
		});
	}
```

- [ ] **Step 6: Build, run the suite, commit**

Run: `./gradlew test`
Expected: pass.

```bash
git add -A
git commit -m "feat: searchable, filterable track list in the side panel" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

---

### Task 10: Track detail view

**Files:**
- Create: `src/main/java/com/soundtracktracker/ui/TrackDetailView.java`
- Modify: `src/main/java/com/soundtracktracker/ui/SoundtrackTrackerPanel.java` (`showDetail`, keep the detail fresh on new snapshots)

**Interfaces:**
- Consumes: `Sections`, `TrackMapMarker.show`, `LinkBrowser.browse`, `Track`, `TrackEnrichment`.
- Produces: `TrackDetailView(Track track, TrackMapMarker mapMarker, Runnable onBack)`.

- [ ] **Step 1: Write `TrackDetailView`**

```java
package com.soundtracktracker.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackEnrichment;
import com.soundtracktracker.map.TrackMapMarker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * The guide page for one track, laid out like a quest journal: a title, the unlock status,
 * then a section per fact the player needs, with map and wiki buttons at the foot.
 */
class TrackDetailView extends JPanel
{
	TrackDetailView(Track track, TrackMapMarker mapMarker, Runnable onBack)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		TrackEnrichment e = track.getEnrichment();

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new EmptyBorder(8, 8, 8, 8));
		JButton back = new JButton("< Back");
		back.setFocusable(false);
		back.addActionListener(ev -> onBack.run());
		JPanel backRow = new JPanel(new BorderLayout());
		backRow.setOpaque(false);
		backRow.add(back, BorderLayout.WEST);
		top.add(backRow, BorderLayout.NORTH);
		JLabel title = new JLabel(track.getName());
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		JLabel status = Sections.smallLabel(track.isUnlocked() ? "Unlocked" : "Locked",
			track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		top.add(status, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(new EmptyBorder(6, 8, 6, 8));

		String how = track.getHint();
		if (e.getUnlockdetail() != null && !e.getUnlockdetail().equalsIgnoreCase(how))
		{
			how = how.isEmpty() ? e.getUnlockdetail() : how + "\n\n" + e.getUnlockdetail();
		}
		body.add(section("How to unlock", how.isEmpty() ? "No hint is known for this track." : how));
		if (e.getLocation() != null)
		{
			body.add(section("Where it plays", e.getLocation()));
		}
		if (e.getQuest() != null)
		{
			body.add(section("Quest", e.getQuest()));
		}
		if (e.getMembers() != null)
		{
			body.add(section("Members", e.getMembers() ? "Yes" : "No"));
		}
		if (track.isHoliday())
		{
			body.add(section("Holiday track", "Not needed for the music cape."));
		}

		JPanel bodyWrapper = new JPanel(new BorderLayout());
		bodyWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bodyWrapper.add(body, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(bodyWrapper);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setBorder(new EmptyBorder(6, 8, 8, 8));
		JButton map = new JButton("Show on map");
		map.setFocusable(false);
		map.setEnabled(track.hasMap());
		map.setToolTipText(track.hasMap() ? "Mark where this track plays on the world map"
			: "No map location is known for this track");
		map.addActionListener(ev -> mapMarker.show(track));
		JButton wiki = new JButton("Wiki");
		wiki.setFocusable(false);
		wiki.setEnabled(e.getWikiUrl() != null);
		wiki.addActionListener(ev -> LinkBrowser.browse(e.getWikiUrl()));
		buttons.add(map);
		buttons.add(wiki);
		add(buttons, BorderLayout.SOUTH);
	}

	private static JComponent section(String heading, String text)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 3));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 8, 8, 8));
		panel.add(Sections.heading(heading), BorderLayout.NORTH);
		panel.add(Sections.wrappedText(text), BorderLayout.CENTER);
		JPanel spaced = new JPanel(new BorderLayout());
		spaced.setOpaque(false);
		spaced.setBorder(new EmptyBorder(0, 0, 6, 0));
		spaced.add(panel, BorderLayout.CENTER);
		spaced.setAlignmentX(LEFT_ALIGNMENT);
		return spaced;
	}
}
```

- [ ] **Step 2: Implement `showDetail` and keep the open detail fresh**

In `SoundtrackTrackerPanel.java` add a field:
```java
	// the track whose detail page is open, so a new snapshot (e.g. it just unlocked) redraws it
	private Track detailTrack;
```

Replace `showDetail` and `showList`:
```java
	void showDetail(Track track)
	{
		detailTrack = track;
		final int scroll = listScrollValue();
		removeAll();
		add(new TrackDetailView(track, mapMarker, () -> showList(scroll)), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	void showList(int scrollValue)
	{
		detailTrack = null;
		removeAll();
		add(listPage, BorderLayout.CENTER);
		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> listScroll.getVerticalScrollBar().setValue(scrollValue));
	}
```

In `setTracks`, after `rebuild();`:
```java
		if (detailTrack != null)
		{
			Track fresh = null;
			for (Track t : tracks)
			{
				if (t.getName().equals(detailTrack.getName()))
				{
					fresh = t;
					break;
				}
			}
			if (fresh == null)
			{
				showList(0); // logged out: the detail no longer describes this player
			}
			else if (fresh.isUnlocked() != detailTrack.isUnlocked())
			{
				showDetail(fresh);
			}
		}
```

- [ ] **Step 3: Build, run the suite, commit**

Run: `./gradlew test`
Expected: pass.

```bash
git add -A
git commit -m "feat: quest-guide style detail page per track with map and wiki" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```

- [ ] **Step 4: In-game verification (user)**

Offer `./gradlew run` again and ask the user to check:

1. The list shows tracks alphabetically with green names for unlocked ones and a location line under each. Scrolling is smooth with the "All" filter.
2. Typing `shanty` narrows the list within a moment; clearing it restores the list. The lock filter and the sort combo behave as labelled; "Location" sort places tracks with no known location at the end.
3. Clicking "Sea Shanty 2" opens the detail page: hint, "Where it plays", "Members: No", enabled "Show on map" and "Wiki" buttons. "Show on map" then opening the world map centres near Port Sarim with a marker. "Wiki" opens the browser. "< Back" returns to the same scroll position.
4. Toggling "Show holiday tracks" off in the plugin config removes tracks such as "Jungle Bells" from the list while the header count is unchanged.
5. Unlock a track while its detail page is open: the status line flips to "Unlocked" without navigating away.
6. Disable the plugin: the sidebar icon and any map marker disappear.

---

### Task 11: README and final review

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-09-06-soundtrack-tracker-design.md` (status line only)

- [ ] **Step 1: Write `README.md`**

```markdown
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
```

- [ ] **Step 2: Update the spec status**

Change the spec's `Status:` line to `Status: implemented 2026-09-06 (see docs/superpowers/plans/2026-09-06-soundtrack-tracker.md)`.

- [ ] **Step 3: Final checks**

Run: `./gradlew test` and `cd tools && python -m unittest test_fetch_tracks`
Expected: both pass.

Run: `git status --short` and confirm no `build/` or `.gradle/` files are staged, and `grep -rn -i "itemsearch\|com.example" --include=*.java --include=*.gradle --include=*.properties .` prints nothing.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: README and spec status" -m "Claude-Session: https://claude.ai/code/session_01517mRLgCs5pJT7y8FD7hRa"
```
