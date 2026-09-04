package com.camerascenes;

/** Pure camera interpolation helpers used by the Camera Scenes load transition. */
final class CameraScenesCameraInterpolation
{
	private static final int HALF_TURN = CameraScenesViewpoint.YAW_UNITS / 2;

	private CameraScenesCameraInterpolation()
	{
	}

	static int yaw(int start, int target, double progress)
	{
		int shortestDelta = Math.floorMod(target - start + HALF_TURN, CameraScenesViewpoint.YAW_UNITS) - HALF_TURN;
		return CameraScenesViewpoint.normalizeYaw(start + (int) Math.round(shortestDelta * progress));
	}

	static int linear(int start, int target, double progress)
	{
		return start + (int) Math.round((target - start) * progress);
	}

	static double easeInOut(double progress)
	{
		return progress * progress * (3.0 - (2.0 * progress));
	}
}
