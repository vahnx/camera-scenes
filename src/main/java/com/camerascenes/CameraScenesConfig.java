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

	// Retained as unexposed legacy accessors so older backup files remain readable.
	// Viewpoint loads are intentionally immediate because attached-camera handoff
	// recalculation produced an inconsistent visible bump during smooth loads.
	@Deprecated
	@ConfigItem(
		keyName = "smoothViewpointLoads",
		name = "Smooth viewpoint loads",
		description = "Retired; viewpoint loads are immediate.",
		hidden = true,
		position = 10
	)
	default boolean smoothViewpointLoads()
	{
		return false;
	}

	@Deprecated
	@Units("ms")
	@Range(min = 100, max = 30000)
	@ConfigItem(
		keyName = "viewpointLoadDuration",
		name = "Panning time",
		description = "Retired; viewpoint loads are immediate.",
		hidden = true,
		position = 12
	)
	default int viewpointLoadDuration()
	{
		return 350;
	}

	@Deprecated
	@ConfigItem(
		keyName = "smoothZoomLoads",
		name = "Smooth zoom during loads",
		description = "Retired; viewpoint loads are immediate.",
		hidden = true,
		position = 11
	)
	default boolean smoothZoomLoads()
	{
		return false;
	}

	// Retained as an unexposed development/backup accessor. The plugin has no
	// user-facing configuration options, so diagnostics remain disabled.
	@ConfigItem(
		keyName = "showDebugTextInSidePanel",
		name = "Display camera yaw/pitch/zoom",
		description = "Retired development diagnostic.",
		hidden = true,
		position = 20
	)
	default boolean showDebugTextInSidePanel()
	{
		return false;
	}
}
