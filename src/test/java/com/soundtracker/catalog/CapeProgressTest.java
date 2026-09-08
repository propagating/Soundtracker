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
