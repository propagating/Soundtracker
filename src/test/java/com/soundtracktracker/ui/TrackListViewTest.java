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
package com.soundtracktracker.ui;

import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackEnrichment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The game canvas is a heavyweight AWT component, so AWT recomputes clip shapes for every
 * sibling whenever a lightweight component is added or removed. A list built from one panel
 * per track therefore freezes the sidebar for seconds on each rebuild or page switch. The list
 * must render rows without adding a component per track.
 */
public class TrackListViewTest
{
	private static Track track(String name, String hint, String clueTier)
	{
		TrackEnrichment e = new TrackEnrichment();
		e.setLocation("Somewhere");
		e.setClueTier(clueTier);
		return Track.builder().name(name).sortName(name).hint(hint).midiId(1).varp(-1).bit(-1)
			.unlocked(false).holiday(false).automatic(false).enrichment(e).build();
	}

	private static List<Track> tracks(int n)
	{
		List<Track> out = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			out.add(track("Track " + i, i % 2 == 0 ? "Hint " + i : "", i % 7 == 0 ? "hard" : null));
		}
		return out;
	}

	@Test
	public void listAddsNoComponentPerTrack() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TrackListView view = new TrackListView(t -> { });
			// Swing gives a JList exactly one child of its own, the cell renderer pane
			int fixed = view.getComponentCount();
			view.setTracks(tracks(1000), "No tracks match.");
			assertEquals(1000, view.rowCount());
			assertEquals("Track 999", view.trackAt(999).getName());
			assertEquals(fixed, view.getComponentCount());
			assertEquals(1, fixed);
			view.setTracks(Collections.emptyList(), "Log in to load your tracks.");
			assertEquals(0, view.rowCount());
			assertEquals("Log in to load your tracks.", view.emptyMessage());
		});
	}

	@Test
	public void openingARowHandsThatTrackToTheCallback() throws Exception
	{
		AtomicReference<Track> opened = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			TrackListView view = new TrackListView(opened::set);
			view.setTracks(tracks(5), "");
			view.openRow(3);
			assertEquals("Track 3", opened.get().getName());
			view.openRow(99);
			assertEquals("Track 3", opened.get().getName());
		});
	}

	@Test
	public void tooltipIsTheHintOfTheRow()
	{
		TrackListView view = new TrackListView(t -> { });
		view.setTracks(tracks(3), "");
		assertEquals("Hint 0", view.tooltipFor(0));
		assertNull(view.tooltipFor(1));
		assertNull(view.tooltipFor(7));
	}
}
