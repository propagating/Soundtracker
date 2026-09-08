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

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.ToolTipManager;
import com.soundtracker.catalog.Track;
import net.runelite.client.ui.ColorScheme;

/**
 * The scrolling list of tracks.
 *
 * <p>Deliberately a {@link JList} with a renderer rather than a panel per row: RuneLite's game
 * canvas is a heavyweight component, so AWT recomputes clip shapes for every sibling whenever a
 * lightweight component is added or removed. With ~850 row panels that made each rebuild and
 * each page switch freeze the sidebar for seconds. A JList paints rows with one reusable stamp
 * and adds nothing to the component tree.
 */
class TrackListView extends JList<Track>
{
	private static final int TEXT_INSET = 8;

	private final Consumer<Track> onOpen;
	private String emptyMessage = "";
	// row under the mouse, painted with the hover colour by the renderer
	private int hoverIndex = -1;

	TrackListView(Consumer<Track> onOpen)
	{
		this.onOpen = onOpen;
		TrackRow renderer = new TrackRow();
		setCellRenderer(renderer);
		setFixedCellHeight(renderer.rowHeight());
		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setOpaque(true);
		setFocusable(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		ToolTipManager.sharedInstance().registerComponent(this);

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				openRow(rowAt(e.getPoint()));
			}

			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHover(rowAt(e.getPoint()));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setHover(-1);
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setTracks(List<Track> tracks, String emptyMessage)
	{
		this.emptyMessage = emptyMessage;
		hoverIndex = -1;
		setListData(tracks.toArray(new Track[0]));
		clearSelection();
	}

	int rowCount()
	{
		return getModel().getSize();
	}

	Track trackAt(int index)
	{
		return index >= 0 && index < rowCount() ? getModel().getElementAt(index) : null;
	}

	String emptyMessage()
	{
		return emptyMessage;
	}

	boolean isHovered(int index)
	{
		return index == hoverIndex;
	}

	/** Opens the detail page for the row at {@code index}; out-of-range indexes are ignored. */
	void openRow(int index)
	{
		Track track = trackAt(index);
		if (track != null)
		{
			clearSelection();
			onOpen.accept(track);
		}
	}

	/** The in-game unlock hint of the row, or null when it has none or the index is out of range. */
	String tooltipFor(int index)
	{
		Track track = trackAt(index);
		return track == null || track.getHint().isEmpty() ? null : track.getHint();
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		return tooltipFor(rowAt(event.getPoint()));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (rowCount() == 0 && !emptyMessage.isEmpty())
		{
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.setFont(getFont().deriveFont(getFont().getSize2D() - 1f));
			g.drawString(emptyMessage, TEXT_INSET, TEXT_INSET + g.getFontMetrics().getAscent());
		}
	}

	// locationToIndex snaps to the nearest row even below the last one; only count real hits
	private int rowAt(Point p)
	{
		int index = locationToIndex(p);
		if (index < 0)
		{
			return -1;
		}
		Rectangle bounds = getCellBounds(index, index);
		return bounds != null && bounds.contains(p) ? index : -1;
	}

	private void setHover(int index)
	{
		if (index != hoverIndex)
		{
			hoverIndex = index;
			repaint();
		}
	}
}
