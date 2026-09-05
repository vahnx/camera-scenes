package com.camerascenes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

final class CameraScenesPanel extends PluginPanel
{
	private static final int HOTKEY_BUTTON_HEIGHT = 30;
	private static final float KEYBIND_MIN_FONT_SIZE = 8f;
	private static final String KEYBIND_RESIZE_LISTENER = "eyeSpy.keybindResizeListener";
	private static final String KEYBIND_HEIGHT_PROPERTY = "eyeSpy.keybindHeight";
	private static final String KEYBIND_BASE_FONT_PROPERTY = "eyeSpy.keybindBaseFont";
	private static final String KEYBIND_CAPTURE_TIMER_PROPERTY = "eyeSpy.keybindCaptureTimer";
	private static final Color KEYBIND_CAPTURE_GLOW = new Color(82, 82, 82);
	private static final Color CONFLICT_IMAGE_HIGHLIGHT = new Color(160, 70, 70);
	private static final int CONFLICT_IMAGE_PULSE_STEPS = 24;
	private static final int CONFLICT_IMAGE_PULSE_DELAY_MILLISECONDS = 115;
	private final CameraScenesPlugin plugin;
	private final JPanel viewpointSetsContainer = new ScrollableViewpointSetsPanel();
	private final List<CameraScenesViewpointSetPanel> renderedSetPanels = new ArrayList<>();
	private final JButton expandCollapseButton;
	private final JLabel cameraDebugLabel = new JLabel();
	private final JLabel cameraConflictWarningLabel = new JLabel();
	private final JLabel cameraConflictImageLabel = new JLabel();
	private final JPanel cameraConflictImageFrame = new JPanel(new BorderLayout());
	private final JLabel cameraPitchWarningLabel = new JLabel();
	private final JLabel cameraPitchImageLabel = new JLabel();
	private final JPanel cameraPitchImageFrame = new JPanel(new BorderLayout());
	private final Timer cameraDebugTimer;
	private final Timer cameraConflictHighlightTimer;
	private final Runnable currentSelectionListener;
	private int conflictImagePulsePhase;

	CameraScenesPanel(CameraScenesPlugin plugin)
	{
		this.plugin = plugin;
		currentSelectionListener = this::refreshCurrentViewpoint;
		plugin.getViewpointCatalog().addSelectionListener(currentSelectionListener);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel(new BorderLayout(0, 6));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(6, 6, 8, 6));
		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setOpaque(false);
		JButton manageButton = button("⋮", ColorScheme.MEDIUM_GRAY_COLOR, this::showBackupMenu);
		manageButton.setPreferredSize(new Dimension(24, 24));
		manageButton.setMinimumSize(new Dimension(24, 24));
		manageButton.setMaximumSize(new Dimension(24, 24));
		manageButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
		manageButton.setMargin(new Insets(0, 0, 0, 0));
		manageButton.setToolTipText("Import or export Camera Scenes backups");

		JPanel actions = new JPanel(new GridBagLayout());
		actions.setOpaque(false);
		actions.setMinimumSize(new Dimension(0, 24));
		actions.setPreferredSize(new Dimension(0, 24));
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JButton createGroupButton = button("Create Group", new Color(0x2F6F3E), () -> {
			plugin.getViewpointCatalog().createSet();
			plugin.saveConfig();
			reload();
		});
		createGroupButton.setMargin(new Insets(0, 2, 0, 2));
		GridBagConstraints expandingButtonConstraints = new GridBagConstraints();
		expandingButtonConstraints.gridy = 0;
		expandingButtonConstraints.weightx = 1;
		expandingButtonConstraints.fill = GridBagConstraints.BOTH;
		expandingButtonConstraints.insets = new Insets(0, 0, 0, 4);
		actions.add(createGroupButton, expandingButtonConstraints);
		expandCollapseButton = button("Expand All", ColorScheme.MEDIUM_GRAY_COLOR, this::toggleAllExpanded);
		expandCollapseButton.setMargin(new Insets(0, 2, 0, 2));
		GridBagConstraints expandButtonConstraints = new GridBagConstraints();
		expandButtonConstraints.gridy = 0;
		expandButtonConstraints.weightx = 1;
		expandButtonConstraints.fill = GridBagConstraints.BOTH;
		expandButtonConstraints.insets = new Insets(0, 0, 0, 4);
		actions.add(expandCollapseButton, expandButtonConstraints);
		GridBagConstraints manageButtonConstraints = new GridBagConstraints();
		manageButtonConstraints.gridy = 0;
		manageButtonConstraints.weightx = 0;
		manageButtonConstraints.fill = GridBagConstraints.BOTH;
		actions.add(manageButton, manageButtonConstraints);
		header.add(actions, BorderLayout.CENTER);

		JPanel status = new JPanel();
		status.setOpaque(false);
		status.setLayout(new BoxLayout(status, BoxLayout.Y_AXIS));
		/*
		 * Camera Smoothing warning retained for development, but hidden because
		 * Camera Scenes no longer performs smooth viewpoint loads.
		 *
		 * cameraConflictWarningLabel.setForeground(ColorScheme.BRAND_ORANGE);
		 * cameraConflictWarningLabel.setFont(FontManager.getRunescapeSmallFont());
		 * cameraConflictWarningLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		 * cameraConflictWarningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		 * cameraConflictWarningLabel.setVisible(false);
		 * status.add(cameraConflictWarningLabel);
		 * cameraConflictImageLabel.setIcon(loadPanelImage("/camera_smoothing_settings.png"));
		 * cameraConflictImageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		 * cameraConflictImageLabel.setHorizontalAlignment(JLabel.CENTER);
		 * cameraConflictImageFrame.setOpaque(true);
		 * cameraConflictImageFrame.setBackground(Color.WHITE);
		 * cameraConflictImageFrame.setBorder(BorderFactory.createCompoundBorder(
		 * 	BorderFactory.createLineBorder(Color.WHITE, 2),
		 * 	BorderFactory.createEmptyBorder(2, 2, 2, 2)));
		 * cameraConflictImageFrame.setAlignmentX(Component.LEFT_ALIGNMENT);
		 * cameraConflictImageFrame.setMaximumSize(new Dimension(180, 200));
		 * cameraConflictImageFrame.add(cameraConflictImageLabel, BorderLayout.CENTER);
		 * cameraConflictImageFrame.setVisible(false);
		 * status.add(cameraConflictImageFrame);
		 */
		cameraPitchWarningLabel.setForeground(ColorScheme.BRAND_ORANGE);
		cameraPitchWarningLabel.setFont(FontManager.getRunescapeSmallFont());
		cameraPitchWarningLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		cameraPitchWarningLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		cameraPitchWarningLabel.setVisible(false);
		status.add(cameraPitchWarningLabel);
		cameraPitchImageLabel.setIcon(loadPanelImage("/camera-scenes-settings.png"));
		cameraPitchImageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		cameraPitchImageLabel.setHorizontalAlignment(JLabel.CENTER);
		cameraPitchImageFrame.setOpaque(true);
		cameraPitchImageFrame.setBackground(Color.WHITE);
		cameraPitchImageFrame.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Color.WHITE, 2),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)));
		cameraPitchImageFrame.setAlignmentX(Component.LEFT_ALIGNMENT);
		cameraPitchImageFrame.setMaximumSize(new Dimension(180, 200));
		cameraPitchImageFrame.add(cameraPitchImageLabel, BorderLayout.CENTER);
		cameraPitchImageFrame.setVisible(false);
		status.add(cameraPitchImageFrame);
		cameraDebugLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cameraDebugLabel.setFont(FontManager.getRunescapeSmallFont());
		cameraDebugLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		cameraDebugLabel.setVisible(false);
		// Debug display retained for development, but intentionally hidden from
		// the sidepanel until it is explicitly re-enabled in a future debug build.
		// status.add(cameraDebugLabel);
		header.add(status, BorderLayout.SOUTH);
		content.add(header, BorderLayout.NORTH);

		viewpointSetsContainer.setLayout(new BoxLayout(viewpointSetsContainer, BoxLayout.Y_AXIS));
		viewpointSetsContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		viewpointSetsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
		JScrollPane scrollPane = new JScrollPane(viewpointSetsContainer);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.add(scrollPane, BorderLayout.CENTER);
		add(content, BorderLayout.CENTER);
		cameraConflictHighlightTimer = new Timer(CONFLICT_IMAGE_PULSE_DELAY_MILLISECONDS, actionEvent -> {
			conflictImagePulsePhase = (conflictImagePulsePhase + 1) % CONFLICT_IMAGE_PULSE_STEPS;
			updateConflictImageBorder();
		});
		cameraConflictHighlightTimer.setCoalesce(true);
		reload();
		cameraDebugTimer = new Timer(100, actionEvent -> requestCameraDebugUpdate());
		cameraDebugTimer.setCoalesce(true);
		// refreshDebugTextVisibility();
	}

	void shutDown()
	{
		plugin.getViewpointCatalog().removeSelectionListener(currentSelectionListener);
		cameraDebugTimer.stop();
		cameraConflictHighlightTimer.stop();
	}

	private void requestCameraDebugUpdate()
	{
		if (!plugin.showDebugTextInSidePanel())
		{
			return;
		}
		plugin.readCurrentCamera(state -> {
			Runnable update = () -> {
			if (state == null)
			{
				cameraDebugLabel.setText("Camera: not logged in");
			}
			else
			{
				cameraDebugLabel.setText(String.format("<html><div style='width:180px'>Yaw %d | Pitch %d | Zoom %d<br>%s</div></html>",
					state.getCurrentYaw(), state.getCurrentPitch(), state.getZoom(), state.getDiagnostics()));
			}
			};
			if (javax.swing.SwingUtilities.isEventDispatchThread())
			{
				update.run();
			}
			else
			{
				javax.swing.SwingUtilities.invokeLater(update);
			}
		});
	}

	void refreshDebugTextVisibility()
	{
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::refreshDebugTextVisibility);
			return;
		}

		boolean visible = plugin.showDebugTextInSidePanel();
		cameraDebugLabel.setVisible(visible);
		if (visible)
		{
			cameraDebugTimer.start();
			requestCameraDebugUpdate();
		}
		else
		{
			cameraDebugTimer.stop();
			cameraDebugLabel.setText("");
		}
		cameraDebugLabel.getParent().revalidate();
		cameraDebugLabel.getParent().repaint();
	}

	private static String escapeHtml(String value)
	{
		return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	void reload()
	{
		viewpointSetsContainer.removeAll();
		renderedSetPanels.clear();
		List<CameraScenesViewpointSet> viewpointSets = plugin.getViewpointCatalog().listAllSets();
		if (viewpointSets.isEmpty())
		{
			JLabel empty = new JLabel("Add a group to save your first viewpoint.");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
			viewpointSetsContainer.add(empty);
		}
		else
		{
			for (CameraScenesViewpointSet viewpointSet : viewpointSets)
			{
				CameraScenesViewpointSetPanel setPanel = new CameraScenesViewpointSetPanel(plugin, viewpointSet, this::reload);
				setPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
				renderedSetPanels.add(setPanel);
				viewpointSetsContainer.add(setPanel);
				viewpointSetsContainer.add(Box.createVerticalStrut(10));
			}
		}
		viewpointSetsContainer.revalidate();
		viewpointSetsContainer.repaint();
		updateExpandCollapseButton();
		refreshConflictWarning();
	}

	private void refreshCurrentViewpoint()
	{
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::refreshCurrentViewpoint);
			return;
		}
		for (CameraScenesViewpointSetPanel setPanel : renderedSetPanels) setPanel.refreshCurrentViewpoint();
	}

	private void setAllExpanded(boolean expanded)
	{
		boolean layoutChanged = false;
		for (CameraScenesViewpointSetPanel setPanel : renderedSetPanels)
		{
			layoutChanged |= setPanel.setExpanded(expanded, false, false);
		}
		plugin.saveConfig();
		updateExpandCollapseButton();
		if (layoutChanged)
		{
			viewpointSetsContainer.revalidate();
			viewpointSetsContainer.repaint();
		}
	}

	private void toggleAllExpanded()
	{
		boolean shouldExpand = plugin.getViewpointCatalog().listAllSets().stream()
			.anyMatch(viewpointSet -> !viewpointSet.isExpanded());
		setAllExpanded(shouldExpand);
	}

	private void updateExpandCollapseButton()
	{
		List<CameraScenesViewpointSet> viewpointSets = plugin.getViewpointCatalog().listAllSets();
		boolean allExpanded = !viewpointSets.isEmpty() && viewpointSets.stream().allMatch(CameraScenesViewpointSet::isExpanded);
		expandCollapseButton.setText(allExpanded ? "Collapse All" : "Expand All");
		expandCollapseButton.setToolTipText(allExpanded ? "Collapse every group" : "Expand every group");
	}

	void refreshConflictWarning()
	{
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::refreshConflictWarning);
			return;
		}

		/*
		 * Camera Smoothing warning retained for development, but disabled because
		 * viewpoint loads are now immediate.
		 * boolean smoothingConflict = plugin.hasCameraSmoothingConflict();
		 * cameraConflictWarningLabel.setText(...);
		 * cameraConflictWarningLabel.setToolTipText(...);
		 * cameraConflictWarningLabel.setVisible(smoothingConflict);
		 * boolean smoothingImageVisible = smoothingConflict && cameraConflictImageLabel.getIcon() != null;
		 * cameraConflictImageFrame.setVisible(smoothingImageVisible);
		 */
		boolean smoothingImageVisible = false;
		boolean pitchConflict = plugin.hasExtendedPitchConflict();
		cameraPitchWarningLabel.setText(pitchConflict
			? "<html><div style='width:180px'>Extended pitch may not load correctly<br>Turn on <b>Expand pitch limit</b> in <b>Camera</b><br>See below image</div></html>"
			: "");
		cameraPitchWarningLabel.setToolTipText(pitchConflict
			? "A saved viewpoint uses pitch outside RuneLite's default camera range."
			: null);
		cameraPitchWarningLabel.setVisible(pitchConflict);
		boolean pitchImageVisible = pitchConflict && cameraPitchImageLabel.getIcon() != null;
		cameraPitchImageFrame.setVisible(pitchImageVisible);
		boolean anyImageVisible = smoothingImageVisible || pitchImageVisible;
		if (anyImageVisible)
		{
			if (!cameraConflictHighlightTimer.isRunning())
			{
				conflictImagePulsePhase = 0;
				updateConflictImageBorder();
				cameraConflictHighlightTimer.start();
			}
		}
		else
		{
			cameraConflictHighlightTimer.stop();
			conflictImagePulsePhase = 0;
			updateConflictImageBorder();
		}
		cameraConflictWarningLabel.revalidate();
		cameraConflictWarningLabel.repaint();
		cameraConflictImageFrame.revalidate();
		cameraConflictImageFrame.repaint();
		cameraPitchWarningLabel.revalidate();
		cameraPitchWarningLabel.repaint();
		cameraPitchImageFrame.revalidate();
		cameraPitchImageFrame.repaint();
	}

	private void updateConflictImageBorder()
	{
		double intensity = (1.0 - Math.cos((conflictImagePulsePhase / (double) CONFLICT_IMAGE_PULSE_STEPS)
			* Math.PI * 2.0)) / 2.0;
		Color borderColor = blend(Color.WHITE, CONFLICT_IMAGE_HIGHLIGHT, intensity);
		cameraConflictImageFrame.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor, 2),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)));
		cameraPitchImageFrame.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(borderColor, 2),
			BorderFactory.createEmptyBorder(2, 2, 2, 2)));
		cameraConflictImageFrame.repaint();
		cameraPitchImageFrame.repaint();
	}

	private static ImageIcon loadPanelImage(String resourcePath)
	{
		try (InputStream stream = CameraScenesPanel.class.getResourceAsStream(resourcePath))
		{
			if (stream == null)
			{
				return null;
			}
			BufferedImage source = ImageIO.read(stream);
			if (source == null)
			{
				return null;
			}
			int maximumWidth = 170;
			if (source.getWidth() <= maximumWidth)
			{
				return new ImageIcon(source);
			}

			int scaledHeight = (int) Math.round(source.getHeight() * (maximumWidth / (double) source.getWidth()));
			return new ImageIcon(source.getScaledInstance(maximumWidth, scaledHeight, Image.SCALE_SMOOTH));
		}
		catch (IOException | RuntimeException ex)
		{
			return null;
		}
	}

	private void showBackupMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem exportItem = new JMenuItem("Export Full Backup");
		exportItem.addActionListener(actionEvent -> exportFullBackup());
		menu.add(exportItem);
		JMenuItem importItem = new JMenuItem("Import Full Backup");
		importItem.addActionListener(actionEvent -> importFullBackup());
		menu.add(importItem);
		menu.addSeparator();
		JMenuItem attachedCameraItem = new JMenuItem("Return to attached camera");
		attachedCameraItem.addActionListener(actionEvent -> plugin.returnToAttachedCamera());
		menu.add(attachedCameraItem);
		menu.show(this, Math.max(0, getWidth() - 44), 24);
	}

	private void exportFullBackup()
	{
		File file = chooseSaveFile("Export Camera Scenes backup", "camera-scenes-backup.json");
		if (file == null) return;
		try
		{
			plugin.exportFullBackup(file);
		}
		catch (IOException | RuntimeException ex)
		{
			JOptionPane.showMessageDialog(this, ex.getMessage(), "Camera Scenes backup error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importFullBackup()
	{
		File file = chooseOpenFile("Import Camera Scenes backup");
		if (file != null) plugin.importFullBackup(file, this);
	}

	static void showGroupBackupMenu(Component invoker, CameraScenesPlugin plugin,
		CameraScenesViewpointSet viewpointSet)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem exportItem = new JMenuItem("Export Group");
		exportItem.addActionListener(actionEvent -> {
			File file = chooseSaveFile(invoker, "Export Camera Scenes group", "camera-scenes-group.json");
			if (file == null) return;
			try
			{
				plugin.exportGroupBackup(file, viewpointSet);
			}
			catch (IOException | RuntimeException ex)
			{
				JOptionPane.showMessageDialog(invoker, ex.getMessage(), "Camera Scenes backup error", JOptionPane.ERROR_MESSAGE);
			}
		});
		menu.add(exportItem);
		JMenuItem importItem = new JMenuItem("Import into Group");
		importItem.addActionListener(actionEvent -> {
			File file = chooseOpenFile(invoker, "Import Camera Scenes group");
			if (file != null) plugin.importGroupBackup(file, viewpointSet, invoker);
		});
		menu.add(importItem);
		menu.show(invoker, 0, invoker.getHeight());
	}

	private File chooseSaveFile(String title, String defaultName)
	{
		return chooseSaveFile(this, title, defaultName);
	}

	private static File chooseSaveFile(Component parent, String title, String defaultName)
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setSelectedFile(new File(defaultName));
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
		if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
		File selected = chooser.getSelectedFile();
		return selected.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")
			? selected : new File(selected.getParentFile(), selected.getName() + ".json");
	}

	private File chooseOpenFile(String title)
	{
		return chooseOpenFile(this, title);
	}

	private static File chooseOpenFile(Component parent, String title)
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(title);
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));
		return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
	}

	static JButton button(String text, Color background, Runnable action)
	{
		JButton button = new JButton(text);
		button.setForeground(Color.WHITE);
		button.setBackground(background);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		button.setPreferredSize(new Dimension(Math.max(70, text.length() * 7 + 20), 24));
		button.addActionListener(actionEvent -> action.run());
		return button;
	}

	static boolean confirmKeybindReassignment(Component parent, List<String> conflicts)
	{
		String message = "That hotkey is already assigned to:\n\n- " + String.join("\n- ", conflicts)
			+ "\n\nReassign it? The existing binding(s) will be cleared.";
		return javax.swing.JOptionPane.showConfirmDialog(parent, message, "Hotkey already in use",
			javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE)
			== javax.swing.JOptionPane.YES_OPTION;
	}

	static JButton orderButton(String symbol, String tooltip, Runnable action)
	{
		JButton button = new JButton();
		button.setForeground(Color.WHITE);
		button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		button.setFocusPainted(false);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setIcon(new CenteredGlyphIcon(symbol));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		button.setPreferredSize(new Dimension(24, 24));
		button.setToolTipText(tooltip);
		button.addActionListener(actionEvent -> action.run());
		return button;
	}

	private static final class CenteredGlyphIcon implements Icon
	{
		private static final int ICON_SIZE = 20;
		private final String glyph;
		private final Font font = FontManager.getRunescapeSmallFont().deriveFont(14f);

		private CenteredGlyphIcon(String glyph)
		{
			this.glyph = glyph;
		}

		@Override
		public int getIconWidth()
		{
			return ICON_SIZE;
		}

		@Override
		public int getIconHeight()
		{
			return ICON_SIZE;
		}

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D glyphGraphics = (Graphics2D) graphics.create();
			try
			{
				glyphGraphics.setFont(font);
				glyphGraphics.setColor(component.isEnabled() ? component.getForeground() : ColorScheme.LIGHT_GRAY_COLOR);
				FontMetrics metrics = glyphGraphics.getFontMetrics();
				int glyphWidth = metrics.stringWidth(glyph);
				int baseline = y + (ICON_SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
				glyphGraphics.drawString(glyph, x + (ICON_SIZE - glyphWidth) / 2, baseline);
			}
			finally
			{
				glyphGraphics.dispose();
			}
		}
	}

	static void setKeybindButtonState(JButton button, String actionLabel, Keybind keybind, boolean capturing)
	{
		if (button.getClientProperty(KEYBIND_BASE_FONT_PROPERTY) == null)
		{
			Font baseFont = button.getFont();
			button.putClientProperty(KEYBIND_BASE_FONT_PROPERTY,
				baseFont == null ? FontManager.getRunescapeSmallFont() : baseFont);
		}
		String displayText = capturing ? "Press key..." : formatKeybind(keybind);
		button.setText(displayText);
		button.setMargin(new Insets(0, 6, 0, 6));
		Object configuredHeight = button.getClientProperty(KEYBIND_HEIGHT_PROPERTY);
		int height = configuredHeight instanceof Integer ? (Integer) configuredHeight : HOTKEY_BUTTON_HEIGHT;
		button.setPreferredSize(new Dimension(70, height));
		if (button.getClientProperty(KEYBIND_RESIZE_LISTENER) == null)
		{
			button.putClientProperty(KEYBIND_RESIZE_LISTENER, Boolean.TRUE);
			button.addComponentListener(new ComponentAdapter()
			{
				@Override
				public void componentResized(ComponentEvent event)
				{
					fitKeybindButtonText((JButton) event.getComponent());
				}
			});
		}
		fitKeybindButtonText(button);
		button.setToolTipText(capturing
			? "<html><b>" + escapeHtml(actionLabel) + "</b><br>Press the desired key. Escape cancels; right-click clears.</html>"
			: "<html><b>" + escapeHtml(actionLabel) + ": " + escapeHtml(formatKeybind(keybind)) + "</b><br>Click to assign. Escape cancels; right-click clears.</html>");
	}

	private static void fitKeybindButtonText(JButton button)
	{
		String displayText = button.getText();
		Object configuredBaseFont = button.getClientProperty(KEYBIND_BASE_FONT_PROPERTY);
		Font baseFont = configuredBaseFont instanceof Font ? (Font) configuredBaseFont : button.getFont();
		if (baseFont == null)
		{
			baseFont = FontManager.getRunescapeSmallFont();
		}
		float fontSize = baseFont.getSize2D();
		Insets insets = button.getInsets();
		Insets margin = button.getMargin();
		int availableWidth = button.getWidth() - insets.left - insets.right - margin.left - margin.right - 4;
		if (availableWidth > 0)
		{
			while (fontSize > KEYBIND_MIN_FONT_SIZE
				&& button.getFontMetrics(baseFont.deriveFont(fontSize)).stringWidth(displayText) > availableWidth)
			{
				fontSize -= 0.5f;
			}
		}
		button.setFont(baseFont.deriveFont(fontSize));
	}

	static String formatKeybind(Keybind keybind)
	{
		return keybind == null ? Keybind.NOT_SET.toString() : keybind.toString();
	}

	static void startKeybindCaptureAnimation(JButton button)
	{
		stopKeybindCaptureAnimation(button);
		final int[] phase = {0};
		Timer pulse = new Timer(115, actionEvent -> {
			phase[0] = (phase[0] + 1) % 24;
			double intensity = (1.0 - Math.cos((phase[0] / 24.0) * Math.PI * 2.0)) / 2.0;
			button.setBackground(blend(Color.BLACK, KEYBIND_CAPTURE_GLOW, intensity));
		});
		button.putClientProperty(KEYBIND_CAPTURE_TIMER_PROPERTY, pulse);
		button.setBackground(Color.BLACK);
		pulse.start();
	}

	static void stopKeybindCaptureAnimation(JButton button)
	{
		Object timer = button.getClientProperty(KEYBIND_CAPTURE_TIMER_PROPERTY);
		if (timer instanceof Timer)
		{
			((Timer) timer).stop();
		}
		button.putClientProperty(KEYBIND_CAPTURE_TIMER_PROPERTY, null);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
	}

	private static Color blend(Color start, Color end, double intensity)
	{
		int red = (int) Math.round(start.getRed() + (end.getRed() - start.getRed()) * intensity);
		int green = (int) Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * intensity);
		int blue = (int) Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * intensity);
		return new Color(red, green, blue);
	}

	private static final class ScrollableViewpointSetsPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	static void setKeybindButtonHeight(JButton button, int height)
	{
		button.putClientProperty(KEYBIND_HEIGHT_PROPERTY, height);
		button.setPreferredSize(new Dimension(70, height));
	}
}
