package com.camerascenes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CameraScenesCameraServiceTest
{
	@Test
	public void negativeZoomIsAReadyCameraValue()
	{
		assertTrue(CameraScenesCameraService.hasReadyCameraState(-94, 1922));
		assertTrue(CameraScenesCameraService.hasReadyCameraState(527, 0));
	}

	@Test
	public void zeroZoomAndPitchStillRepresentUninitializedCameraState()
	{
		assertFalse(CameraScenesCameraService.hasReadyCameraState(0, 0));
	}

	@Test
	public void cameraSettledAllowsSmallClientRoundingDifference()
	{
		assertTrue(CameraScenesCameraService.hasCameraSettled(1023, 256, 1024, 256));
		assertTrue(CameraScenesCameraService.hasCameraSettled(1024, 257, 1024, 256));
		assertFalse(CameraScenesCameraService.hasCameraSettled(1020, 256, 1024, 256));
	}

	@Test
	public void cameraSettledHandlesYawWraparound()
	{
		assertTrue(CameraScenesCameraService.hasCameraSettled(16383, 256, 0, 256));
	}
}
