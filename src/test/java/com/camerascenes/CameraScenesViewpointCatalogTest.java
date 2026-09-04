package com.camerascenes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.config.Keybind;
import org.junit.Test;

public class CameraScenesViewpointCatalogTest
{
	@Test
	public void normalizesLoadedIdsAndNullCollections()
	{
		CameraScenesViewpointSet viewpointSet = new CameraScenesViewpointSet();
		viewpointSet.setId(99);
		viewpointSet.setViewpoints(null);
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(viewpointSet));

		assertEquals(0, catalog.listAllSets().get(0).getId());
		assertEquals(0, catalog.listAllSets().get(0).getViewpoints().size());
	}

	@Test
	public void cyclesEnabledPointsAndWraps()
	{
		CameraScenesViewpointSet viewpointSet = new CameraScenesViewpointSet(0, "Group");
		CameraScenesViewpoint first = new CameraScenesViewpoint(0, "First", 0, 128, 512, null, true);
		CameraScenesViewpoint second = new CameraScenesViewpoint(1, "Second", 0, 128, 512, null, true);
		viewpointSet.setViewpoints(Arrays.asList(first, second));
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(viewpointSet));

		assertSame(first, catalog.advance(viewpointSet));
		assertSame(second, catalog.advance(viewpointSet));
		assertSame(first, catalog.advance(viewpointSet));
		assertSame(second, catalog.rewind(viewpointSet));
	}

	@Test
	public void tracksCurrentViewpointAndNotifiesListeners()
	{
		CameraScenesViewpointSet viewpointSet = new CameraScenesViewpointSet(0, "Group");
		CameraScenesViewpoint first = new CameraScenesViewpoint(0, "First", 0, 128, 512, null, true);
		CameraScenesViewpoint second = new CameraScenesViewpoint(1, "Second", 0, 128, 512, null, true);
		viewpointSet.setViewpoints(Arrays.asList(first, second));
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		AtomicInteger notifications = new AtomicInteger();
		catalog.addSelectionListener(notifications::incrementAndGet);
		catalog.replaceAll(Arrays.asList(viewpointSet));

		catalog.markSelected(viewpointSet, second);
		catalog.markSelected(viewpointSet, second);

		assertFalse(catalog.isCurrent(viewpointSet, first));
		assertTrue(catalog.isCurrent(viewpointSet, second));
		assertEquals(1, notifications.get());
	}

	@Test
	public void rejectsActiveDuplicateKeybindsButIgnoresDisabledGroups()
	{
		Keybind keybind = new Keybind(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK);
		CameraScenesViewpoint first = new CameraScenesViewpoint(0, "First", 0, 128, 512, keybind, true);
		CameraScenesViewpoint second = new CameraScenesViewpoint(0, "Second", 0, 128, 512, keybind, true);
		CameraScenesViewpointSet activeGroup = new CameraScenesViewpointSet(0, "Active");
		CameraScenesViewpointSet disabledGroup = new CameraScenesViewpointSet(1, "Disabled");
		activeGroup.setViewpoints(Arrays.asList(first));
		disabledGroup.setEnabled(false);
		disabledGroup.setViewpoints(Arrays.asList(second));
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(activeGroup, disabledGroup));

		assertNull(catalog.findActiveKeybindConflict(keybind, activeGroup, first, false));
		Keybind groupKeybind = new Keybind(KeyEvent.VK_C, 0);
		activeGroup.setPreviousKeybind(groupKeybind);
		assertNull(catalog.findActiveKeybindConflict(groupKeybind, activeGroup, null, false));

		activeGroup.setViewpoints(Arrays.asList(first, second));
		assertNotNull(catalog.findActiveKeybindConflict(keybind, activeGroup, first, false));
	}

	@Test
	public void treatsDeserializedNotSetValuesAsUnset()
	{
		Keybind persistedNotSet = new Keybind(Keybind.NOT_SET.getKeyCode(), Keybind.NOT_SET.getModifiers());
		CameraScenesViewpoint viewpoint = new CameraScenesViewpoint(0, "First", 0, 128, 512, persistedNotSet, true);
		CameraScenesViewpointSet viewpointSet = new CameraScenesViewpointSet(0, "Group");
		viewpointSet.setViewpoints(Arrays.asList(viewpoint));
		viewpointSet.setPreviousKeybind(persistedNotSet);

		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(viewpointSet));

		assertSame(Keybind.NOT_SET, viewpoint.getKeybind());
		assertSame(Keybind.NOT_SET, viewpointSet.getPreviousKeybind());
		assertTrue(catalog.listAllActiveKeybindConflicts().isEmpty());
	}

	@Test
	public void detectsConflictWhenDisabledGroupIsEnabled()
	{
		Keybind keybind = new Keybind(KeyEvent.VK_B, 0);
		CameraScenesViewpoint activeViewpoint = new CameraScenesViewpoint(0, "Active", 0, 128, 512, keybind, true);
		CameraScenesViewpointSet activeGroup = new CameraScenesViewpointSet(0, "Active");
		activeGroup.setViewpoints(Arrays.asList(activeViewpoint));
		CameraScenesViewpointSet disabledGroup = new CameraScenesViewpointSet(1, "Disabled");
		disabledGroup.setEnabled(false);
		disabledGroup.setNextKeybind(keybind);
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(activeGroup, disabledGroup));

		assertNotNull(catalog.findGroupActivationConflict(disabledGroup));
	}

	@Test
	public void clearsOnlyActiveConflictsWhenReassigning()
	{
		Keybind keybind = new Keybind(KeyEvent.VK_D, 0);
		CameraScenesViewpoint activeViewpoint = new CameraScenesViewpoint(0, "Active", 0, 128, 512, keybind, true);
		CameraScenesViewpoint disabledViewpoint = new CameraScenesViewpoint(0, "Disabled", 0, 128, 512, keybind, true);
		CameraScenesViewpointSet activeGroup = new CameraScenesViewpointSet(0, "Active");
		CameraScenesViewpointSet disabledGroup = new CameraScenesViewpointSet(1, "Disabled");
		activeGroup.setViewpoints(Arrays.asList(activeViewpoint));
		disabledGroup.setEnabled(false);
		disabledGroup.setViewpoints(Arrays.asList(disabledViewpoint));
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(activeGroup, disabledGroup));

		assertEquals(1, catalog.clearActiveKeybindConflicts(keybind, disabledGroup, disabledViewpoint, false));
		assertEquals(Keybind.NOT_SET, activeViewpoint.getKeybind());
		assertEquals(keybind, disabledViewpoint.getKeybind());
	}

	@Test
	public void clearsActiveConflictsWhenGroupIsActivated()
	{
		Keybind keybind = new Keybind(KeyEvent.VK_E, 0);
		CameraScenesViewpoint activeViewpoint = new CameraScenesViewpoint(0, "Active", 0, 128, 512, keybind, true);
		CameraScenesViewpointSet activeGroup = new CameraScenesViewpointSet(0, "Active");
		activeGroup.setViewpoints(Arrays.asList(activeViewpoint));
		CameraScenesViewpointSet disabledGroup = new CameraScenesViewpointSet(1, "Disabled");
		disabledGroup.setEnabled(false);
		disabledGroup.setNextKeybind(keybind);
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(activeGroup, disabledGroup));

		assertNotNull(catalog.findGroupActivationConflict(disabledGroup));
		assertEquals(1, catalog.clearGroupActivationConflicts(disabledGroup));
		assertEquals(Keybind.NOT_SET, activeViewpoint.getKeybind());
		assertFalse(disabledGroup.isEnabled());
	}

	@Test
	public void listsAllConflictsForGroupActivation()
	{
		Keybind firstKeybind = new Keybind(KeyEvent.VK_F, 0);
		Keybind secondKeybind = new Keybind(KeyEvent.VK_G, 0);
		CameraScenesViewpoint firstActiveViewpoint = new CameraScenesViewpoint(0, "First active", 0, 128, 512, firstKeybind, true);
		CameraScenesViewpoint secondActiveViewpoint = new CameraScenesViewpoint(1, "Second active", 0, 128, 512, secondKeybind, true);
		CameraScenesViewpointSet activeGroup = new CameraScenesViewpointSet(0, "Active");
		activeGroup.setViewpoints(Arrays.asList(firstActiveViewpoint, secondActiveViewpoint));
		CameraScenesViewpointSet disabledGroup = new CameraScenesViewpointSet(1, "Disabled");
		disabledGroup.setEnabled(false);
		disabledGroup.setPreviousKeybind(firstKeybind);
		disabledGroup.setNextKeybind(secondKeybind);
		CameraScenesViewpointCatalog catalog = new CameraScenesViewpointCatalog();
		catalog.replaceAll(Arrays.asList(activeGroup, disabledGroup));

		assertEquals(2, catalog.listGroupActivationConflicts(disabledGroup).size());
	}
}
