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
package com.soundtracker.catalog;

import com.google.gson.Gson;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
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
	// the table stores a 1-based slot into the MUSICMULTI varp series, not the varp id itself
	private static final int SLOT_A = 1;
	private static final int SLOT_B = 2;
	private static final int VARP_A = VarPlayerID.MUSICMULTI_1;
	private static final int VARP_B = VarPlayerID.MUSICMULTI_2;

	@Mock
	private Client client;

	private TrackCatalog catalog;
	private final List<List<Track>> published = new ArrayList<>();

	/** Rows: 1 locked bit0/slotA, 2 unlocked bit3/slotA, 3 hidden, 4 holiday bit0/slotB, 5 automatic, 6 no name. */
	@Before
	public void setUp()
	{
		lenient().when(client.getDBTableRows(DBTableID.Music.ID)).thenReturn(Arrays.asList(1, 2, 3, 4, 5, 6));
		row(1, "Sea Shanty 2", "Sea Shanty 2", "This track unlocks in Port Sarim.", 35, SLOT_A, 0, false, false, false);
		row(2, "The Trade Parade", "Trade Parade, The", "Grand Exchange.", 40, SLOT_A, 3, false, false, false);
		row(3, "Hidden One", "Hidden One", "", 41, SLOT_A, 4, true, false, false);
		row(4, "Jungle Bells", "Jungle Bells", "Christmas.", 42, SLOT_B, 0, false, true, false);
		row(5, "Scape Main", "Scape Main", "Automatic.", 43, -1, -1, false, false, true);
		row(6, null, null, null, 44, SLOT_A, 5, false, false, false);
		lenient().when(client.getVarpValue(VARP_A)).thenReturn(1 << 3);
		lenient().when(client.getVarpValue(VARP_B)).thenReturn(0);

		EnrichmentStore store = new EnrichmentStore(new Gson(), "/unused.json");
		store.load(new StringReader("[{\"cacheid\":35,\"name\":\"Sea Shanty 2\",\"region\":\"Port Sarim\"}]"));
		catalog = new TrackCatalog(client, store);
		catalog.addListener(published::add);
	}

	private void row(int id, String display, String sort, String hint, int midi, int slot, int bit,
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
			.thenReturn(slot < 0 ? new Object[0] : new Object[]{slot});
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

	/**
	 * The live table stores holiday as an integer holiday type (1 to 4) whose default for an
	 * ordinary track is -1, not a boolean; -1 must not read as "is a holiday track".
	 */
	@Test
	public void holidayTypeColumnTreatsTheMinusOneDefaultAsNotHoliday()
	{
		lenient().when(client.getDBTableField(1, DBTableID.Music.COL_HOLIDAY, 0)).thenReturn(new Object[]{-1});
		lenient().when(client.getDBTableField(4, DBTableID.Music.COL_HOLIDAY, 0)).thenReturn(new Object[]{2});
		catalog.load();
		List<Track> tracks = catalog.snapshot();
		assertFalse(byName(tracks, "Sea Shanty 2").isHoliday());
		assertTrue(byName(tracks, "Sea Shanty 2").countsForCape());
		assertTrue(byName(tracks, "Jungle Bells").isHoliday());
	}

	@Test
	public void variableSlotResolvesToTheMusicmultiVarp()
	{
		catalog.load();
		List<Track> tracks = catalog.snapshot();
		assertEquals(VarPlayerID.MUSICMULTI_1, byName(tracks, "Sea Shanty 2").getVarp());
		assertEquals(VarPlayerID.MUSICMULTI_2, byName(tracks, "Jungle Bells").getVarp());
		// slot 27 is the last varp in the series today
		lenient().when(client.getDBTableField(1, DBTableID.Music.COL_VARIABLE, 0)).thenReturn(new Object[]{27});
		lenient().when(client.getVarpValue(VarPlayerID.MUSICMULTI_27)).thenReturn(1);
		catalog = new TrackCatalog(client, new EnrichmentStore(new Gson(), "/unused.json"));
		catalog.load();
		assertTrue(byName(catalog.snapshot(), "Sea Shanty 2").isUnlocked());
	}

	@Test
	public void unknownVariableSlotReadsAsLockedWithoutCrashing()
	{
		lenient().when(client.getDBTableField(1, DBTableID.Music.COL_VARIABLE, 0)).thenReturn(new Object[]{99});
		catalog.load();
		Track shanty = byName(catalog.snapshot(), "Sea Shanty 2");
		assertEquals(-1, shanty.getVarp());
		assertFalse(shanty.isUnlocked());
	}

	/**
	 * A varp holds 32 bits; Java's shift masks the count to 5 bits, so bit 32 would silently read
	 * bit 0 instead of being treated like a row with no unlock bit at all.
	 */
	@Test
	public void bitBeyondTheVarpWidthReadsAsLocked()
	{
		lenient().when(client.getDBTableField(1, DBTableID.Music.COL_VARIABLE, 1)).thenReturn(new Object[]{32});
		lenient().when(client.getVarpValue(VARP_A)).thenReturn(1);
		catalog.load();
		Track shanty = byName(catalog.snapshot(), "Sea Shanty 2");
		assertFalse(shanty.isUnlocked());
		assertEquals(-1, shanty.getBit());
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
	public void loadTwiceWithUnchangedVarpsPublishesOnce()
	{
		catalog.load();
		catalog.load();
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
