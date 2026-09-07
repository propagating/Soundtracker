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
