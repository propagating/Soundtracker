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
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import com.soundtracktracker.catalog.TrackEnrichment;
import com.soundtracktracker.map.TrackMapMarker;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/**
 * The guide page for one track, laid out like a quest journal: a title, the unlock status,
 * then a section per fact the player needs, with map and wiki buttons at the foot.
 */
class TrackDetailView extends JPanel
{
	TrackDetailView(Track track, TrackMapMarker mapMarker, Runnable onBack)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		TrackEnrichment e = track.getEnrichment();

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setBorder(new EmptyBorder(8, 8, 8, 8));
		JButton back = new JButton("< Back");
		back.setFocusable(false);
		back.addActionListener(ev -> onBack.run());
		JPanel backRow = new JPanel(new BorderLayout());
		backRow.setOpaque(false);
		backRow.add(back, BorderLayout.WEST);
		top.add(backRow, BorderLayout.NORTH);
		JLabel title = new JLabel(track.getName());
		title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		JLabel status = Sections.smallLabel(track.isUnlocked() ? "Unlocked" : "Locked",
			track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		top.add(status, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(new EmptyBorder(6, 8, 6, 8));

		String how = track.getHint();
		if (e.getUnlockdetail() != null && !e.getUnlockdetail().equalsIgnoreCase(how))
		{
			how = how.isEmpty() ? e.getUnlockdetail() : how + "\n\n" + e.getUnlockdetail();
		}
		body.add(section("How to unlock", how.isEmpty() ? "No hint is known for this track." : how));
		if (e.getLocation() != null)
		{
			body.add(section("Where it plays", e.getLocation()));
		}
		if (e.getQuest() != null)
		{
			body.add(section("Quest", e.getQuest()));
		}
		if (e.getMembers() != null)
		{
			body.add(section("Members", e.getMembers() ? "Yes" : "No"));
		}
		if (track.isClueTrack())
		{
			String clue = "Cecilia in Falador Park may ask you to play this track for a"
				+ (e.getClueTier().startsWith("e") ? "n " : " ") + e.getClueTier() + " clue step.";
			if (e.getClueNote() != null)
			{
				clue += "\n\n" + e.getClueNote();
			}
			body.add(section("Clue scroll", clue));
		}
		if (track.isHoliday())
		{
			body.add(section("Holiday track", "Not needed for the music cape."));
		}

		JPanel bodyWrapper = new JPanel(new BorderLayout());
		bodyWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bodyWrapper.add(body, BorderLayout.NORTH);
		JScrollPane scroll = new JScrollPane(bodyWrapper);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setBorder(new EmptyBorder(6, 8, 8, 8));
		JButton map = new JButton("Show on map");
		map.setFocusable(false);
		map.setEnabled(track.hasMap());
		map.setToolTipText(track.hasMap() ? "Mark where this track plays on the world map"
			: "No map location is known for this track");
		map.addActionListener(ev -> mapMarker.show(track));
		JButton wiki = new JButton("Wiki");
		wiki.setFocusable(false);
		wiki.setEnabled(e.getWikiUrl() != null);
		wiki.addActionListener(ev -> LinkBrowser.browse(e.getWikiUrl()));
		buttons.add(map);
		buttons.add(wiki);
		add(buttons, BorderLayout.SOUTH);
	}

	private static JComponent section(String heading, String text)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 3));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(6, 8, 8, 8));
		panel.add(Sections.heading(heading), BorderLayout.NORTH);
		panel.add(Sections.wrappedText(text), BorderLayout.CENTER);
		JPanel spaced = new JPanel(new BorderLayout());
		spaced.setOpaque(false);
		spaced.setBorder(new EmptyBorder(0, 0, 6, 0));
		spaced.add(panel, BorderLayout.CENTER);
		spaced.setAlignmentX(LEFT_ALIGNMENT);
		return spaced;
	}
}
