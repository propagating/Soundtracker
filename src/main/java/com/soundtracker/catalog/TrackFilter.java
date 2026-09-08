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
package com.soundtracker.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pure list shaping for the panel: which tracks to show and in what order. */
public final class TrackFilter
{
	public enum LockFilter
	{
		ALL("All"),
		LOCKED("Locked"),
		UNLOCKED("Unlocked"),
		/** Still locked and counted toward the music cape: the player's to-do list. */
		CAPE("Needed for cape");

		private final String label;

		LockFilter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum SortMode
	{
		NAME("Name"),
		LOCATION("Location");

		private final String label;

		SortMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private static final Comparator<Track> BY_NAME = Comparator
		.comparing((Track t) -> lower(t.getSortName() == null ? t.getName() : t.getSortName()))
		.thenComparing(Track::getName);

	// tracks with no known region sink to the bottom rather than forming a blank group on top
	private static final Comparator<Track> BY_LOCATION = Comparator
		.comparing((Track t) -> t.getEnrichment().getRegion() == null)
		.thenComparing(t -> lower(nullToEmpty(t.getEnrichment().getRegion())))
		.thenComparing(BY_NAME);

	private TrackFilter()
	{
	}

	/**
	 * @param clueOnly keep only tracks a Treasure Trails step can ask for
	 */
	public static List<Track> apply(Collection<Track> tracks, String query, LockFilter lock,
		SortMode sort, boolean showHoliday, boolean clueOnly)
	{
		String q = query == null ? "" : lower(query.trim());
		List<Track> out = new ArrayList<>();
		for (Track t : tracks)
		{
			if (!showHoliday && t.isHoliday())
			{
				continue;
			}
			if (clueOnly && !t.isClueTrack())
			{
				continue;
			}
			if (lock == LockFilter.LOCKED && t.isUnlocked())
			{
				continue;
			}
			if (lock == LockFilter.UNLOCKED && !t.isUnlocked())
			{
				continue;
			}
			if (lock == LockFilter.CAPE && (t.isUnlocked() || !t.countsForCape()))
			{
				continue;
			}
			if (!q.isEmpty() && !lower(t.getName()).contains(q))
			{
				continue;
			}
			out.add(t);
		}
		out.sort(sort == SortMode.LOCATION ? BY_LOCATION : BY_NAME);
		return out;
	}

	private static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}

	private static String nullToEmpty(String s)
	{
		return s == null ? "" : s;
	}
}
