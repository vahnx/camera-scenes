package com.camerascenes;

/** Camera target values captured from the active RuneLite client. */
final class CameraScenesCameraSnapshot
{
	private final int yaw;
	private final int pitch;
	private final int zoom;

	CameraScenesCameraSnapshot(int yaw, int pitch, int zoom)
	{
		this.yaw = yaw;
		this.pitch = pitch;
		this.zoom = zoom;
	}

	int getYaw() { return yaw; }
	int getPitch() { return pitch; }
	int getZoom() { return zoom; }
}
