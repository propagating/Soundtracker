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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/** One line of the list: name coloured by unlock state, location underneath, click for detail. */
class TrackRow extends JPanel
{
	TrackRow(Track track, Consumer<Track> onClick)
	{
		setLayout(new BorderLayout(4, 0));
		setBorder(new EmptyBorder(5, 8, 5, 8));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(track.getName());
		name.setForeground(track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : Color.WHITE);
		String where = track.getEnrichment().getLocation();
		JLabel sub = Sections.smallLabel(where == null ? " " : where, ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel(new BorderLayout());
		text.setOpaque(false);
		text.add(name, BorderLayout.NORTH);
		text.add(sub, BorderLayout.SOUTH);
		add(text, BorderLayout.CENTER);

		JLabel state = Sections.smallLabel(track.isUnlocked() ? "Unlocked" : "Locked",
			track.isUnlocked() ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		add(state, BorderLayout.EAST);

		setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
		setToolTipText(track.getHint().isEmpty() ? null : track.getHint());

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.accept(track);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
	}
}
