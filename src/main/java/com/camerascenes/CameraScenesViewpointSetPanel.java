package com.camerascenes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;

final class CameraScenesViewpointSetPanel extends JPanel
{
	private static final Color GROUP_HEADER_BACKGROUND = new Color(58, 58, 58);
	private static final Color GROUP_ACCENT = new Color(185, 125, 45);
	private static final Color DISABLED_GROUP_BACKGROUND = new Color(45, 45, 45);
	private static final Color DISABLED_GROUP_HEADER_BACKGROUND = new Color(50, 50, 50);
	private static final Color DISABLED_GROUP_ACCENT = new Color(105, 105, 105);
	private static final int GROUP_HEADER_CONTROL_HEIGHT = 24;
	private static final int GROUP_NAME_FIELD_WIDTH = 160;
	private final CameraScenesPlugin plugin;
	private final CameraScenesViewpointSet viewpointSet;
	private final Runnable reloadPanel;
	private final JPanel setContentArea = new JPanel(new BorderLayout(0, 4));
	private final JPanel setActionArea = new JPanel();
	private final JPanel expandedControls = new JPanel();
	private final JPanel cycleActions = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JPanel compactCycleActions = new JPanel(new GridLayout(1, 2, 2, 0));
	private final JPanel headerRightControls = new JPanel(new BorderLayout(0, 0));
	private final JPanel viewpointEntriesContainer = new JPanel();
	private final java.util.List<CameraScenesViewpointPanel> viewpointPanels = new java.util.ArrayList<>();
	private final JButton expandButton = new JButton();
	private JButton previousViewpointButton;
	private JButton nextViewpointButton;
	private JButton compactPreviousViewpointButton;
	private JButton compactNextViewpointButton;

	CameraScenesViewpointSetPanel(CameraScenesPlugin plugin, CameraScenesViewpointSet viewpointSet, Runnable reloadPanel)
	{
		this.plugin = plugin;
		this.viewpointSet = viewpointSet;
		this.reloadPanel = reloadPanel;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GROUP_ACCENT),
			BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 2, 5, 2))));

		JPanel header = new JPanel(new BorderLayout(4, 4));
		header.setOpaque(true);
		header.setBackground(GROUP_HEADER_BACKGROUND);
		header.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		refreshEnabledAppearance(header);
		expandButton.setPreferredSize(new Dimension(GROUP_HEADER_CONTROL_HEIGHT, GROUP_HEADER_CONTROL_HEIGHT));
		expandButton.setMinimumSize(new Dimension(GROUP_HEADER_CONTROL_HEIGHT, GROUP_HEADER_CONTROL_HEIGHT));
		expandButton.setMaximumSize(new Dimension(GROUP_HEADER_CONTROL_HEIGHT, GROUP_HEADER_CONTROL_HEIGHT));
		expandButton.setFocusPainted(false);
		expandButton.addActionListener(actionEvent -> setExpanded(!viewpointSet.isExpanded(), true));
		header.add(expandButton, BorderLayout.WEST);

		JCheckBox enabledToggle = new JCheckBox();
		enabledToggle.setSelected(viewpointSet.isEnabled());
		enabledToggle.setOpaque(false);
		enabledToggle.setToolTipText("Enable or disable this group");
		enabledToggle.addActionListener(actionEvent -> {
			if (enabledToggle.isSelected())
			{
				java.util.List<String> conflicts = plugin.getViewpointCatalog().listGroupActivationConflicts(viewpointSet);
				if (!conflicts.isEmpty())
				{
					enabledToggle.setSelected(false);
					if (CameraScenesPanel.confirmKeybindReassignment(this, conflicts))
					{
						viewpointSet.setEnabled(true);
						plugin.getViewpointCatalog().clearGroupActivationConflicts(viewpointSet);
						plugin.saveConfig();
						refreshEnabledAppearance(header);
						plugin.refreshPanel();
					}
					return;
				}
			}
			viewpointSet.setEnabled(enabledToggle.isSelected());
			plugin.saveConfig();
			refreshEnabledAppearance(header);
			refreshCycleButtons();
		});
		JPanel groupOrderControls = new JPanel(new GridLayout(1, 3, 2, 0));
		groupOrderControls.setOpaque(false);
		groupOrderControls.add(enabledToggle);
		JButton moveGroupUpButton = CameraScenesPanel.orderButton("↑", "Move group up", () -> {
			if (plugin.getViewpointCatalog().shiftSetOrder(viewpointSet, -1))
			{
				plugin.saveConfig();
				reloadPanel.run();
			}
		});
		moveGroupUpButton.setEnabled(plugin.getViewpointCatalog().canShiftSetOrder(viewpointSet, -1));
		JButton moveGroupDownButton = CameraScenesPanel.orderButton("↓", "Move group down", () -> {
			if (plugin.getViewpointCatalog().shiftSetOrder(viewpointSet, 1))
			{
				plugin.saveConfig();
				reloadPanel.run();
			}
		});
		moveGroupDownButton.setEnabled(plugin.getViewpointCatalog().canShiftSetOrder(viewpointSet, 1));
		groupOrderControls.add(moveGroupUpButton);
		groupOrderControls.add(moveGroupDownButton);
		groupOrderControls.setPreferredSize(new Dimension(3 * GROUP_HEADER_CONTROL_HEIGHT + 4, GROUP_HEADER_CONTROL_HEIGHT));
		groupOrderControls.setMinimumSize(new Dimension(3 * GROUP_HEADER_CONTROL_HEIGHT + 4, GROUP_HEADER_CONTROL_HEIGHT));
		groupOrderControls.setMaximumSize(new Dimension(3 * GROUP_HEADER_CONTROL_HEIGHT + 4, GROUP_HEADER_CONTROL_HEIGHT));
		headerRightControls.setOpaque(false);
		headerRightControls.add(groupOrderControls, BorderLayout.EAST);
		header.add(headerRightControls, BorderLayout.EAST);

		JTextField setNameField = new JTextField(viewpointSet.getName() == null ? "" : viewpointSet.getName());
		setNameField.setPreferredSize(new Dimension(GROUP_NAME_FIELD_WIDTH, GROUP_HEADER_CONTROL_HEIGHT));
		setNameField.setMinimumSize(new Dimension(90, GROUP_HEADER_CONTROL_HEIGHT));
		setNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, GROUP_HEADER_CONTROL_HEIGHT));
		setNameField.setForeground(Color.WHITE);
		setNameField.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setNameField.setCaretColor(Color.WHITE);
		setNameField.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		setNameField.setToolTipText(groupNameTooltip());
		setNameField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent documentEvent) { update(); }
			@Override public void removeUpdate(DocumentEvent documentEvent) { update(); }
			@Override public void changedUpdate(DocumentEvent documentEvent) { update(); }
			private void update()
			{
				viewpointSet.setName(setNameField.getText());
				setNameField.setToolTipText(groupNameTooltip());
				plugin.saveConfig();
			}
		});
		header.add(setNameField, BorderLayout.CENTER);
		add(header, BorderLayout.NORTH);

		setActionArea.setLayout(new BoxLayout(setActionArea, BoxLayout.Y_AXIS));
		setActionArea.setOpaque(false);
		cycleActions.setOpaque(false);
		cycleActions.setAlignmentX(LEFT_ALIGNMENT);
		cycleActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		previousViewpointButton = CameraScenesPanel.button("Previous", ColorScheme.MEDIUM_GRAY_COLOR,
			this::loadPreviousViewpoint);
		cycleActions.add(previousViewpointButton);
		nextViewpointButton = CameraScenesPanel.button("Next", ColorScheme.MEDIUM_GRAY_COLOR,
			this::loadNextViewpoint);
		cycleActions.add(nextViewpointButton);
		compactCycleActions.setOpaque(false);
		compactCycleActions.setPreferredSize(new Dimension(46, 24));
		compactCycleActions.setMinimumSize(new Dimension(46, 24));
		compactCycleActions.setMaximumSize(new Dimension(46, 24));
		compactPreviousViewpointButton = CameraScenesPanel.orderButton("←", "Load the previous enabled viewpoint in this group",
			this::loadPreviousViewpoint);
		compactPreviousViewpointButton.setPreferredSize(new Dimension(22, 24));
		compactNextViewpointButton = CameraScenesPanel.orderButton("→", "Load the next enabled viewpoint in this group",
			this::loadNextViewpoint);
		compactNextViewpointButton.setPreferredSize(new Dimension(22, 24));
		compactCycleActions.add(compactPreviousViewpointButton);
		compactCycleActions.add(compactNextViewpointButton);

		expandedControls.setLayout(new BoxLayout(expandedControls, BoxLayout.Y_AXIS));
		expandedControls.setOpaque(false);
		expandedControls.setAlignmentX(LEFT_ALIGNMENT);
		JPanel shortcutGrid = new JPanel(new GridLayout(1, 2, 4, 4));
		shortcutGrid.setOpaque(false);
		shortcutGrid.setAlignmentX(LEFT_ALIGNMENT);
		shortcutGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		shortcutGrid.add(keybindButton("Previous hotkey", viewpointSet.getPreviousKeybind(),
			keybind -> viewpointSet.setPreviousKeybind(keybind),
			keybind -> plugin.getViewpointCatalog().listActiveKeybindConflicts(keybind, viewpointSet, null, false), false));
		shortcutGrid.add(keybindButton("Next hotkey", viewpointSet.getNextKeybind(),
			keybind -> viewpointSet.setNextKeybind(keybind),
			keybind -> plugin.getViewpointCatalog().listActiveKeybindConflicts(keybind, viewpointSet, null, true), true));
		expandedControls.add(Box.createVerticalStrut(4));
		expandedControls.add(shortcutGrid);
		expandedControls.add(Box.createVerticalStrut(4));
		JPanel setActions = new JPanel(new GridLayout(1, 2, 4, 0));
		setActions.setOpaque(false);
		setActions.setAlignmentX(LEFT_ALIGNMENT);
		setActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		setActions.add(CameraScenesPanel.button("Add Viewpoint", new Color(0x3D8B40), () -> {
			plugin.addViewpoint(viewpointSet, reloadPanel);
			reloadPanel.run();
		}));
		setActions.add(CameraScenesPanel.button("Delete All", new Color(0xB94A4A), () -> {
			int result = javax.swing.JOptionPane.showConfirmDialog(this, "Delete this group and its viewpoints?",
				"Confirm delete", javax.swing.JOptionPane.OK_CANCEL_OPTION);
			if (result == javax.swing.JOptionPane.OK_OPTION)
			{
				plugin.getViewpointCatalog().deleteSet(viewpointSet);
				plugin.saveConfig();
				reloadPanel.run();
			}
		}));
		expandedControls.add(setActions);
		setContentArea.setOpaque(false);
		setContentArea.add(setActionArea, BorderLayout.NORTH);

		viewpointEntriesContainer.setLayout(new BoxLayout(viewpointEntriesContainer, BoxLayout.Y_AXIS));
		viewpointEntriesContainer.setOpaque(false);
		viewpointEntriesContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		for (CameraScenesViewpoint savedViewpoint : plugin.getViewpointCatalog().listViewpoints(viewpointSet))
		{
			CameraScenesViewpointPanel viewpointPanel = new CameraScenesViewpointPanel(plugin, viewpointSet, savedViewpoint,
				reloadPanel, this::refreshCycleButtons);
			viewpointPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			viewpointPanels.add(viewpointPanel);
			viewpointEntriesContainer.add(viewpointPanel);
			viewpointEntriesContainer.add(Box.createVerticalStrut(5));
		}
		add(setContentArea, BorderLayout.CENTER);
		refreshCycleButtons();
		setExpanded(viewpointSet.isExpanded(), false, false);
		installGroupContextMenuRecursively(this);
		if (viewpointEntriesContainer.getParent() != setContentArea)
		{
			installGroupContextMenuRecursively(viewpointEntriesContainer);
		}
	}

	private void refreshEnabledAppearance(JPanel header)
	{
		boolean enabled = viewpointSet.isEnabled();
		setBackground(enabled ? ColorScheme.DARKER_GRAY_COLOR : DISABLED_GROUP_BACKGROUND);
		header.setBackground(enabled ? GROUP_HEADER_BACKGROUND : DISABLED_GROUP_HEADER_BACKGROUND);
		Color accent = enabled ? GROUP_ACCENT : DISABLED_GROUP_ACCENT;
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, accent),
			BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
				BorderFactory.createEmptyBorder(5, 2, 5, 2))));
		revalidate();
		repaint();
	}

	boolean setExpanded(boolean expanded, boolean persist)
	{
		return setExpanded(expanded, persist, true);
	}

	boolean setExpanded(boolean expanded, boolean persist, boolean relayout)
	{
		boolean stateChanged = viewpointSet.isExpanded() != expanded;
		viewpointSet.setExpanded(expanded);
		expandButton.setText(expanded ? "▼" : "▶");
		expandButton.setToolTipText(expanded ? "Collapse group" : "Expand group");
		if (expanded)
		{
			if (setContentArea.getParent() != this)
			{
				add(setContentArea, BorderLayout.CENTER);
			}
			if (compactCycleActions.getParent() == headerRightControls)
			{
				headerRightControls.remove(compactCycleActions);
			}
			if (cycleActions.getParent() != setActionArea)
			{
				setActionArea.add(cycleActions, 0);
			}
			if (expandedControls.getParent() != setActionArea)
			{
				setActionArea.add(expandedControls);
			}
			if (viewpointEntriesContainer.getParent() != setContentArea)
			{
				setContentArea.add(viewpointEntriesContainer, BorderLayout.CENTER);
			}
		}
		else if (viewpointEntriesContainer.getParent() == setContentArea)
		{
			setContentArea.remove(viewpointEntriesContainer);
		}
		if (!expanded && expandedControls.getParent() == setActionArea)
		{
			setActionArea.remove(expandedControls);
		}
		if (!expanded)
		{
			if (setContentArea.getParent() == this)
			{
				remove(setContentArea);
			}
			if (cycleActions.getParent() == setActionArea)
			{
				setActionArea.remove(cycleActions);
			}
			if (compactCycleActions.getParent() != headerRightControls)
			{
				headerRightControls.add(compactCycleActions, BorderLayout.WEST);
			}
		}
		headerRightControls.revalidate();
		headerRightControls.repaint();
		if (persist) plugin.saveConfig();
		if (stateChanged && relayout)
		{
			Container component = this;
			while (component != null)
			{
				component.revalidate();
				component.repaint();
				component = component.getParent();
			}
		}
		return stateChanged;
	}

	void refreshCurrentViewpoint()
	{
		for (CameraScenesViewpointPanel viewpointPanel : viewpointPanels) viewpointPanel.refreshCurrentViewpoint();
	}

	private String groupNameTooltip()
	{
		int count = plugin.getViewpointCatalog().listViewpoints(viewpointSet).size();
		String groupName = viewpointSet.getName() == null || viewpointSet.getName().trim().isEmpty()
			? "Untitled group" : viewpointSet.getName().trim();
		return groupName + " (" + count + " " + (count == 1 ? "Viewport" : "Viewports") + ")";
	}

	private JButton keybindButton(String shortcutLabel, Keybind assignedKeybind, KeybindSetter keybindSetter,
		KeybindConflictFinder conflictFinder, boolean targetIsNextGroupBinding)
	{
		final Keybind[] currentKeybind = {assignedKeybind == null ? Keybind.NOT_SET : assignedKeybind};
		final boolean[] capturing = {false};
		JButton shortcutButton = new JButton();
		shortcutButton.setForeground(Color.WHITE);
		shortcutButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		shortcutButton.setFocusPainted(false);
		shortcutButton.setFocusable(true);
		shortcutButton.setRequestFocusEnabled(true);
		shortcutButton.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		CameraScenesPanel.setKeybindButtonHeight(shortcutButton, 24);
		CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], false);
		shortcutButton.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mouseReleased(java.awt.event.MouseEvent mouseEvent)
			{
				if (mouseEvent.getButton() == java.awt.event.MouseEvent.BUTTON1)
				{
					capturing[0] = true;
					CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], true);
					CameraScenesPanel.startKeybindCaptureAnimation(shortcutButton);
					shortcutButton.requestFocusInWindow();
				}
				if (mouseEvent.getButton() == java.awt.event.MouseEvent.BUTTON3)
				{
					capturing[0] = false;
					currentKeybind[0] = Keybind.NOT_SET;
					keybindSetter.set(Keybind.NOT_SET);
					CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					plugin.saveConfig();
				}
			}
		});
		shortcutButton.addKeyListener(new KeyAdapter()
		{
			@Override public void keyPressed(KeyEvent keyEvent)
			{
				if (keyEvent.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					capturing[0] = false;
					CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					keyEvent.consume();
					shortcutButton.transferFocus();
					return;
				}
				if (!capturing[0]) return;
				if (Keybind.getModifierForKeyCode(keyEvent.getKeyCode()) != null) return;
				Keybind keybind = new Keybind(keyEvent);
				java.util.List<String> conflicts = conflictFinder.find(keybind);
				if (!conflicts.isEmpty())
				{
					capturing[0] = false;
					CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					if (CameraScenesPanel.confirmKeybindReassignment(CameraScenesViewpointSetPanel.this, conflicts))
					{
						plugin.getViewpointCatalog().clearActiveKeybindConflicts(keybind, viewpointSet, null,
							targetIsNextGroupBinding);
						currentKeybind[0] = keybind;
						keybindSetter.set(keybind);
						plugin.saveConfig();
						plugin.refreshPanel();
					}
					keyEvent.consume();
					shortcutButton.transferFocus();
					return;
				}
				currentKeybind[0] = keybind;
				keybindSetter.set(keybind);
				capturing[0] = false;
				CameraScenesPanel.setKeybindButtonState(shortcutButton, shortcutLabel, currentKeybind[0], false);
				CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
				plugin.saveConfig();
				keyEvent.consume();
				shortcutButton.transferFocus();
			}
		});
		return shortcutButton;
	}

	private void loadPreviousViewpoint()
	{
		plugin.loadViewpoint(plugin.getViewpointCatalog().rewind(viewpointSet));
	}

	private void loadNextViewpoint()
	{
		plugin.loadViewpoint(plugin.getViewpointCatalog().advance(viewpointSet));
	}

	private void refreshCycleButtons()
	{
		if (previousViewpointButton == null || nextViewpointButton == null
			|| compactPreviousViewpointButton == null || compactNextViewpointButton == null)
		{
			return;
		}
		boolean canCycle = viewpointSet.isEnabled()
			&& plugin.getViewpointCatalog().listEnabledViewpoints(viewpointSet).size() > 1;
		previousViewpointButton.setEnabled(canCycle);
		nextViewpointButton.setEnabled(canCycle);
		String previousTooltip = cycleTooltip("previous", viewpointSet.getPreviousKeybind(), canCycle);
		String nextTooltip = cycleTooltip("next", viewpointSet.getNextKeybind(), canCycle);
		previousViewpointButton.setToolTipText(previousTooltip);
		nextViewpointButton.setToolTipText(nextTooltip);
		compactPreviousViewpointButton.setEnabled(canCycle);
		compactNextViewpointButton.setEnabled(canCycle);
		compactPreviousViewpointButton.setToolTipText(previousTooltip);
		compactNextViewpointButton.setToolTipText(nextTooltip);
	}

	private String cycleTooltip(String direction, Keybind keybind, boolean canCycle)
	{
		String description = canCycle ? "Load the " + direction + " enabled viewpoint in this group"
			: "Requires at least two enabled viewpoints in this group";
		String buttonLabel = Character.toUpperCase(direction.charAt(0)) + direction.substring(1);
		return "<html><b>" + buttonLabel + " hotkey: " + CameraScenesPanel.formatKeybind(keybind)
			+ "</b><br>" + description + "</html>";
	}

	private void installGroupContextMenuRecursively(Component component)
	{
		if (component instanceof javax.swing.JComponent
			&& !(component instanceof javax.swing.text.JTextComponent)
			&& !(component instanceof javax.swing.AbstractButton)
			&& !(component instanceof javax.swing.JSpinner))
		{
			component.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override public void mousePressed(java.awt.event.MouseEvent event)
				{
					showContextMenu(event);
				}

				@Override public void mouseReleased(java.awt.event.MouseEvent event)
				{
					showContextMenu(event);
				}

				private void showContextMenu(java.awt.event.MouseEvent event)
				{
					if (event.isPopupTrigger())
					{
						CameraScenesPanel.showGroupBackupMenu(event.getComponent(), plugin, viewpointSet);
					}
				}
			});
		}

		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				installGroupContextMenuRecursively(child);
			}
		}
	}

	private interface KeybindSetter { void set(Keybind keybind); }
	private interface KeybindConflictFinder { java.util.List<String> find(Keybind keybind); }
}
