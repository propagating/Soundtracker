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

import lombok.Data;

/**
 * One row of tracks.json: what the wiki knows about a track that the game cache does not.
 * Field names match the JSON keys so Gson needs no adapters.
 */
@Data
public class TrackEnrichment
{
	/** Stands in for a track the wiki sweep did not cover; every getter returns null. */
	public static final TrackEnrichment EMPTY = new TrackEnrichment();

	private Integer cacheid;
	private String name;
	private String location;
	private String region;
	private String quest;
	private String unlockdetail;
	private Boolean members;
	private MapPoint map;
	private String wikiUrl;
	/** Clue tier (easy, medium, hard, elite) whose Cecilia step can ask for this track; null otherwise. */
	private String clueTier;
	/** The wiki's note for that clue step, e.g. a quest requirement; null when there is none. */
	private String clueNote;

	@Data
	public static class MapPoint
	{
		private int x;
		private int y;
		private int plane;
	}
}
