package com.camerascenes;

import com.google.gson.Gson;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CameraScenesBackupServiceTest
{
	@Test
	public void roundTripsFullBackupIncludingHotkeysAndSettings() throws Exception
	{
		KeybindPair keybinds = new KeybindPair();
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint(0, "Bank", 1234, 3064, 640,
			keybinds.viewpoint, false);
		viewpoint.setNotes("Use this at the bank");
		CameraScenesViewpointSet group = new CameraScenesViewpointSet(0, "Bank scenes");
		group.setEnabled(false);
		group.setExpanded(false);
		group.setPreviousKeybind(keybinds.previous);
		group.setNextKeybind(keybinds.next);
		group.setViewpoints(Arrays.asList(viewpoint));

		CameraScenesBackupService service = new CameraScenesBackupService(new Gson());
		File file = File.createTempFile("camera-scenes-backup", ".json");
		file.deleteOnExit();
		service.write(file, CameraScenesBackup.all(Arrays.asList(group), CameraScenesBackupSettings.from(new CameraScenesConfig() { } )));

		CameraScenesBackup decoded = service.read(file);
		assertEquals(CameraScenesBackup.CURRENT_SCHEMA_VERSION, decoded.getSchemaVersion());
		assertEquals(CameraScenesBackup.SCOPE_ALL, decoded.getScope());
		assertEquals(group, decoded.getGroups().get(0));
		assertTrue(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains("\"previousKeybind\""));
		assertTrue(decoded.getSettings().isSmoothViewpointLoads());
	}

	@Test(expected = java.io.IOException.class)
	public void rejectsFutureSchemaVersions() throws Exception
	{
		CameraScenesBackupService service = new CameraScenesBackupService(new Gson());
		File file = File.createTempFile("camera-scenes-future", ".json");
		file.deleteOnExit();
		Files.write(file.toPath(), ("{\"schemaVersion\":99,\"format\":\"camera-scenes-backup\","
			+ "\"scope\":\"all\",\"settings\":{},\"groups\":[]}").getBytes(StandardCharsets.UTF_8));
		service.read(file);
	}

	private static final class KeybindPair
	{
		private final net.runelite.client.config.Keybind previous =
			new net.runelite.client.config.Keybind(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK);
		private final net.runelite.client.config.Keybind next =
			new net.runelite.client.config.Keybind(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK);
		private final net.runelite.client.config.Keybind viewpoint =
			new net.runelite.client.config.Keybind(KeyEvent.VK_B, InputEvent.SHIFT_DOWN_MASK);
	}
}
