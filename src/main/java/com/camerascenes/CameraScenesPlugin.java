package com.camerascenes;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(name = "Camera Scenes")
public class CameraScenesPlugin extends Plugin
{
	private static final int SAVE_DEBOUNCE_MILLISECONDS = 300;
	private static final String ICON_RESOURCE = "/camera_scenes_icon.png";
	private static final String CAMERA_SMOOTHING_PLUGIN_CLASS = "com.camerasmoothing.CameraSmoothingPlugin";
	private static final String CAMERA_SMOOTHING_CONFIG_GROUP = "camerasmoothing";
	private static final String CAMERA_SMOOTHING_ROTATION_KEY = "smoothRotation";
	private static final String CAMERA_CONFIG_GROUP = "zoom";
	private static final String CAMERA_PITCH_LIMIT_KEY = "relaxCameraPitch";
	private static final String SHOW_DEBUG_TEXT_KEY = "showDebugTextInSidePanel";

	@Inject private ClientToolbar clientToolbar;
	@Inject private KeyManager keyManager;
	@Inject private CameraScenesViewpointCatalog viewpointCatalog;
	@Inject private CameraScenesCameraService cameraService;
	@Inject private CameraScenesProfileStore profileStore;
	@Inject private CameraScenesViewpointHistory viewpointHistory;
	@Inject private CameraScenesBackupService backupService;
	@Inject private CameraScenesShortcutRouter shortcutRouter;
	@Inject private CameraScenesShortcutDiagnostics shortcutDiagnostics;
	@Inject private PluginManager pluginManager;
	@Inject private ConfigManager configManager;
	@Inject private CameraScenesConfig config;

	private CameraScenesPanel panel;
	private NavigationButton navigationButton;
	private Timer pendingSaveTimer;

	@Override
	protected void startUp()
	{
		loadConfig();
		pendingSaveTimer = new Timer(SAVE_DEBOUNCE_MILLISECONDS, actionEvent -> saveConfigNow());
		pendingSaveTimer.setRepeats(false);
		panel = new CameraScenesPanel(this);
		keyManager.registerKeyListener(shortcutRouter);
		navigationButton = NavigationButton.builder()
			.tooltip("Camera Scenes")
			.icon(createIcon())
			.priority(5)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		cameraService.cancelPendingLoad();
		if (pendingSaveTimer != null)
		{
			pendingSaveTimer.stop();
			pendingSaveTimer = null;
			saveConfigNow();
		}
		keyManager.unregisterKeyListener(shortcutRouter);
		if (panel != null)
		{
			panel.shutDown();
		}
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		panel = null;
	}

	public CameraScenesViewpointCatalog getViewpointCatalog()
	{
		return viewpointCatalog;
	}

	CameraScenesViewpointHistory getViewpointHistory()
	{
		return viewpointHistory;
	}

	String getLastShortcutDiagnostic()
	{
		return shortcutDiagnostics.getLastEvent();
	}

	boolean showDebugTextInSidePanel()
	{
		return Boolean.parseBoolean(configManager.getConfiguration(CameraScenesConfig.GROUP, SHOW_DEBUG_TEXT_KEY));
	}

	public void addViewpoint(CameraScenesViewpointSet viewpointSet, Runnable refresh)
	{
		CameraScenesViewpoint viewpoint = viewpointCatalog.createViewpoint(viewpointSet);
		saveConfig();
		captureViewpoint(viewpoint, refresh);
	}

	public void captureViewpoint(CameraScenesViewpoint viewpoint, Runnable refresh)
	{
		cameraService.captureCurrent(snapshot ->
		{
			viewpointHistory.recordBeforeMutation(viewpoint);
			viewpoint.capture(snapshot.getYaw(), snapshot.getPitch(), snapshot.getZoom());
			saveConfig();
			if (refresh != null) refresh.run();
		});
	}

	public void loadViewpoint(CameraScenesViewpoint viewpoint)
	{
		viewpointCatalog.markSelected(viewpointCatalog.findSetForViewpoint(viewpoint), viewpoint);
		cameraService.apply(viewpoint);
	}

	void returnToAttachedCamera()
	{
		cameraService.returnToAttachedCamera();
	}

	boolean hasCameraSmoothingConflict()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (CAMERA_SMOOTHING_PLUGIN_CLASS.equals(plugin.getClass().getName())
				&& pluginManager.isPluginActive(plugin))
			{
				String configuredRotationSmoothing = configManager.getConfiguration(
					CAMERA_SMOOTHING_CONFIG_GROUP, CAMERA_SMOOTHING_ROTATION_KEY);
				return configuredRotationSmoothing == null || Boolean.parseBoolean(configuredRotationSmoothing);
			}
		}
		return false;
	}

	boolean hasExtendedPitchConflict()
	{
		if (Boolean.parseBoolean(configManager.getConfiguration(CAMERA_CONFIG_GROUP, CAMERA_PITCH_LIMIT_KEY)))
		{
			return false;
		}

		for (CameraScenesViewpointSet viewpointSet : viewpointCatalog.listAllSets())
		{
			for (CameraScenesViewpoint viewpoint : viewpointCatalog.listViewpoints(viewpointSet))
			{
				if (viewpoint != null && CameraScenesViewpoint.usesExtendedPitch(viewpoint.getPitch()))
				{
					return true;
				}
			}
		}
		return false;
	}

	void readCurrentCamera(Consumer<CameraScenesCameraState> stateConsumer)
	{
		cameraService.readCurrent(stateConsumer);
	}

	public void saveConfig()
	{
		if (pendingSaveTimer == null)
		{
			saveConfigNow();
			return;
		}
		pendingSaveTimer.restart();
	}

	void refreshCompatibilityWarnings()
	{
		if (panel != null)
		{
			panel.refreshConflictWarning();
		}
	}

	void exportFullBackup(File file) throws IOException
	{
		backupService.write(file, CameraScenesBackup.all(
			backupService.copyGroups(viewpointCatalog.listAllSets()),
			CameraScenesBackupSettings.from(config)));
	}

	void exportGroupBackup(File file, CameraScenesViewpointSet viewpointSet) throws IOException
	{
		List<CameraScenesViewpointSet> copies = backupService.copyGroups(java.util.Collections.singletonList(viewpointSet));
		backupService.write(file, CameraScenesBackup.group(copies.get(0)));
	}

	void importFullBackup(File file, java.awt.Component parent)
	{
		try
		{
			CameraScenesBackup backup = backupService.read(file);
			if (!CameraScenesBackup.SCOPE_ALL.equals(backup.getScope()))
			{
				throw new IOException("Choose a full-backup JSON file for this action.");
			}

			Object[] choices = {"Restore backup", "Merge groups", "Cancel"};
			int choice = javax.swing.JOptionPane.showOptionDialog(parent,
				"Restore replaces all Camera Scenes groups, viewpoints, and hotkeys.\n"
					+ "Merge keeps the current groups and adds the backup groups.\n"
					+ "Neither option changes other plugins.",
				"Import Camera Scenes backup", javax.swing.JOptionPane.DEFAULT_OPTION,
				javax.swing.JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
			if (choice == 0)
			{
				viewpointCatalog.replaceAll(backupService.copyGroups(backup.getGroups()));
				viewpointHistory.clearAll();
				applyBackupSettings(backup.getSettings());
				saveConfigNow();
				refreshPanel();
			}
			else if (choice == 1)
			{
				List<CameraScenesViewpointSet> previous = backupService.copyGroups(viewpointCatalog.listAllSets());
				List<CameraScenesViewpointSet> imported = backupService.copyGroups(backup.getGroups());
				viewpointCatalog.appendAll(imported);
				viewpointHistory.clearAll();
				List<String> conflicts = viewpointCatalog.listAllActiveKeybindConflicts();
				if (!conflicts.isEmpty() && !confirmImportedBindingConflicts(parent, conflicts))
				{
					viewpointCatalog.replaceAll(previous);
					return;
				}
				if (!conflicts.isEmpty()) viewpointCatalog.clearActiveKeybindConflictsFor(imported);
				saveConfigNow();
				refreshPanel();
			}
		}
		catch (IOException | RuntimeException ex)
		{
			showBackupError(parent, ex.getMessage());
		}
	}

	void importGroupBackup(File file, CameraScenesViewpointSet target, java.awt.Component parent)
	{
		try
		{
			CameraScenesBackup backup = backupService.read(file);
			if (!CameraScenesBackup.SCOPE_GROUP.equals(backup.getScope()))
			{
				throw new IOException("Choose a group JSON file for this action.");
			}
			int choice = javax.swing.JOptionPane.showConfirmDialog(parent,
				"Replace this group's viewpoints, notes, enabled states, and hotkeys?",
				"Import group into Camera Scenes", javax.swing.JOptionPane.OK_CANCEL_OPTION,
				javax.swing.JOptionPane.WARNING_MESSAGE);
			if (choice != javax.swing.JOptionPane.OK_OPTION) return;

			List<CameraScenesViewpointSet> previous = backupService.copyGroups(viewpointCatalog.listAllSets());
			CameraScenesViewpointSet imported = backupService.copyGroups(backup.getGroups()).get(0);
			target.setName(imported.getName());
			target.setEnabled(imported.isEnabled());
			target.setExpanded(imported.isExpanded());
			target.setPreviousKeybind(imported.getPreviousKeybind());
			target.setNextKeybind(imported.getNextKeybind());
			target.setViewpoints(imported.getViewpoints());
			viewpointCatalog.normalize();
			viewpointHistory.clearAll();
			List<String> conflicts = viewpointCatalog.listAllActiveKeybindConflicts();
			if (!conflicts.isEmpty() && !confirmImportedBindingConflicts(parent, conflicts))
			{
				viewpointCatalog.replaceAll(previous);
				return;
			}
			if (!conflicts.isEmpty()) viewpointCatalog.clearActiveKeybindConflictsFor(java.util.Collections.singletonList(target));
			saveConfigNow();
			refreshPanel();
		}
		catch (IOException | RuntimeException ex)
		{
			showBackupError(parent, ex.getMessage());
		}
	}

	private void applyBackupSettings(CameraScenesBackupSettings settings)
	{
		configManager.setConfiguration(CameraScenesConfig.GROUP, "smoothViewpointLoads", settings.isSmoothViewpointLoads());
		configManager.setConfiguration(CameraScenesConfig.GROUP, "viewpointLoadDuration", settings.getViewpointLoadDuration());
		configManager.setConfiguration(CameraScenesConfig.GROUP, "smoothZoomLoads", settings.isSmoothZoomLoads());
		configManager.setConfiguration(CameraScenesConfig.GROUP, "showDebugTextInSidePanel", settings.isShowDebugTextInSidePanel());
	}

	private static boolean confirmImportedBindingConflicts(java.awt.Component parent, List<String> conflicts)
	{
		String message = "Imported hotkeys conflict with active Camera Scenes bindings:\n\n- "
			+ String.join("\n- ", conflicts)
			+ "\n\nLet the imported bindings take precedence and clear the conflicting Camera Scenes bindings?";
		return javax.swing.JOptionPane.showConfirmDialog(parent, message, "Imported hotkey conflicts",
			javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE)
			== javax.swing.JOptionPane.YES_OPTION;
	}

	private static void showBackupError(java.awt.Component parent, String message)
	{
		javax.swing.JOptionPane.showMessageDialog(parent,
			message == null || message.trim().isEmpty() ? "Unable to read or write the Camera Scenes backup." : message,
			"Camera Scenes backup error", javax.swing.JOptionPane.ERROR_MESSAGE);
	}

	void refreshPanel()
	{
		if (panel != null) panel.reload();
	}

	@Subscribe
	public void onBeforeRender(net.runelite.api.events.BeforeRender event)
	{
		cameraService.onBeforeRender();
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (event.getPlugin() != null && CAMERA_SMOOTHING_PLUGIN_CLASS.equals(event.getPlugin().getClass().getName())
			&& panel != null)
		{
			panel.refreshConflictWarning();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Debug sidepanel text is retained in code but intentionally disabled.
		// if (CameraScenesConfig.GROUP.equals(event.getGroup())
		// 	&& SHOW_DEBUG_TEXT_KEY.equals(event.getKey())
		// 	&& panel != null)
		// {
		// 	panel.refreshDebugTextVisibility();
		// }
		if (CAMERA_SMOOTHING_CONFIG_GROUP.equals(event.getGroup())
			&& CAMERA_SMOOTHING_ROTATION_KEY.equals(event.getKey())
			&& panel != null)
		{
			panel.refreshConflictWarning();
		}
		if (CAMERA_CONFIG_GROUP.equals(event.getGroup())
			&& CAMERA_PITCH_LIMIT_KEY.equals(event.getKey())
			&& panel != null)
		{
			panel.refreshConflictWarning();
		}
	}

	@Subscribe
	public void onGameStateChanged(net.runelite.api.events.GameStateChanged event)
	{
		if (event.getGameState() != net.runelite.api.GameState.LOGGED_IN)
		{
			cameraService.cancelPendingLoad();
		}
	}

	private synchronized void saveConfigNow()
	{
		profileStore.write(viewpointCatalog.listAllSets());
	}

	private void loadConfig()
	{
		try
		{
			viewpointHistory.clearAll();
			viewpointCatalog.replaceAll(profileStore.read());
		}
		catch (RuntimeException ex)
		{
			log.warn("Unable to load Camera Scenes viewpoints; starting with an empty list", ex);
			viewpointHistory.clearAll();
			viewpointCatalog.replaceAll(java.util.Collections.emptyList());
		}
	}

	private static BufferedImage createIcon()
	{
		try (InputStream stream = CameraScenesPlugin.class.getResourceAsStream(ICON_RESOURCE))
		{
			if (stream != null)
			{
				BufferedImage resourceIcon = ImageIO.read(stream);
				if (resourceIcon != null)
				{
					return resourceIcon;
				}
			}
		}
		catch (IOException ex)
		{
			log.warn("Unable to load the Camera Scenes icon resource", ex);
		}

		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(new Color(35, 35, 35, 235));
			graphics.fillRoundRect(1, 5, 9, 7, 2, 2);
			graphics.setColor(new Color(185, 185, 185));
			graphics.fillOval(8, 6, 5, 5);
			graphics.setColor(new Color(35, 35, 35, 235));
			graphics.fillOval(9, 7, 3, 3);
			graphics.setColor(new Color(232, 157, 48));
			graphics.fillRect(3, 3, 3, 2);
			graphics.setColor(new Color(185, 185, 185));
			graphics.fillOval(12, 11, 2, 2);
			graphics.setColor(new Color(35, 35, 35, 235));
			graphics.fillRect(13, 12, 2, 2);
			graphics.setColor(new Color(210, 210, 210));
			graphics.fillOval(5, 12, 2, 2);
			graphics.fillRect(5, 13, 2, 3);
			graphics.drawLine(4, 15, 3, 16);
			graphics.drawLine(7, 15, 8, 16);
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	@Provides
	CameraScenesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CameraScenesConfig.class);
	}
}
