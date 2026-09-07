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
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/**
 * The stamp {@link TrackListView} paints each row with: name coloured by unlock state,
 * location underneath, lock state and any clue tier on the right. One instance is reused for
 * every row, so nothing here may keep per-row state.
 */
class TrackRow extends JPanel implements ListCellRenderer<Track>
{
	private static final int ROW_GAP = 2;

	private final JLabel name = new JLabel();
	private final JLabel sub = Sections.smallLabel(" ", ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel state = Sections.smallLabel(" ", Color.WHITE);
	private final JLabel clue = Sections.smallLabel(" ", ColorScheme.BRAND_ORANGE);

	TrackRow()
	{
		setLayout(new BorderLayout(4, 0));
		// the gap between rows is the list background showing through a bottom border
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, ROW_GAP, 0, ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(5, 8, 5, 8)));
		setOpaque(true);

		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		text.add(name, BorderLayout.NORTH);
		text.add(sub, BorderLayout.SOUTH);
		add(text, BorderLayout.CENTER);

		state.setHorizontalAlignment(JLabel.RIGHT);
		clue.setHorizontalAlignment(JLabel.RIGHT);
		JPanel side = new JPanel(new BorderLayout());
		side.setOpaque(false);
		side.add(state, BorderLayout.NORTH);
		side.add(clue, BorderLayout.SOUTH);
		add(side, BorderLayout.EAST);
	}

	/** Height every row is drawn at: the two-line layout with the clue tag present. */
	int rowHeight()
	{
		name.setText("Xg");
		sub.setText("Xg");
		state.setText("Unlocked");
		clue.setText("Clue: Medium");
		return getPreferredSize().height;
	}

	@Override
	public Component getListCellRendererComponent(JList<? extends Track> list, Track track, int index,
		boolean isSelected, boolean cellHasFocus)
	{
		name.setText(track.getName());
		name.setForeground(track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE);
		String where = track.getEnrichment().getLocation();
		sub.setText(where == null ? " " : where);
		state.setText(track.isUnlocked() ? "Unlocked" : "Locked");
		state.setForeground(track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		clue.setText(track.isClueTrack() ? Sections.clueTag(track) : " ");
		boolean hovered = list instanceof TrackListView && ((TrackListView) list).isHovered(index);
		setBackground(hovered ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		return this;
	}
}
