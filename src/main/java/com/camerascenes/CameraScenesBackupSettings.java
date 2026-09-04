package com.camerascenes;

/** Camera Scenes config values included in a full backup. */
final class CameraScenesBackupSettings
{
	private boolean smoothViewpointLoads;
	private int viewpointLoadDuration;
	private boolean smoothZoomLoads;
	private boolean showDebugTextInSidePanel;

	CameraScenesBackupSettings()
	{
	}

	static CameraScenesBackupSettings from(CameraScenesConfig config)
	{
		CameraScenesBackupSettings settings = new CameraScenesBackupSettings();
		settings.smoothViewpointLoads = config.smoothViewpointLoads();
		settings.viewpointLoadDuration = config.viewpointLoadDuration();
		settings.smoothZoomLoads = config.smoothZoomLoads();
		settings.showDebugTextInSidePanel = config.showDebugTextInSidePanel();
		return settings;
	}

	boolean isSmoothViewpointLoads()
	{
		return smoothViewpointLoads;
	}

	int getViewpointLoadDuration()
	{
		return viewpointLoadDuration;
	}

	boolean isSmoothZoomLoads()
	{
		return smoothZoomLoads;
	}

	boolean isShowDebugTextInSidePanel()
	{
		return showDebugTextInSidePanel;
	}
}
