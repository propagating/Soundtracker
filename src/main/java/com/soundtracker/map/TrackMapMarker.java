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
package com.soundtracker.map;

import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.soundtracker.catalog.Track;
import com.soundtracker.catalog.TrackEnrichment;

/**
 * Sole owner of this plugin's world-map state: one marker for the track the player is looking
 * at, and the pan target so opening the map centres on it. EDT-safe; the pan target crosses to
 * the client thread.
 */
@Slf4j
public class TrackMapMarker
{
	private final Client client;
	private final ClientThread clientThread;
	private final WorldMapPointManager manager;
	private final BufferedImage icon;
	private WorldMapPoint shown;

	public TrackMapMarker(Client client, ClientThread clientThread, WorldMapPointManager manager,
		BufferedImage icon)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.manager = manager;
		this.icon = icon;
	}

	/** Replaces any earlier marker with this track's, or just clears when it has no map point. */
	public void show(Track track)
	{
		clear();
		TrackEnrichment.MapPoint map = track.getEnrichment().getMap();
		if (map == null)
		{
			return;
		}
		if (icon == null)
		{
			log.warn("soundtracker: marker icon missing, not marking map");
			return;
		}
		WorldPoint target = new WorldPoint(map.getX(), map.getY(), map.getPlane());
		WorldMapPoint point = WorldMapPoint.builder()
			.worldPoint(target)
			.image(icon)
			.snapToEdge(true)
			.jumpOnClick(true)
			.name(track.getName())
			.tooltip(map.getPlane() > 0 ? track.getName() + " (floor " + map.getPlane() + ")" : track.getName())
			.build();
		manager.add(point);
		shown = point;
		clientThread.invokeLater(() ->
		{
			WorldMap worldMap = client.getWorldMap();
			if (worldMap != null)
			{
				worldMap.setWorldMapPositionTarget(target);
			}
		});
	}

	public void clear()
	{
		if (shown != null)
		{
			manager.remove(shown);
			shown = null;
		}
	}
}
