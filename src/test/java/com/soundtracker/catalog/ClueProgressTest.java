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

public class ClueProgressTest
{
	static Track track(String name, boolean unlocked, String clueTier)
	{
		TrackEnrichment e = TrackEnrichment.builder().clueTier(clueTier).build();
		return Track.builder()
			.name(name).sortName(name).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(unlocked).holiday(false).automatic(false)
			.enrichment(e)
			.build();
	}

	@Test
	public void trackWithATierIsAClueTrack()
	{
		assertTrue(track("Alone", false, "easy").isClueTrack());
		assertFalse(track("Adventure", false, null).isClueTrack());
	}

	@Test
	public void countsOnlyClueTracks()
	{
		CapeProgress.Progress p = ClueProgress.of(Arrays.asList(
			track("Alone", true, "easy"),
			track("Faerie", false, "medium"),
			track("Adventure", true, null)));
		assertEquals(1, p.getUnlocked());
		assertEquals(2, p.getRequired());
		assertFalse(p.isComplete());
	}

	@Test
	public void noClueTracksMeansNothingRequired()
	{
		CapeProgress.Progress p = ClueProgress.of(Collections.singletonList(track("Adventure", true, null)));
		assertEquals(0, p.getRequired());
		assertFalse(p.isComplete());
	}
}
