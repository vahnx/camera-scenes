package com.camerascenes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CameraScenesCameraInterpolationTest
{
	@Test
	public void yawUsesTheShortestWraparoundPath()
	{
		assertEquals(0, CameraScenesCameraInterpolation.yaw(16380, 4, 0.5));
		assertEquals(0, CameraScenesCameraInterpolation.yaw(4, 16380, 0.5));
		assertEquals(16383, CameraScenesCameraInterpolation.yaw(16383, 16383, 1.0));
		assertEquals(0, CameraScenesCameraInterpolation.yaw(0, 0, 1.0));
	}

	@Test
	public void linearInterpolationReachesBothEndpoints()
	{
		assertEquals(150, CameraScenesCameraInterpolation.linear(50, 150, 1.0));
		assertEquals(50, CameraScenesCameraInterpolation.linear(50, 150, 0.0));
		assertEquals(-272, CameraScenesCameraInterpolation.linear(512, -272, 1.0));
		assertEquals(1400, CameraScenesCameraInterpolation.linear(-272, 1400, 1.0));
		assertEquals(0.5, CameraScenesCameraInterpolation.easeInOut(0.5), 0.000001);
	}
}
