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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
	}

	/** A config item changed; re-apply the holiday visibility. */
	public void refreshFromConfig()
	{
		rebuild();
	}

	private void rebuild()
	{
		list.setTracks(TrackFilter.apply(tracks, search.getText(),
			(LockFilter) lockFilter.getSelectedItem(), (SortMode) sortMode.getSelectedItem(),
			config.showHolidayTracks()));
	}

	void showDetail(Track track)
	{
		log.debug("soundtracktracker: detail for {}", track.getName());
	}

	void showList(int scrollValue)
	{
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
