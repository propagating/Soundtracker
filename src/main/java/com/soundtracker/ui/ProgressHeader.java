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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import com.soundtracker.catalog.CapeProgress;
import net.runelite.client.ui.ColorScheme;

/** "412 / 869 tracks" and a bar: how far the player is from the music cape (or from every clue track). */
public class ProgressHeader extends JPanel
{
	static final String LOGGED_OUT = "Log in to load your tracks";

	private final JLabel label = new JLabel(LOGGED_OUT);
	private final JProgressBar bar = new JProgressBar();

	public ProgressHeader()
	{
		setLayout(new BorderLayout(0, 4));
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setForeground(Color.WHITE);
		bar.setPreferredSize(new Dimension(0, 8));
		bar.setBorderPainted(false);
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		add(label, BorderLayout.NORTH);
		add(bar, BorderLayout.CENTER);
	}

	/**
	 * @param clueMode show the Treasure Trails count instead of the cape count
	 * @param loggedIn false while the catalog is empty because nobody is logged in
	 */
	public void update(CapeProgress.Progress progress, boolean clueMode, boolean loggedIn)
	{
		if (!loggedIn || progress.getRequired() == 0)
		{
			label.setText(!loggedIn ? LOGGED_OUT
				: clueMode ? "No clue tracks are known" : "No tracks count toward the cape");
			bar.setMaximum(1);
			bar.setValue(0);
			bar.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
			return;
		}
		label.setText(progress.getUnlocked() + " / " + progress.getRequired()
			+ (clueMode ? " clue tracks unlocked" : " tracks for the cape"));
		bar.setMaximum(progress.getRequired());
		bar.setValue(progress.getUnlocked());
		bar.setForeground(progress.isComplete()
			? ColorScheme.PROGRESS_COMPLETE_COLOR
			: ColorScheme.PROGRESS_INPROGRESS_COLOR);
	}
}
