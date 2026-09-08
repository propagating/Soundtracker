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
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import com.soundtracker.catalog.Track;
import com.soundtracker.catalog.TrackEnrichment;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TrackMapMarkerTest
{
	@Mock
	private Client client;
	@Mock
	private ClientThread clientThread;
	@Mock
	private WorldMapPointManager manager;
	@Mock
	private WorldMap worldMap;

	private TrackMapMarker marker;

	private static Track track(String name, Integer x, Integer y, int plane)
	{
		TrackEnrichment e = new TrackEnrichment();
		if (x != null)
		{
			TrackEnrichment.MapPoint p = new TrackEnrichment.MapPoint();
			p.setX(x);
			p.setY(y);
			p.setPlane(plane);
			e.setMap(p);
		}
		return Track.builder().name(name).sortName(name).hint("").midiId(1).varp(-1).bit(-1)
			.unlocked(false).holiday(false).automatic(false).enrichment(e).build();
	}

	private static Track underground(String name, int x, int y, int ex, int ey, String entranceName)
	{
		Track t = track(name, x, y, 0);
		TrackEnrichment.Entrance entrance = new TrackEnrichment.Entrance();
		entrance.setX(ex);
		entrance.setY(ey);
		entrance.setPlane(0);
		entrance.setName(entranceName);
		t.getEnrichment().setEntrance(entrance);
		return t;
	}

	@Before
	public void setUp()
	{
		doAnswer(inv ->
		{
			((Runnable) inv.getArgument(0)).run();
			return null;
		}).when(clientThread).invokeLater(any(Runnable.class));
		when(client.getWorldMap()).thenReturn(worldMap);
		marker = new TrackMapMarker(client, clientThread, manager,
			new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB));
	}

	@Test
	public void showAddsOnePointAndPansToIt()
	{
		marker.show(track("Sea Shanty 2", 3035, 3225, 0));
		ArgumentCaptor<WorldMapPoint> point = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(manager).add(point.capture());
		assertEquals(new WorldPoint(3035, 3225, 0), point.getValue().getWorldPoint());
		assertEquals("Sea Shanty 2", point.getValue().getName());
		verify(worldMap).setWorldMapPositionTarget(new WorldPoint(3035, 3225, 0));
	}

	@Test
	public void undergroundTrackMarksTheEntranceAndPansThere()
	{
		marker.show(underground("Arabique", 2816, 9824, 2884, 3397, "Taverley Dungeon"));
		ArgumentCaptor<WorldMapPoint> points = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(manager, org.mockito.Mockito.times(2)).add(points.capture());
		WorldMapPoint entrance = points.getAllValues().get(0);
		WorldMapPoint spot = points.getAllValues().get(1);
		assertEquals(new WorldPoint(2884, 3397, 0), entrance.getWorldPoint());
		assertEquals("Arabique: entrance to Taverley Dungeon", entrance.getTooltip());
		assertEquals(new WorldPoint(2816, 9824, 0), spot.getWorldPoint());
		verify(worldMap).setWorldMapPositionTarget(new WorldPoint(2884, 3397, 0));
	}

	@Test
	public void clearRemovesBothMarkers()
	{
		marker.show(underground("Arabique", 2816, 9824, 2884, 3397, "Taverley Dungeon"));
		marker.clear();
		verify(manager, org.mockito.Mockito.times(2)).remove(any(WorldMapPoint.class));
	}

	@Test
	public void showReplacesThePreviousPoint()
	{
		marker.show(track("A", 1, 2, 0));
		marker.show(track("B", 3, 4, 1));
		ArgumentCaptor<WorldMapPoint> added = ArgumentCaptor.forClass(WorldMapPoint.class);
		verify(manager, org.mockito.Mockito.times(2)).add(added.capture());
		verify(manager).remove(added.getAllValues().get(0));
	}

	@Test
	public void trackWithoutMapOnlyClears()
	{
		marker.show(track("A", 1, 2, 0));
		marker.show(track("No map", null, null, 0));
		verify(manager, org.mockito.Mockito.times(1)).add(any(WorldMapPoint.class));
		verify(manager, org.mockito.Mockito.times(1)).remove(any(WorldMapPoint.class));
	}

	@Test
	public void clearWithoutShowDoesNothing()
	{
		marker.clear();
		verify(manager, never()).remove(any(WorldMapPoint.class));
	}

	@Test
	public void missingIconSkipsTheMarker()
	{
		TrackMapMarker noIcon = new TrackMapMarker(client, clientThread, manager, null);
		noIcon.show(track("A", 1, 2, 0));
		verify(manager, never()).add(any(WorldMapPoint.class));
	}
}
