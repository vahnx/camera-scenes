package com.camerascenes;

import javax.inject.Singleton;

/** Holds the latest safe-to-display shortcut state for the Camera Scenes diagnostics panel. */
@Singleton
final class CameraScenesShortcutDiagnostics
{
	private volatile String lastEvent = "waiting";

	void record(String event)
	{
		lastEvent = event;
	}

	String getLastEvent()
	{
		return lastEvent;
	}
}
