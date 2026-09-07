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

import java.awt.Color;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import com.soundtracktracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/** Small factories so every row and section in the panel reads the same. */
final class Sections
{
	private Sections()
	{
	}

	/** "Clue: Hard" for a track Cecilia can ask for; the tier comes from the wiki in lower case. */
	static String clueTag(Track track)
	{
		return "Clue: " + capitalise(track.getEnrichment().getClueTier());
	}

	static String capitalise(String s)
	{
		return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	static JLabel smallLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1f));
		label.setForeground(color);
		return label;
	}

	static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 1f));
		label.setForeground(ColorScheme.BRAND_ORANGE);
		return label;
	}

	/** Body text that wraps to the sidebar width; a JLabel would clip instead. */
	static JTextArea wrappedText(String text)
	{
		JTextArea area = new JTextArea(text);
		area.setFont(UIManager.getFont("Label.font"));
		area.setForeground(Color.WHITE);
		area.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(true);
		area.setBorder(null);
		return area;
	}
}
