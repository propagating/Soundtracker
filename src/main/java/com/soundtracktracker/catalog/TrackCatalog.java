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
		// GameState.LOGGED_IN fires after every LOADING transition (teleport, dungeon, hop,
		// not just actual logins), so load() re-runs and would otherwise republish an
		// unchanged snapshot on each one, forcing the panel to rebuild ~1200 Swing rows.
		if (next.equals(tracks))
		{
			return;
		}
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
		int missingMidi = 0;
		int enriched = 0;
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
			if (midi < 0)
			{
				missingMidi++;
			}
			int varp = integer(row, DBTableID.Music.COL_VARIABLE, 0);
			int bit = integer(row, DBTableID.Music.COL_VARIABLE, 1);
			TrackEnrichment trackEnrichment = enrichment.find(midi, name);
			if (!TrackEnrichment.EMPTY.equals(trackEnrichment))
			{
				enriched++;
			}
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
				.enrichment(trackEnrichment)
				.build();
			if (varp >= 0 && bit >= 0)
			{
				indexesByVarp.computeIfAbsent(varp, k -> new ArrayList<>()).add(out.size());
			}
			out.add(track);
		}
		rows = out;
		log.debug("soundtracktracker: read {} tracks across {} varps, skipped {} without a name, "
				+ "{} without a midi id, enriched {}",
			out.size(), indexesByVarp.size(), skipped, missingMidi, enriched);
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
