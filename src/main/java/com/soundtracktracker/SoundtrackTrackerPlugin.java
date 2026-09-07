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
package com.soundtracktracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;
import com.soundtracktracker.catalog.EnrichmentStore;
import com.soundtracktracker.catalog.TrackCatalog;
import com.soundtracktracker.map.TrackMapMarker;
import com.soundtracktracker.ui.SoundtrackTrackerPanel;

@Slf4j
@PluginDescriptor(
	name = "Soundtrack Tracker",
	description = "Track which music tracks you have unlocked and how to unlock the rest for the music cape",
	tags = {"music", "cape", "soundtrack", "tracks", "unlock"}
)
public class SoundtrackTrackerPlugin extends Plugin
{
	static final String TRACKS_RESOURCE = "/com/soundtracktracker/tracks.json";

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private Gson gson;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private SoundtrackTrackerConfig config;
	@Inject
	private WorldMapPointManager worldMapPointManager;

	// written on the EDT in startUp/shutDown, read on the client thread (event subscribers)
	private volatile EnrichmentStore enrichmentStore;
	private volatile TrackCatalog catalog;
	private volatile SoundtrackTrackerPanel panel;
	private NavigationButton navButton;
	private volatile TrackMapMarker mapMarker;
	// set on the client thread once tracks.json is parsed; the catalog must not read rows
	// before then or every track would be joined to an empty enrichment for the whole run
	private volatile boolean dataReady;

	private final TrackCatalog.Listener toPanel = tracks ->
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setTracks(tracks);
			}
		});

	@Provides
	SoundtrackTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SoundtrackTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		dataReady = false;
		enrichmentStore = new EnrichmentStore(gson, TRACKS_RESOURCE);
		catalog = new TrackCatalog(client, enrichmentStore);
		catalog.addListener(toPanel);

		BufferedImage markerIcon;
		try
		{
			markerIcon = ImageUtil.loadImageResource(getClass(), "map_marker.png");
		}
		catch (Exception e)
		{
			log.warn("soundtracktracker: failed to load map marker icon", e);
			markerIcon = null;
		}
		mapMarker = new TrackMapMarker(client, clientThread, worldMapPointManager, markerIcon);
		panel = new SoundtrackTrackerPanel(config, mapMarker);
		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Soundtrack Tracker")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		// parse the bundled JSON off the client thread, then load the catalog if already in game
		final EnrichmentStore store = enrichmentStore;
		final TrackCatalog cat = catalog;
		executor.execute(() ->
		{
			store.load();
			clientThread.invoke(() ->
			{
				if (catalog != cat)
				{
					return; // plugin was stopped while we were parsing
				}
				dataReady = true;
				if (client.getGameState() == GameState.LOGGED_IN)
				{
					cat.load();
				}
			});
		});
	}

	@Override
	protected void shutDown()
	{
		if (catalog != null)
		{
			catalog.removeListener(toPanel);
			catalog = null;
		}
		if (mapMarker != null)
		{
			mapMarker.clear();
			mapMarker = null;
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (panel != null)
		{
			// RuneLite calls shutDown() on the EDT, same as startUp(), so this can call
			// straight into the panel rather than hopping back through invokeLater
			panel.dispose();
			panel = null;
		}
		enrichmentStore = null;
		dataReady = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (catalog == null)
		{
			return;
		}
		if (event.getGameState() == GameState.LOGGED_IN && dataReady)
		{
			catalog.load();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			catalog.reset();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (catalog != null && event.getVarpId() >= 0)
		{
			catalog.onVarpChanged(event.getVarpId());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!SoundtrackTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.refreshFromConfig();
			}
		});
	}
}
