package com.camerascenes;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;

/** Persists Camera Scenes profiles without coupling storage details to the plugin lifecycle. */
final class CameraScenesProfileStore
{
	private static final String VIEWPOINT_SETS_KEY = "groups";

	private final Gson gson;
	private final ConfigManager configManager;

	@Inject
	CameraScenesProfileStore(Gson gson, ConfigManager configManager)
	{
		this.gson = gson;
		this.configManager = configManager;
	}

	List<CameraScenesViewpointSet> read()
	{
		String serializedSets = configManager.getConfiguration(CameraScenesConfig.GROUP, VIEWPOINT_SETS_KEY);
		if (serializedSets == null || serializedSets.trim().isEmpty()) return Collections.emptyList();
		CameraScenesViewpointSet[] decodedSets = gson.fromJson(serializedSets, CameraScenesViewpointSet[].class);
		return decodedSets == null ? Collections.emptyList() : Arrays.asList(decodedSets);
	}

	void write(List<CameraScenesViewpointSet> viewpointSets)
	{
		configManager.setConfiguration(CameraScenesConfig.GROUP, VIEWPOINT_SETS_KEY, gson.toJson(viewpointSets));
	}
}
