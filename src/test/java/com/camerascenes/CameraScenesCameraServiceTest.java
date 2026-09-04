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
}
