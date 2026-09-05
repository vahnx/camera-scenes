package com.camerascenes;

/** Live camera values used by the Camera Scenes diagnostics display. */
final class CameraScenesCameraState
{
	private final int currentYaw;
	private final int currentPitch;
	private final int yawTarget;
	private final int pitchTarget;
	private final int zoom;
	private String diagnostics = "";
	void setDiagnostics(String value) { diagnostics = value; }
	String getDiagnostics() { return diagnostics; }

	CameraScenesCameraState(int currentYaw, int currentPitch, int yawTarget, int pitchTarget, int zoom)
	{
		this.currentYaw = currentYaw;
		this.currentPitch = currentPitch;
		this.yawTarget = yawTarget;
		this.pitchTarget = pitchTarget;
		this.zoom = zoom;
	}

	int getCurrentYaw() { return currentYaw; }
	int getCurrentPitch() { return currentPitch; }
	int getYawTarget() { return yawTarget; }
	int getPitchTarget() { return pitchTarget; }
	int getZoom() { return zoom; }
}
