package com.camerascenes;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.ColorScheme;

final class CameraScenesViewpointPanel extends JPanel
{
	private static final Color VIEWPOINT_CARD_BACKGROUND = new Color(51, 51, 51);
	private static final Color CURRENT_VIEWPOINT_OUTLINE = new Color(112, 190, 225);
	private static final int NUMBER_FIELD_MAX_CHARACTERS = 6;
	private static final float NUMBER_FIELD_FONT_SIZE = 10.0f;
	private final CameraScenesPlugin plugin;
	private final CameraScenesViewpointSet viewpointSet;
	private final CameraScenesViewpoint viewpoint;
	private final Runnable reloadSet;
	private final Runnable refreshGroupControls;
	private final JPanel actionArea = new JPanel();
	private JButton notesButton;
	private JButton undoButton;
	private JButton redoButton;
	private JPanel notesEditorPanel;
	private JTextArea notesEditor;

	CameraScenesViewpointPanel(CameraScenesPlugin plugin, CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint viewpoint,
		Runnable reloadSet, Runnable refreshGroupControls)
	{
		this.plugin = plugin;
		this.viewpointSet = viewpointSet;
		this.viewpoint = viewpoint;
		this.reloadSet = reloadSet;
		this.refreshGroupControls = refreshGroupControls;
		setLayout(new BorderLayout(4, 4));
		setBackground(VIEWPOINT_CARD_BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		refreshCurrentViewpoint();

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setOpaque(false);
		JCheckBox enabledToggle = new JCheckBox();
		enabledToggle.setSelected(viewpoint.isEnabled());
		enabledToggle.setOpaque(false);
		enabledToggle.setToolTipText("Enable or disable this viewpoint hotkey");
		enabledToggle.addActionListener(actionEvent -> {
			if (enabledToggle.isSelected())
			{
				java.util.List<String> conflicts = plugin.getViewpointCatalog().listActiveKeybindConflicts(
					viewpoint.getKeybind(), viewpointSet, viewpoint, false);
				if (!conflicts.isEmpty())
				{
					enabledToggle.setSelected(false);
					if (CameraScenesPanel.confirmKeybindReassignment(this, conflicts))
					{
						plugin.getViewpointCatalog().clearActiveKeybindConflicts(viewpoint.getKeybind(), viewpointSet,
							viewpoint, false);
						viewpoint.setEnabled(true);
						plugin.saveConfig();
						plugin.refreshPanel();
					}
					return;
				}
			}
			if (viewpoint.isEnabled() != enabledToggle.isSelected())
			{
				viewpoint.setEnabled(enabledToggle.isSelected());
			}
			plugin.saveConfig();
			refreshGroupControls.run();
		});
		JTextField viewpointNameField = new JTextField(viewpoint.getName() == null ? "" : viewpoint.getName());
		viewpointNameField.setPreferredSize(new Dimension(160, 24));
		viewpointNameField.setMinimumSize(new Dimension(80, 24));
		viewpointNameField.setForeground(Color.WHITE);
		viewpointNameField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		viewpointNameField.setCaretColor(Color.WHITE);
		viewpointNameField.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		viewpointNameField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent documentEvent) { update(); }
			@Override public void removeUpdate(DocumentEvent documentEvent) { update(); }
			@Override public void changedUpdate(DocumentEvent documentEvent) { update(); }
			private void update()
			{
				String name = viewpointNameField.getText();
				if (!java.util.Objects.equals(viewpoint.getName(), name))
				{
					viewpoint.setName(name);
					plugin.saveConfig();
				}
			}
		});
		top.add(viewpointNameField, BorderLayout.CENTER);

		JPanel cameraValues = new JPanel(new GridLayout(1, 3, 4, 0));
		cameraValues.setOpaque(false);
		cameraValues.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
		JComboBox<CameraScenesViewpoint.CardinalDirection> directionSelector =
			new JComboBox<>(CameraScenesViewpoint.CardinalDirection.values());
		directionSelector.setSelectedItem(viewpoint.getDirection());
		directionSelector.setToolTipText("Yaw is saved as the nearest compass direction");
		JSpinner pitchSpinner = spinner(viewpoint.getPitch(), CameraScenesViewpoint.MIN_PITCH, CameraScenesViewpoint.MAX_PITCH);
		JSpinner zoomSpinner = spinner(viewpoint.getZoom(), CameraScenesViewpoint.MIN_ZOOM, CameraScenesViewpoint.MAX_ZOOM);
		cameraValues.add(value("Direction", directionSelector));
		cameraValues.add(value("Pitch", pitchSpinner));
		cameraValues.add(value("Zoom", zoomSpinner));
		add(cameraValues, BorderLayout.CENTER);
		directionSelector.addActionListener(actionEvent -> {
			CameraScenesViewpoint.CardinalDirection direction =
				(CameraScenesViewpoint.CardinalDirection) directionSelector.getSelectedItem();
			if (direction != null && viewpoint.getYaw() != direction.getYaw())
			{
				plugin.getViewpointHistory().recordBeforeMutation(viewpoint);
				viewpoint.setYaw(direction.getYaw());
				plugin.saveConfig();
				refreshHistoryButtons();
			}
		});
		bindSpinner(pitchSpinner, viewpoint::setPitch);
		bindSpinner(zoomSpinner, viewpoint::setZoom);

		actionArea.setLayout(new BoxLayout(actionArea, BoxLayout.Y_AXIS));
		actionArea.setOpaque(false);
		JButton hotkey = keybindButton();
		CameraScenesPanel.setKeybindButtonHeight(hotkey, 24);
		JButton moveViewpointUpButton = CameraScenesPanel.orderButton("↑", "Move viewpoint up", () -> {
			if (plugin.getViewpointCatalog().shiftViewpointOrder(viewpointSet, viewpoint, -1))
			{
				plugin.saveConfig();
				reloadSet.run();
			}
		});
		moveViewpointUpButton.setEnabled(plugin.getViewpointCatalog().canShiftViewpointOrder(viewpointSet, viewpoint, -1));
		JButton moveViewpointDownButton = CameraScenesPanel.orderButton("↓", "Move viewpoint down", () -> {
			if (plugin.getViewpointCatalog().shiftViewpointOrder(viewpointSet, viewpoint, 1))
			{
				plugin.saveConfig();
				reloadSet.run();
			}
		});
		moveViewpointDownButton.setEnabled(plugin.getViewpointCatalog().canShiftViewpointOrder(viewpointSet, viewpoint, 1));
		JPanel viewpointOrderControls = new JPanel(new GridLayout(1, 3, 2, 0));
		viewpointOrderControls.setOpaque(false);
		viewpointOrderControls.setPreferredSize(new Dimension(76, 24));
		viewpointOrderControls.setMinimumSize(new Dimension(76, 24));
		viewpointOrderControls.setMaximumSize(new Dimension(76, 24));
		viewpointOrderControls.add(enabledToggle);
		viewpointOrderControls.add(moveViewpointUpButton);
		viewpointOrderControls.add(moveViewpointDownButton);
		top.add(viewpointOrderControls, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);
		JPanel hotkeyRow = new JPanel(new GridLayout(1, 2, 4, 0));
		hotkeyRow.setOpaque(false);
		hotkeyRow.setAlignmentX(LEFT_ALIGNMENT);
		hotkeyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		hotkeyRow.add(hotkey);
		hotkeyRow.add(CameraScenesPanel.button("Load View", ColorScheme.BRAND_ORANGE, () -> plugin.loadViewpoint(viewpoint)));
		actionArea.add(hotkeyRow);
		actionArea.add(Box.createVerticalStrut(4));
		JPanel viewpointActions = new JPanel(new GridLayout(1, 2, 4, 0));
		viewpointActions.setOpaque(false);
		viewpointActions.setAlignmentX(LEFT_ALIGNMENT);
		viewpointActions.add(CameraScenesPanel.button("Save View", new Color(0x3D6E9E), () -> plugin.captureViewpoint(viewpoint, reloadSet)));
		viewpointActions.add(CameraScenesPanel.button("Delete View", new Color(0x8B3D3D), () -> {
			int result = javax.swing.JOptionPane.showConfirmDialog(this, "Delete this viewpoint?", "Confirm delete",
				javax.swing.JOptionPane.OK_CANCEL_OPTION);
			if (result == javax.swing.JOptionPane.OK_OPTION)
			{
				plugin.getViewpointCatalog().deleteViewpoint(viewpointSet, viewpoint);
				plugin.saveConfig();
				reloadSet.run();
			}
		}));
		actionArea.add(viewpointActions);
		actionArea.add(Box.createVerticalStrut(4));
		notesButton = CameraScenesPanel.button("Notes", ColorScheme.DARKER_GRAY_COLOR, this::toggleNotesEditor);
		notesButton.setToolTipText("Add or edit notes for this viewpoint");
		JPanel historyActions = new JPanel(new GridLayout(1, 3, 4, 0));
		historyActions.setOpaque(false);
		historyActions.setAlignmentX(LEFT_ALIGNMENT);
		undoButton = CameraScenesPanel.button("\u21B6", ColorScheme.DARKER_GRAY_COLOR, this::undoViewpoint);
		undoButton.setToolTipText("Undo the last camera-value change for this viewpoint");
		redoButton = CameraScenesPanel.button("\u21B7", ColorScheme.DARKER_GRAY_COLOR, this::redoViewpoint);
		redoButton.setToolTipText("Redo the last undone camera change for this viewpoint");
		historyActions.add(notesButton);
		historyActions.add(undoButton);
		historyActions.add(redoButton);
		actionArea.add(historyActions);
		add(actionArea, BorderLayout.SOUTH);
		refreshHistoryButtons();
	}

	void refreshCurrentViewpoint()
	{
		Color outline = plugin.getViewpointCatalog().isCurrent(viewpointSet, viewpoint)
			? CURRENT_VIEWPOINT_OUTLINE : ColorScheme.MEDIUM_GRAY_COLOR;
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(outline),
			BorderFactory.createEmptyBorder(4, 4, 4, 4)));
		revalidate();
		repaint();
	}

	private void toggleNotesEditor()
	{
		if (notesEditorPanel == null)
		{
			openNotesEditor();
		}
		else
		{
			closeNotesEditor();
		}
	}

	private void openNotesEditor()
	{
		notesEditor = new JTextArea(viewpoint.getNotes(), 4, 20);
		notesEditor.setLineWrap(true);
		notesEditor.setWrapStyleWord(true);
		notesEditor.setForeground(Color.WHITE);
		notesEditor.setBackground(ColorScheme.DARK_GRAY_COLOR);
		notesEditor.setCaretColor(Color.WHITE);
		notesEditor.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JScrollPane notesScrollPane = new JScrollPane(notesEditor);
		notesScrollPane.setPreferredSize(new Dimension(200, 92));
		notesScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		notesEditorPanel = new JPanel(new BorderLayout(0, 4));
		notesEditorPanel.setOpaque(false);
		notesEditorPanel.setAlignmentX(LEFT_ALIGNMENT);
		notesEditorPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		notesEditorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 124));
		notesEditorPanel.add(notesScrollPane, BorderLayout.CENTER);
		notesEditor.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent documentEvent) { saveNotesAutomatically(); }
			@Override public void removeUpdate(DocumentEvent documentEvent) { saveNotesAutomatically(); }
			@Override public void changedUpdate(DocumentEvent documentEvent) { saveNotesAutomatically(); }
		});

		actionArea.add(notesEditorPanel);
		notesButton.setText("Close");
		notesButton.setToolTipText("Close the inline notes editor");
		refreshLayout();
		notesEditor.requestFocusInWindow();
	}

	private void saveNotesAutomatically()
	{
		String notes = notesEditor.getText().trim();
		if (!java.util.Objects.equals(viewpoint.getNotes(), notes))
		{
			viewpoint.setNotes(notes);
			plugin.saveConfig();
		}
	}

	private void closeNotesEditor()
	{
		if (notesEditorPanel == null) return;
		actionArea.remove(notesEditorPanel);
		notesEditorPanel = null;
		notesEditor = null;
		notesButton.setText(noteButtonText());
		notesButton.setToolTipText("Add or edit notes for this viewpoint");
		refreshLayout();
	}

	private void refreshLayout()
	{
		Container component = this;
		while (component != null)
		{
			component.revalidate();
			component.repaint();
			component = component.getParent();
		}
	}

	private String noteButtonText()
	{
		return "Notes";
	}

	private JPanel value(String label, Component input)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setOpaque(false);
		JLabel text = new JLabel(label);
		text.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		text.setHorizontalAlignment(JLabel.CENTER);
		panel.add(text, BorderLayout.NORTH);
		panel.add(input, BorderLayout.CENTER);
		return panel;
	}

	private JSpinner spinner(int value, int min, int max)
	{
		JSpinner cameraSpinner = new JSpinner(new SpinnerNumberModel(value, min, max, 1));
		JSpinner.NumberEditor numberEditor = new JSpinner.NumberEditor(cameraSpinner, "0");
		cameraSpinner.setEditor(numberEditor);
		JTextField numberField = numberEditor.getTextField();
		numberField.setColumns(NUMBER_FIELD_MAX_CHARACTERS);
		numberField.setFont(numberField.getFont().deriveFont(NUMBER_FIELD_FONT_SIZE));
		if (numberField.getDocument() instanceof AbstractDocument)
		{
			AbstractDocument document = (AbstractDocument) numberField.getDocument();
			document.setDocumentFilter(new CameraNumberDocumentFilter(min < 0, document.getDocumentFilter()));
		}
		cameraSpinner.setPreferredSize(new Dimension(74, 24));
		cameraSpinner.setToolTipText("Edit the saved camera value; extended pitch requires RuneLite Camera's pitch limit setting");
		return cameraSpinner;
	}

	private void bindSpinner(JSpinner spinner, IntSetter setter)
	{
		spinner.addChangeListener(changeEvent -> {
			plugin.getViewpointHistory().recordBeforeMutation(viewpoint);
			setter.set((Integer) spinner.getValue());
			plugin.saveConfig();
			plugin.refreshCompatibilityWarnings();
			refreshHistoryButtons();
		});
	}

	private void undoViewpoint()
	{
		if (plugin.getViewpointHistory().undo(viewpoint))
		{
			plugin.loadViewpoint(viewpoint);
			plugin.saveConfig();
			reloadSet.run();
		}
	}

	private void redoViewpoint()
	{
		if (plugin.getViewpointHistory().redo(viewpoint))
		{
			plugin.loadViewpoint(viewpoint);
			plugin.saveConfig();
			reloadSet.run();
		}
	}

	private void refreshHistoryButtons()
	{
		if (undoButton == null || redoButton == null)
		{
			return;
		}

		CameraScenesViewpointHistory history = plugin.getViewpointHistory();
		undoButton.setEnabled(history.canUndo(viewpoint));
		redoButton.setEnabled(history.canRedo(viewpoint));
	}

	private JButton keybindButton()
	{
		final Keybind[] currentKeybind = {viewpoint.getKeybind() == null ? Keybind.NOT_SET : viewpoint.getKeybind()};
		final boolean[] capturing = {false};
		JButton shortcutButton = new JButton();
		shortcutButton.setForeground(Color.WHITE);
		shortcutButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		shortcutButton.setFocusPainted(false);
		shortcutButton.setFocusable(true);
		shortcutButton.setRequestFocusEnabled(true);
		shortcutButton.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], false);
		shortcutButton.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mouseReleased(java.awt.event.MouseEvent mouseEvent)
			{
				if (mouseEvent.getButton() == java.awt.event.MouseEvent.BUTTON1)
				{
					capturing[0] = true;
					CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], true);
					CameraScenesPanel.startKeybindCaptureAnimation(shortcutButton);
					shortcutButton.requestFocusInWindow();
				}
				if (mouseEvent.getButton() == java.awt.event.MouseEvent.BUTTON3)
				{
					capturing[0] = false;
					if (!CameraScenesViewpoint.isUnsetKeybind(currentKeybind[0]))
					{
						currentKeybind[0] = Keybind.NOT_SET;
						viewpoint.setKeybind(Keybind.NOT_SET);
					}
					CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					plugin.saveConfig();
					refreshHistoryButtons();
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
					CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					keyEvent.consume();
					shortcutButton.transferFocus();
					return;
				}
				if (!capturing[0]) return;
				if (Keybind.getModifierForKeyCode(keyEvent.getKeyCode()) != null) return;
				Keybind keybind = new Keybind(keyEvent);
				java.util.List<String> conflicts = plugin.getViewpointCatalog().listActiveKeybindConflicts(keybind,
					viewpointSet, viewpoint, false);
				if (!conflicts.isEmpty())
				{
					capturing[0] = false;
					CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], false);
					CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
					if (CameraScenesPanel.confirmKeybindReassignment(CameraScenesViewpointPanel.this, conflicts))
					{
						plugin.getViewpointCatalog().clearActiveKeybindConflicts(keybind, viewpointSet, viewpoint, false);
						currentKeybind[0] = keybind;
						viewpoint.setKeybind(keybind);
						plugin.saveConfig();
						plugin.refreshPanel();
					}
					keyEvent.consume();
					shortcutButton.transferFocus();
					return;
				}
				if (!keybind.equals(currentKeybind[0]))
				{
					currentKeybind[0] = keybind;
					viewpoint.setKeybind(keybind);
				}
				capturing[0] = false;
				CameraScenesPanel.setKeybindButtonState(shortcutButton, "Load hotkey", currentKeybind[0], false);
				CameraScenesPanel.stopKeybindCaptureAnimation(shortcutButton);
				plugin.saveConfig();
				refreshHistoryButtons();
				keyEvent.consume();
				shortcutButton.transferFocus();
			}
		});
		return shortcutButton;
	}

	private interface IntSetter
	{
		void set(int value);
	}

	private static final class CameraNumberDocumentFilter extends DocumentFilter
	{
		private final boolean negativeAllowed;
		private final DocumentFilter delegate;

		private CameraNumberDocumentFilter(boolean negativeAllowed, DocumentFilter delegate)
		{
			this.negativeAllowed = negativeAllowed;
			this.delegate = delegate;
		}

		@Override
		public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
			throws BadLocationException
		{
			replace(bypass, offset, 0, text, attributes);
		}

		@Override
		public void remove(FilterBypass bypass, int offset, int length) throws BadLocationException
		{
			replace(bypass, offset, length, "", null);
		}

		@Override
		public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
			throws BadLocationException
		{
			String current = bypass.getDocument().getText(0, bypass.getDocument().getLength());
			String replacement = text == null ? "" : text;
			String candidate = current.substring(0, offset) + replacement
				+ current.substring(offset + length);
			if (isValid(candidate))
			{
				if (delegate == null)
				{
					bypass.replace(offset, length, replacement, attributes);
				}
				else
				{
					delegate.replace(bypass, offset, length, replacement, attributes);
				}
			}
		}

		private boolean isValid(String value)
		{
			if (value.isEmpty() || value.equals("-"))
			{
				return value.isEmpty() || negativeAllowed;
			}
			if (value.length() > NUMBER_FIELD_MAX_CHARACTERS
				|| (value.startsWith("-") && !negativeAllowed))
			{
				return false;
			}
			int start = value.startsWith("-") ? 1 : 0;
			for (int index = start; index < value.length(); index++)
			{
				if (value.charAt(index) < '0' || value.charAt(index) > '9')
				{
					return false;
				}
			}
			return true;
		}
	}
}
