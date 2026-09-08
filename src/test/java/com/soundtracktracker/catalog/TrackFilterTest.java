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
		return track(name, sortName, unlocked, holiday, region, null);
	}

	private static Track track(String name, String sortName, boolean unlocked, boolean holiday, String region,
		String clueTier)
	{
		TrackEnrichment e = new TrackEnrichment();
		e.setRegion(region);
		e.setClueTier(clueTier);
		return Track.builder()
			.name(name).sortName(sortName).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(unlocked).holiday(holiday).automatic(false).enrichment(e)
			.build();
	}

	private final List<Track> tracks = Arrays.asList(
		track("The Trade Parade", "Trade Parade, The", false, false, "Grand Exchange"),
		track("Adventure", "Adventure", true, false, "Lumbridge"),
		track("Sea Shanty 2", "Sea Shanty 2", false, false, "Port Sarim", "hard"),
		track("Scape Main", "Scape Main", true, false, null),
		track("Jungle Bells", "Jungle Bells", false, true, "Karamja"));

	private static List<String> names(List<Track> in)
	{
		return in.stream().map(Track::getName).collect(Collectors.toList());
	}

	@Test
	public void sortsByGameSortName()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.NAME, true, false);
		assertEquals(Arrays.asList("Adventure", "Jungle Bells", "Scape Main", "Sea Shanty 2", "The Trade Parade"), names(out));
	}

	@Test
	public void queryIsCaseInsensitiveSubstring()
	{
		List<Track> out = TrackFilter.apply(tracks, "  SHANTY ", LockFilter.ALL, SortMode.NAME, true, false);
		assertEquals(Arrays.asList("Sea Shanty 2"), names(out));
	}

	@Test
	public void lockFilterKeepsOnlyMatchingState()
	{
		assertEquals(Arrays.asList("Jungle Bells", "Sea Shanty 2", "The Trade Parade"),
			names(TrackFilter.apply(tracks, "", LockFilter.LOCKED, SortMode.NAME, true, false)));
		assertEquals(Arrays.asList("Adventure", "Scape Main"),
			names(TrackFilter.apply(tracks, "", LockFilter.UNLOCKED, SortMode.NAME, true, false)));
	}

	@Test
	public void neededForCapeKeepsLockedTracksThatCountForTheCape()
	{
		// Jungle Bells is locked but a holiday track, so it is never needed for the cape
		assertEquals(Arrays.asList("Sea Shanty 2", "The Trade Parade"),
			names(TrackFilter.apply(tracks, "", LockFilter.CAPE, SortMode.NAME, true, false)));
	}

	@Test
	public void holidayTracksCanBeHidden()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.NAME, false, false);
		assertEquals(Arrays.asList("Adventure", "Scape Main", "Sea Shanty 2", "The Trade Parade"), names(out));
	}

	@Test
	public void locationSortGroupsByRegionWithUnknownLast()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.LOCATION, true, false);
		assertEquals(Arrays.asList("The Trade Parade", "Jungle Bells", "Adventure", "Sea Shanty 2", "Scape Main"), names(out));
	}

	@Test
	public void clueOnlyKeepsTracksWithAClueTier()
	{
		List<Track> out = TrackFilter.apply(tracks, "", LockFilter.ALL, SortMode.NAME, true, true);
		assertEquals(Arrays.asList("Sea Shanty 2"), names(out));
	}

	@Test
	public void clueOnlyCombinesWithTheLockFilter()
	{
		assertEquals(0, TrackFilter.apply(tracks, "", LockFilter.UNLOCKED, SortMode.NAME, true, true).size());
	}

	@Test
	public void nullQueryMatchesEverything()
	{
		assertEquals(5, TrackFilter.apply(tracks, null, LockFilter.ALL, SortMode.NAME, true, false).size());
	}
}
