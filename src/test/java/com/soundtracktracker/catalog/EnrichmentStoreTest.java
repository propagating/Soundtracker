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
		assertEquals("hard", hit.getClueTier());
		assertEquals("Requires completion of Bone Voyage.", hit.getClueNote());
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
