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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.soundtracktracker.SoundtrackTrackerConfig;
import com.soundtracktracker.catalog.CapeProgress;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackFilter;
import com.soundtracktracker.catalog.TrackFilter.LockFilter;
import com.soundtracktracker.catalog.TrackFilter.SortMode;
import com.soundtracktracker.map.TrackMapMarker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** The side panel: progress, controls and the list, swapping to a detail page on click. Swing thread only. */
public class SoundtrackTrackerPanel extends PluginPanel
{
	private final SoundtrackTrackerConfig config;
	private final TrackMapMarker mapMarker;

	private final ProgressHeader header = new ProgressHeader();
	private final JTextField search = new JTextField();
	private final JComboBox<LockFilter> lockFilter = new JComboBox<>(LockFilter.values());
	private final JComboBox<SortMode> sortMode = new JComboBox<>(SortMode.values());
	private final TrackListView list = new TrackListView(this::showDetail);
	private final JScrollPane listScroll;
	private final JPanel listPage = new JPanel(new BorderLayout());
	// typing rebuilds up to ~900 rows; coalesce keystrokes rather than rebuild on each
	private final Timer debounce;

	private List<Track> tracks = Collections.emptyList();
	// the track whose detail page is open, so a new snapshot (e.g. it just unlocked) redraws it
	private Track detailTrack;

	public SoundtrackTrackerPanel(SoundtrackTrackerConfig config, TrackMapMarker mapMarker)
	{
		super(false);
		this.config = config;
		this.mapMarker = mapMarker;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBorder(new EmptyBorder(6, 8, 6, 8));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		search.setToolTipText("Search tracks by name");
		controls.add(search);
		JPanel combos = new JPanel(new GridLayout(1, 2, 4, 0));
		combos.setOpaque(false);
		combos.setBorder(new EmptyBorder(4, 0, 0, 0));
		combos.add(lockFilter);
		combos.add(sortMode);
		controls.add(combos);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(header, BorderLayout.NORTH);
		top.add(controls, BorderLayout.SOUTH);

		// BorderLayout.NORTH inside the viewport keeps rows at their preferred height
		JPanel listWrapper = new JPanel(new BorderLayout());
		listWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listWrapper.add(list, BorderLayout.NORTH);
		listScroll = new JScrollPane(listWrapper);
		listScroll.setBorder(null);
		listScroll.getVerticalScrollBar().setUnitIncrement(16);
		listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		listPage.setBackground(ColorScheme.DARK_GRAY_COLOR);
		listPage.add(top, BorderLayout.NORTH);
		listPage.add(listScroll, BorderLayout.CENTER);
		add(listPage, BorderLayout.CENTER);

		lockFilter.setSelectedItem(config.hideUnlocked() ? LockFilter.LOCKED : LockFilter.ALL);
		debounce = new Timer(150, e -> rebuild());
		debounce.setRepeats(false);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				debounce.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				debounce.restart();
			}
		});
		lockFilter.addActionListener(e -> rebuild());
		sortMode.addActionListener(e -> rebuild());
		rebuild();
	}

	/** A new catalog snapshot; an empty list means logged out. */
	public void setTracks(List<Track> tracks)
	{
		this.tracks = tracks;
		header.update(CapeProgress.of(tracks));
		rebuild();
		if (detailTrack != null)
		{
			Track fresh = null;
			// the midi id is stable and unique; the display name is not guaranteed to be,
			// so only fall back to matching by name for tracks that have no midi id
			boolean byMidi = detailTrack.getMidiId() != -1;
			for (Track t : tracks)
			{
				if (byMidi ? t.getMidiId() == detailTrack.getMidiId() : t.getName().equals(detailTrack.getName()))
				{
					fresh = t;
					break;
				}
			}
			if (fresh == null)
			{
				showList(0); // logged out: the detail no longer describes this player
			}
			else if (fresh.isUnlocked() != detailTrack.isUnlocked())
			{
				showDetail(fresh);
			}
		}
	}

	/** A config item changed; re-apply the holiday visibility. */
	public void refreshFromConfig()
	{
		rebuild();
	}

	/**
	 * Stops the pending debounce timer; call before this panel is discarded.
	 *
	 * <p>Public, not package-private, because the plugin (a different package) must call this
	 * from {@code shutDown()} to stop the timer before the panel is dropped.
	 */
	public void dispose()
	{
		debounce.stop();
	}

	private void rebuild()
	{
		List<Track> filtered = TrackFilter.apply(tracks, search.getText(),
			(LockFilter) lockFilter.getSelectedItem(), (SortMode) sortMode.getSelectedItem(),
			config.showHolidayTracks());
		String emptyMessage = tracks.isEmpty() ? "Log in to load your tracks." : "No tracks match.";
		list.setTracks(filtered, emptyMessage);
	}

	void showDetail(Track track)
	{
		detailTrack = track;
		final int scroll = listScrollValue();
		removeAll();
		add(new TrackDetailView(track, mapMarker, () -> showList(scroll)), BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	void showList(int scrollValue)
	{
		detailTrack = null;
		removeAll();
		add(listPage, BorderLayout.CENTER);
		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> listScroll.getVerticalScrollBar().setValue(scrollValue));
	}

	int listScrollValue()
	{
		return listScroll.getVerticalScrollBar().getValue();
	}
}
