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
package com.soundtracker.ui;

import com.soundtracker.SoundtrackerConfig;
import com.soundtracker.catalog.Track;
import com.soundtracker.catalog.TrackEnrichment;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SoundtrackerPanelTest
{
	private static Track track(int i, boolean unlocked)
	{
		return Track.builder().name("Track " + i).sortName("Track " + i).hint("").midiId(i).varp(-1).bit(-1)
			.unlocked(unlocked).holiday(false).automatic(false).enrichment(TrackEnrichment.EMPTY).build();
	}

	private static List<Track> tracks(int n, boolean unlocked)
	{
		List<Track> out = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			out.add(track(i, unlocked));
		}
		return out;
	}

	private static Component find(Container root, Class<?> type)
	{
		for (Component c : root.getComponents())
		{
			if (type.isInstance(c))
			{
				return c;
			}
			if (c instanceof Container)
			{
				Component hit = find((Container) c, type);
				if (hit != null)
				{
					return hit;
				}
			}
		}
		return null;
	}

	/**
	 * When the open track's unlock state flips, the detail page is redrawn. The scroll offset to
	 * return to must be the one captured when the page was first opened, not whatever the hidden
	 * list's scroll bar reads after the new snapshot rebuilt it.
	 */
	@Test
	public void detailRedrawKeepsTheScrollOffsetItWasOpenedWith() throws Exception
	{
		SoundtrackerPanel panel = new SoundtrackerPanel(new SoundtrackerConfig() { }, null);
		// the list page is the panel's only child until a detail page replaces it
		Container listPage = (Container) panel.getComponent(0);
		JScrollBar bar = ((JScrollPane) find(listPage, JScrollPane.class)).getVerticalScrollBar();

		SwingUtilities.invokeAndWait(() ->
		{
			List<Track> initial = tracks(200, false);
			panel.setTracks(initial);
			bar.setMaximum(5000);
			bar.setValue(1234);
			panel.showDetail(initial.get(50));
			// the list is now off-screen; a rebuild from the next snapshot moves its scroll bar
			bar.setValue(0);
			List<Track> next = tracks(200, false);
			next.set(50, track(50, true));
			panel.setTracks(next);
			((JButton) find(panel, JButton.class)).doClick();
		});
		// showList applies the offset via invokeLater; flush it
		SwingUtilities.invokeAndWait(() -> { });

		assertEquals(1234, bar.getValue());
	}
}
