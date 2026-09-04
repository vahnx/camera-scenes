package com.camerascenes;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;

/** Resolves Camera Scenes shortcuts without making the plugin lifecycle handle input details. */
final class CameraScenesShortcutRouter implements KeyListener
{
	private final Client client;
	private final CameraScenesViewpointCatalog viewpointCatalog;
	private final CameraScenesCameraService cameraService;
	private final CameraScenesShortcutDiagnostics shortcutDiagnostics;
	private final Set<Keybind> pressedShortcuts = new HashSet<>();

	@Inject
	CameraScenesShortcutRouter(Client client, CameraScenesViewpointCatalog viewpointCatalog,
		CameraScenesCameraService cameraService, CameraScenesShortcutDiagnostics shortcutDiagnostics)
	{
		this.client = client;
		this.viewpointCatalog = viewpointCatalog;
		this.cameraService = cameraService;
		this.shortcutDiagnostics = shortcutDiagnostics;
	}

	@Override
	public void keyPressed(KeyEvent keyEvent)
	{
		if (keyEvent == null) return;
		String keyDescription = describe(keyEvent);
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			shortcutDiagnostics.record(keyDescription + " blocked: not logged in");
			return;
		}
		shortcutDiagnostics.record(keyDescription + " received: no match " + describeEvent(keyEvent)
			+ "; saved bindings: " + describeSavedBindings());

		for (CameraScenesViewpointSet viewpointSet : viewpointCatalog.listEnabledSets())
		{
			if (matches(viewpointSet.getNextKeybind(), keyEvent))
			{
				trigger(viewpointSet.getNextKeybind(), "group next",
					viewpointCatalog.advance(viewpointSet), keyEvent, keyDescription);
				return;
			}
			if (matches(viewpointSet.getPreviousKeybind(), keyEvent))
			{
				trigger(viewpointSet.getPreviousKeybind(), "group previous",
					viewpointCatalog.rewind(viewpointSet), keyEvent, keyDescription);
				return;
			}
			for (CameraScenesViewpoint savedViewpoint : viewpointCatalog.listEnabledViewpoints(viewpointSet))
			{
				if (matches(savedViewpoint.getKeybind(), keyEvent))
				{
					viewpointCatalog.markSelected(viewpointSet, savedViewpoint);
					trigger(savedViewpoint.getKeybind(), "viewpoint load", savedViewpoint,
						keyEvent, keyDescription);
					return;
				}
			}
		}
	}

	@Override
	public void keyReleased(KeyEvent keyEvent)
	{
		if (keyEvent == null) return;
		pressedShortcuts.removeIf(keybind -> keybind.matches(keyEvent));
	}

	@Override
	public void keyTyped(KeyEvent keyEvent)
	{
		if (keyEvent != null && !pressedShortcuts.isEmpty())
		{
			keyEvent.consume();
		}
	}

	@Override
	public void focusLost()
	{
		pressedShortcuts.clear();
	}

	private void trigger(Keybind keybind, String action, CameraScenesViewpoint savedViewpoint, KeyEvent keyEvent,
		String keyDescription)
	{
		shortcutDiagnostics.record(keyDescription + " matched: " + action);
		if (pressedShortcuts.add(keybind) && savedViewpoint != null)
		{
			cameraService.apply(savedViewpoint);
		}
		if (Keybind.getModifierForKeyCode(keyEvent.getKeyCode()) == null)
		{
			keyEvent.consume();
		}
	}

	private static boolean matches(Keybind keybind, KeyEvent keyEvent)
	{
		if (CameraScenesViewpoint.isUnsetKeybind(keybind))
		{
			return false;
		}

		// RuneLite normally supplies an extended key code. Some desktop-remote and
		// alternate keyboard paths can leave it undefined, while the ordinary code
		// is still valid. Keep RuneLite's exact modifier semantics in either case.
		int eventKeyCode = keyEvent.getExtendedKeyCode();
		if (eventKeyCode == KeyEvent.VK_UNDEFINED)
		{
			eventKeyCode = keyEvent.getKeyCode();
		}
		if (eventKeyCode == KeyEvent.VK_UNDEFINED)
		{
			return false;
		}

		Integer modifierKeyCode = Keybind.getModifierForKeyCode(eventKeyCode);
		int eventModifiers = keyEvent.getModifiersEx()
			& (java.awt.event.InputEvent.CTRL_DOWN_MASK
				| java.awt.event.InputEvent.ALT_DOWN_MASK
				| java.awt.event.InputEvent.SHIFT_DOWN_MASK
				| java.awt.event.InputEvent.META_DOWN_MASK);
		if (modifierKeyCode != null)
		{
			eventKeyCode = KeyEvent.VK_UNDEFINED;
			eventModifiers |= modifierKeyCode;
		}
		return keybind.getKeyCode() == eventKeyCode && keybind.getModifiers() == eventModifiers;
	}

	private static String describe(KeyEvent keyEvent)
	{
		String modifiers = KeyEvent.getModifiersExText(keyEvent.getModifiersEx());
		String key = KeyEvent.getKeyText(keyEvent.getExtendedKeyCode());
		return modifiers.isEmpty() ? key : modifiers + "+" + key;
	}

	private String describeEvent(KeyEvent keyEvent)
	{
		return "[code=" + keyEvent.getKeyCode() + ", extended=" + keyEvent.getExtendedKeyCode()
			+ ", modifiers=" + keyEvent.getModifiersEx() + "]";
	}

	private String describeSavedBindings()
	{
		List<String> bindings = new ArrayList<>();
		for (CameraScenesViewpointSet viewpointSet : viewpointCatalog.listAllSets())
		{
			if (!viewpointSet.isEnabled())
			{
				continue;
			}
			addBinding(bindings, "G" + viewpointSet.getId() + " prev", viewpointSet.getPreviousKeybind());
			addBinding(bindings, "G" + viewpointSet.getId() + " next", viewpointSet.getNextKeybind());
			for (CameraScenesViewpoint viewpoint : viewpointCatalog.listViewpoints(viewpointSet))
			{
				if (viewpoint.isEnabled())
				{
					addBinding(bindings, "G" + viewpointSet.getId() + " V" + viewpoint.getId(), viewpoint.getKeybind());
				}
			}
		}
		return bindings.isEmpty() ? "none" : String.join(", ", bindings);
	}

	private static void addBinding(List<String> bindings, String label, Keybind keybind)
	{
		if (!CameraScenesViewpoint.isUnsetKeybind(keybind))
		{
			bindings.add(label + "=" + keybind + "(" + keybind.getKeyCode() + "/" + keybind.getModifiers() + ")");
		}
	}
}
