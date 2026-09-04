package com.camerascenes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Keybind;
import org.junit.Test;

public class CameraScenesViewpointHistoryTest
{
	@Test
	public void undoAndRedoRestoreOnlyCameraState()
	{
		Keybind originalKeybind = new Keybind(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
		Keybind changedKeybind = new Keybind(KeyEvent.VK_B, InputEvent.SHIFT_DOWN_MASK);
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint(0, "Original", 100, 200, 500,
			originalKeybind, true);
		viewpoint.setNotes("Original notes");
		CameraScenesViewpointHistory history = new CameraScenesViewpointHistory();

		history.recordBeforeMutation(viewpoint);
		viewpoint.setName("Changed");
		viewpoint.setNotes("Changed notes");
		viewpoint.setYaw(300);
		viewpoint.setPitch(400);
		viewpoint.setZoom(600);
		viewpoint.setKeybind(changedKeybind);
		viewpoint.setEnabled(false);

		assertTrue(history.undo(viewpoint));
		assertEquals("Changed", viewpoint.getName());
		assertEquals("Changed notes", viewpoint.getNotes());
		assertEquals(CameraScenesViewpoint.CardinalDirection.SOUTH.getYaw(), viewpoint.getYaw());
		assertEquals(200, viewpoint.getPitch());
		assertEquals(500, viewpoint.getZoom());
		assertEquals(changedKeybind, viewpoint.getKeybind());
		assertFalse(viewpoint.isEnabled());
		assertTrue(history.canRedo(viewpoint));

		assertTrue(history.redo(viewpoint));
		assertEquals("Changed", viewpoint.getName());
		assertEquals("Changed notes", viewpoint.getNotes());
		assertEquals(CameraScenesViewpoint.CardinalDirection.WEST.getYaw(), viewpoint.getYaw());
		assertEquals(400, viewpoint.getPitch());
		assertEquals(600, viewpoint.getZoom());
		assertEquals(changedKeybind, viewpoint.getKeybind());
		assertFalse(viewpoint.isEnabled());
	}

	@Test
	public void rapidEditsCoalesceAndNewEditsClearRedo()
	{
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint();
		CameraScenesViewpointHistory history = new CameraScenesViewpointHistory();

		history.recordBeforeMutation(viewpoint);
		viewpoint.setYaw(10);
		history.recordBeforeMutation(viewpoint);
		viewpoint.setYaw(20);

		assertTrue(history.undo(viewpoint));
		assertEquals(0, viewpoint.getYaw());
		assertFalse(history.canUndo(viewpoint));
		assertTrue(history.canRedo(viewpoint));

		history.recordBeforeMutation(viewpoint);
		viewpoint.setYaw(30);
		assertFalse(history.canRedo(viewpoint));
	}
}
