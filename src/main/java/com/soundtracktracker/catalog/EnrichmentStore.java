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
