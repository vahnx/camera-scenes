package com.camerascenes;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CameraScenesViewpointTest
{
	@Test
	public void captureNormalizesAndClampsCameraValues()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		viewpoint.capture(-1, 9999, -9999);

		assertEquals(CameraScenesViewpoint.CardinalDirection.SOUTH.getYaw(), viewpoint.getYaw());
		assertEquals(CameraScenesViewpoint.MAX_PITCH, viewpoint.getPitch());
		assertEquals(CameraScenesViewpoint.MIN_ZOOM, viewpoint.getZoom());
	}

	@Test
	public void yawSnapsToTheNearestCardinalDirection()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		viewpoint.setYaw(300);
		assertEquals(CameraScenesViewpoint.CardinalDirection.WEST, viewpoint.getDirection());
		assertEquals(512, viewpoint.getYaw());

		viewpoint.setYaw(1792);
		assertEquals(CameraScenesViewpoint.CardinalDirection.SOUTH, viewpoint.getDirection());
	}

	@Test
	public void supportsExtendedPitchValues()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		viewpoint.setPitch(3064);

		assertEquals(3064, viewpoint.getPitch());
	}

	@Test
	public void identifiesPitchOutsideTheDefaultCameraRange()
	{
		assertFalse(CameraScenesViewpoint.usesExtendedPitch(CameraScenesViewpoint.DEFAULT_MIN_PITCH));
		assertFalse(CameraScenesViewpoint.usesExtendedPitch(CameraScenesViewpoint.DEFAULT_MAX_PITCH));
		assertTrue(CameraScenesViewpoint.usesExtendedPitch(CameraScenesViewpoint.MIN_PITCH));
		assertTrue(CameraScenesViewpoint.usesExtendedPitch(CameraScenesViewpoint.MAX_PITCH));
	}

	@Test
	public void acceptsTheFullSupportedPitchAndZoomBoundaries()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		viewpoint.setPitch(CameraScenesViewpoint.MIN_PITCH);
		assertEquals(CameraScenesViewpoint.MIN_PITCH, viewpoint.getPitch());
		viewpoint.setPitch(CameraScenesViewpoint.MAX_PITCH);
		assertEquals(CameraScenesViewpoint.MAX_PITCH, viewpoint.getPitch());

		viewpoint.setZoom(CameraScenesViewpoint.MIN_ZOOM);
		assertEquals(CameraScenesViewpoint.MIN_ZOOM, viewpoint.getZoom());
		viewpoint.setZoom(0);
		assertEquals(0, viewpoint.getZoom());
		viewpoint.setZoom(CameraScenesViewpoint.MAX_ZOOM);
		assertEquals(CameraScenesViewpoint.MAX_ZOOM, viewpoint.getZoom());
	}

	@Test
	public void notesAreOptionalAndNullSafe()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		assertEquals("", viewpoint.getNotes());

		viewpoint.setNotes("South-facing bank viewpoint");
		assertEquals("South-facing bank viewpoint", viewpoint.getNotes());

		viewpoint.setNotes(null);
		assertEquals("", viewpoint.getNotes());
	}

	@Test
	public void keepsLegacyViewpointArrayStorageKey()
	{
		CameraScenesViewpointSet viewpointSet = new Gson().fromJson(
			"{\"points\":[{\"name\":\"Legacy view\"}]}", CameraScenesViewpointSet.class);

		assertEquals("Legacy view", viewpointSet.getViewpoints().get(0).getName());
		assertTrue(new Gson().toJson(viewpointSet).contains("\"points\""));
	}
}
