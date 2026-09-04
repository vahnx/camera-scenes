package com.camerascenes;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(CameraScenesConfig.GROUP)
public interface CameraScenesConfig extends Config
{
	String GROUP = "eye-spy";

	// The former disableWhileTyping option was retired. Existing saved values are
	// intentionally ignored so removing the UI item does not affect other config keys.

	@ConfigItem(
		keyName = "smoothViewpointLoads",
		name = "Smooth viewpoint loads",
		description = "Moves the camera smoothly when loading a saved viewpoint.",
		position = 10
	)
	default boolean smoothViewpointLoads()
	{
		return true;
	}

	@Units("ms")
	@Range(min = 100, max = 2000)
	@ConfigItem(
		keyName = "viewpointLoadDuration",
		name = "Panning time",
		description = "How long smooth viewpoint panning takes.",
		position = 12
	)
	default int viewpointLoadDuration()
	{
		return 350;
	}

	@ConfigItem(
		keyName = "smoothZoomLoads",
		name = "Smooth zoom during loads",
		description = "Smoothly changes zoom while loading a viewpoint.",
		position = 11
	)
	default boolean smoothZoomLoads()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDebugTextInSidePanel",
		name = "Display camera yaw/pitch/zoom",
		description = "Shows the current camera yaw, pitch, and zoom in the sidepanel.",
		position = 20
	)
	default boolean showDebugTextInSidePanel()
	{
		return false;
	}
}
