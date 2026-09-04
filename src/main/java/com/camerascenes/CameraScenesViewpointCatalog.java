package com.camerascenes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import net.runelite.client.config.Keybind;

/** Owns Camera Scenes's saved viewpoint sets and transient selection state. */
@Singleton
public class CameraScenesViewpointCatalog
{
	private final List<CameraScenesViewpointSet> viewpointSets = new ArrayList<>();
	private final Map<Integer, Integer> lastChosenViewpointIds = new HashMap<>();
	private final List<Runnable> selectionListeners = new ArrayList<>();

	public void replaceAll(List<CameraScenesViewpointSet> loadedSets)
	{
		viewpointSets.clear();
		lastChosenViewpointIds.clear();
		if (loadedSets != null) viewpointSets.addAll(loadedSets);
		normalize();
	}

	public void appendAll(List<CameraScenesViewpointSet> additionalSets)
	{
		if (additionalSets != null)
		{
			viewpointSets.addAll(additionalSets);
			normalize();
		}
	}

	public void addSelectionListener(Runnable listener)
	{
		if (listener != null && !selectionListeners.contains(listener)) selectionListeners.add(listener);
	}

	public void removeSelectionListener(Runnable listener)
	{
		selectionListeners.remove(listener);
	}

	public void normalize()
	{
		for (int setIndex = 0; setIndex < viewpointSets.size(); setIndex++)
		{
			CameraScenesViewpointSet viewpointSet = viewpointSets.get(setIndex);
			if (viewpointSet == null) continue;
			viewpointSet.setId(setIndex);
			viewpointSet.setNextKeybind(viewpointSet.getNextKeybind());
			viewpointSet.setPreviousKeybind(viewpointSet.getPreviousKeybind());
			if (viewpointSet.getViewpoints() == null)
			{
				viewpointSet.setViewpoints(new ArrayList<>());
				continue;
			}
			for (int entryIndex = 0; entryIndex < viewpointSet.getViewpoints().size(); entryIndex++)
			{
				CameraScenesViewpoint savedViewpoint = viewpointSet.getViewpoints().get(entryIndex);
				if (savedViewpoint != null)
				{
					savedViewpoint.setId(entryIndex);
					savedViewpoint.setKeybind(savedViewpoint.getKeybind());
					savedViewpoint.setYaw(savedViewpoint.getYaw());
					savedViewpoint.setPitch(savedViewpoint.getPitch());
					savedViewpoint.setZoom(savedViewpoint.getZoom());
					savedViewpoint.setNotes(savedViewpoint.getNotes());
				}
			}
		}
	}

	public CameraScenesViewpointSet createSet()
	{
		CameraScenesViewpointSet newViewpointSet = new CameraScenesViewpointSet(viewpointSets.size(), "New Group");
		viewpointSets.add(newViewpointSet);
		return newViewpointSet;
	}

	public boolean canShiftSetOrder(CameraScenesViewpointSet viewpointSet, int offset)
	{
		int currentIndex = indexOfSet(viewpointSet);
		return currentIndex >= 0 && currentIndex + offset >= 0 && currentIndex + offset < viewpointSets.size();
	}

	public boolean shiftSetOrder(CameraScenesViewpointSet viewpointSet, int offset)
	{
		if (!canShiftSetOrder(viewpointSet, offset)) return false;

		Map<CameraScenesViewpointSet, Integer> selectedIdsBySet = captureSelectedViewpoints();
		int currentIndex = indexOfSet(viewpointSet);
		Collections.swap(viewpointSets, currentIndex, currentIndex + offset);
		normalize();
		restoreSelectedViewpoints(selectedIdsBySet);
		return true;
	}

	public void deleteSet(CameraScenesViewpointSet viewpointSet)
	{
		if (viewpointSet == null || !viewpointSets.remove(viewpointSet)) return;
		lastChosenViewpointIds.remove(viewpointSet.getId());
		normalize();
	}

	public CameraScenesViewpoint createViewpoint(CameraScenesViewpointSet viewpointSet)
	{
		CameraScenesViewpoint newViewpoint = new CameraScenesViewpoint(viewpointSet.getViewpoints().size(), "New Viewpoint", 0,
			CameraScenesViewpoint.DEFAULT_PITCH, 512, Keybind.NOT_SET, true);
		viewpointSet.getViewpoints().add(newViewpoint);
		return newViewpoint;
	}

	public boolean canShiftViewpointOrder(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint viewpoint, int offset)
	{
		int currentIndex = indexOfViewpoint(viewpointSet, viewpoint);
		return currentIndex >= 0 && currentIndex + offset >= 0
			&& currentIndex + offset < viewpointSet.getViewpoints().size();
	}

	public boolean shiftViewpointOrder(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint viewpoint, int offset)
	{
		if (!canShiftViewpointOrder(viewpointSet, viewpoint, offset)) return false;

		Integer selectedId = lastChosenViewpointIds.get(viewpointSet.getId());
		CameraScenesViewpoint selectedViewpoint = findViewpointById(viewpointSet, selectedId);
		int currentIndex = indexOfViewpoint(viewpointSet, viewpoint);
		Collections.swap(viewpointSet.getViewpoints(), currentIndex, currentIndex + offset);
		normalize();
		if (selectedViewpoint == null)
		{
			lastChosenViewpointIds.remove(viewpointSet.getId());
		}
		else
		{
			lastChosenViewpointIds.put(viewpointSet.getId(), selectedViewpoint.getId());
		}
		return true;
	}

	public void deleteViewpoint(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint savedViewpoint)
	{
		if (viewpointSet == null || savedViewpoint == null || !viewpointSet.getViewpoints().remove(savedViewpoint)) return;
		for (int entryIndex = 0; entryIndex < viewpointSet.getViewpoints().size(); entryIndex++)
		{
			CameraScenesViewpoint remainingViewpoint = viewpointSet.getViewpoints().get(entryIndex);
			if (remainingViewpoint != null) remainingViewpoint.setId(entryIndex);
		}
		lastChosenViewpointIds.remove(viewpointSet.getId());
	}

	public List<CameraScenesViewpointSet> listAllSets()
	{
		return viewpointSets.stream().filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(CameraScenesViewpointSet::getId)).collect(Collectors.toList());
	}

	public List<CameraScenesViewpointSet> listEnabledSets()
	{
		return listAllSets().stream().filter(CameraScenesViewpointSet::isEnabled).collect(Collectors.toList());
	}

	public List<CameraScenesViewpoint> listViewpoints(CameraScenesViewpointSet viewpointSet)
	{
		return viewpointSet == null ? new ArrayList<>() : viewpointSet.getViewpoints().stream().filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(CameraScenesViewpoint::getId)).collect(Collectors.toList());
	}

	public List<CameraScenesViewpoint> listEnabledViewpoints(CameraScenesViewpointSet viewpointSet)
	{
		return listViewpoints(viewpointSet).stream().filter(CameraScenesViewpoint::isEnabled).collect(Collectors.toList());
	}

	public CameraScenesViewpoint advance(CameraScenesViewpointSet viewpointSet)
	{
		return selectRelative(viewpointSet, SelectionStep.FORWARD);
	}

	public CameraScenesViewpoint rewind(CameraScenesViewpointSet viewpointSet)
	{
		return selectRelative(viewpointSet, SelectionStep.BACKWARD);
	}

	public void markSelected(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint savedViewpoint)
	{
		if (viewpointSet != null && savedViewpoint != null)
		{
			Integer previousId = lastChosenViewpointIds.get(viewpointSet.getId());
			lastChosenViewpointIds.put(viewpointSet.getId(), savedViewpoint.getId());
			if (!Objects.equals(previousId, savedViewpoint.getId()))
			{
				for (Runnable listener : new ArrayList<>(selectionListeners)) listener.run();
			}
		}
	}

	public boolean isCurrent(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint savedViewpoint)
	{
		return viewpointSet != null && savedViewpoint != null
			&& Objects.equals(lastChosenViewpointIds.get(viewpointSet.getId()), savedViewpoint.getId());
	}

	public CameraScenesViewpointSet findSetForViewpoint(CameraScenesViewpoint target)
	{
		if (target == null) return null;
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			if (viewpointSet != null && indexOfViewpoint(viewpointSet, target) >= 0) return viewpointSet;
		}
		return null;
	}

	public String findActiveKeybindConflict(Keybind candidate, CameraScenesViewpointSet targetSet,
		CameraScenesViewpoint targetViewpoint, boolean targetIsNextGroupBinding)
	{
		List<String> conflicts = listActiveKeybindConflicts(candidate, targetSet, targetViewpoint,
			targetIsNextGroupBinding, false);
		return conflicts.isEmpty() ? null : conflicts.get(0);
	}

	public String findGroupActivationConflict(CameraScenesViewpointSet targetSet)
	{
		List<String> conflicts = listGroupActivationConflicts(targetSet);
		return conflicts.isEmpty() ? null : conflicts.get(0);
	}

	public List<String> listGroupActivationConflicts(CameraScenesViewpointSet targetSet)
	{
		Set<String> conflicts = new LinkedHashSet<>();
		if (targetSet == null) return new ArrayList<>(conflicts);
		conflicts.addAll(listActiveKeybindConflicts(targetSet.getPreviousKeybind(), targetSet, null, false, true));
		conflicts.addAll(listActiveKeybindConflicts(targetSet.getNextKeybind(), targetSet, null, true, true));
		for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(targetSet))
		{
			conflicts.addAll(listActiveKeybindConflicts(viewpoint.getKeybind(), targetSet, viewpoint, false, true));
		}
		return new ArrayList<>(conflicts);
	}

	public int clearGroupActivationConflicts(CameraScenesViewpointSet targetSet)
	{
		if (targetSet == null) return 0;
		boolean wasEnabled = targetSet.isEnabled();
		targetSet.setEnabled(true);
		int cleared = 0;
		cleared += clearActiveKeybindConflicts(targetSet.getPreviousKeybind(), targetSet, null, false);
		cleared += clearActiveKeybindConflicts(targetSet.getNextKeybind(), targetSet, null, true);
		for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(targetSet))
		{
			cleared += clearActiveKeybindConflicts(viewpoint.getKeybind(), targetSet, viewpoint, false);
		}
		if (!wasEnabled) targetSet.setEnabled(false);
		return cleared;
	}

	public List<String> listActiveKeybindConflicts(Keybind candidate, CameraScenesViewpointSet targetSet,
		CameraScenesViewpoint targetViewpoint, boolean targetIsNextGroupBinding)
	{
		return listActiveKeybindConflicts(candidate, targetSet, targetViewpoint, targetIsNextGroupBinding, false);
	}

	public int clearActiveKeybindConflicts(Keybind candidate, CameraScenesViewpointSet targetSet,
		CameraScenesViewpoint targetViewpoint, boolean targetIsNextGroupBinding)
	{
		if (isUnset(candidate)) return 0;
		int cleared = 0;
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			if (viewpointSet == null || !viewpointSet.isEnabled()) continue;
			if (sameKeybind(candidate, viewpointSet.getPreviousKeybind())
				&& !(viewpointSet == targetSet && targetViewpoint == null && !targetIsNextGroupBinding))
			{
				viewpointSet.setPreviousKeybind(Keybind.NOT_SET);
				cleared++;
			}
			if (sameKeybind(candidate, viewpointSet.getNextKeybind())
				&& !(viewpointSet == targetSet && targetViewpoint == null && targetIsNextGroupBinding))
			{
				viewpointSet.setNextKeybind(Keybind.NOT_SET);
				cleared++;
			}
			for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(viewpointSet))
			{
				if (sameKeybind(candidate, viewpoint.getKeybind())
					&& !(viewpointSet == targetSet && viewpoint == targetViewpoint))
				{
					viewpoint.setKeybind(Keybind.NOT_SET);
					cleared++;
				}
			}
		}
		return cleared;
	}

	/** Returns duplicate active Camera Scenes bindings, grouped by their key combination. */
	public List<String> listAllActiveKeybindConflicts()
	{
		Map<String, List<String>> bindings = new LinkedHashMap<>();
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			if (viewpointSet == null || !viewpointSet.isEnabled()) continue;
			addBinding(bindings, viewpointSet.getPreviousKeybind(), bindingLabel(viewpointSet, null, "Previous hotkey"));
			addBinding(bindings, viewpointSet.getNextKeybind(), bindingLabel(viewpointSet, null, "Next hotkey"));
			for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(viewpointSet))
			{
				addBinding(bindings, viewpoint.getKeybind(), bindingLabel(viewpointSet, viewpoint, "Load hotkey"));
			}
		}

		List<String> conflicts = new ArrayList<>();
		for (Map.Entry<String, List<String>> binding : bindings.entrySet())
		{
			if (binding.getValue().size() > 1)
			{
				conflicts.add(binding.getKey() + ": " + String.join("; ", binding.getValue()));
			}
		}
		return conflicts;
	}

	public int clearActiveKeybindConflictsFor(List<CameraScenesViewpointSet> preferredSets)
	{
		if (preferredSets == null) return 0;
		int cleared = 0;
		for (CameraScenesViewpointSet viewpointSet : preferredSets)
		{
			if (viewpointSet == null || !viewpointSet.isEnabled()) continue;
			cleared += clearActiveKeybindConflicts(viewpointSet.getPreviousKeybind(), viewpointSet, null, false);
			cleared += clearActiveKeybindConflicts(viewpointSet.getNextKeybind(), viewpointSet, null, true);
			for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(viewpointSet))
			{
				cleared += clearActiveKeybindConflicts(viewpoint.getKeybind(), viewpointSet, viewpoint, false);
			}
		}
		return cleared;
	}

	private String findActiveKeybindConflict(Keybind candidate, CameraScenesViewpointSet targetSet,
		CameraScenesViewpoint targetViewpoint, boolean targetIsNextGroupBinding, boolean includeTargetSet)
	{
		List<String> conflicts = listActiveKeybindConflicts(candidate, targetSet, targetViewpoint,
			targetIsNextGroupBinding, includeTargetSet);
		return conflicts.isEmpty() ? null : conflicts.get(0);
	}

	private List<String> listActiveKeybindConflicts(Keybind candidate, CameraScenesViewpointSet targetSet,
		CameraScenesViewpoint targetViewpoint, boolean targetIsNextGroupBinding, boolean includeTargetSet)
	{
		Set<String> conflicts = new LinkedHashSet<>();
		if (isUnset(candidate)) return new ArrayList<>(conflicts);
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			if (viewpointSet == null || (!viewpointSet.isEnabled() && !(includeTargetSet && viewpointSet == targetSet)))
			{
				continue;
			}
			if (sameKeybind(candidate, viewpointSet.getPreviousKeybind())
				&& !(viewpointSet == targetSet && targetViewpoint == null && !targetIsNextGroupBinding))
			{
				conflicts.add(bindingLabel(viewpointSet, null, "Previous hotkey"));
			}
			if (sameKeybind(candidate, viewpointSet.getNextKeybind())
				&& !(viewpointSet == targetSet && targetViewpoint == null && targetIsNextGroupBinding))
			{
				conflicts.add(bindingLabel(viewpointSet, null, "Next hotkey"));
			}
			for (CameraScenesViewpoint viewpoint : listEnabledViewpoints(viewpointSet))
			{
				if (sameKeybind(candidate, viewpoint.getKeybind())
					&& !(viewpointSet == targetSet && viewpoint == targetViewpoint))
				{
					conflicts.add(bindingLabel(viewpointSet, viewpoint, "Load hotkey"));
				}
			}
		}
		return new ArrayList<>(conflicts);
	}

	private static boolean isUnset(Keybind keybind)
	{
		return CameraScenesViewpoint.isUnsetKeybind(keybind);
	}

	private static boolean sameKeybind(Keybind first, Keybind second)
	{
		return !isUnset(first) && !isUnset(second)
			&& first.getKeyCode() == second.getKeyCode() && first.getModifiers() == second.getModifiers();
	}

	private static void addBinding(Map<String, List<String>> bindings, Keybind keybind, String label)
	{
		if (isUnset(keybind)) return;
		bindings.computeIfAbsent(keybind.toString(), ignored -> new ArrayList<>()).add(label);
	}

	private static String bindingLabel(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint viewpoint, String action)
	{
		String groupName = viewpointSet.getName() == null || viewpointSet.getName().trim().isEmpty()
			? "Untitled group" : viewpointSet.getName().trim();
		if (viewpoint == null) return "Group \"" + groupName + "\" " + action;
		String viewpointName = viewpoint.getName() == null || viewpoint.getName().trim().isEmpty()
			? "Untitled viewpoint" : viewpoint.getName().trim();
		return "Group \"" + groupName + "\" / \"" + viewpointName + "\" " + action;
	}

	private CameraScenesViewpoint selectRelative(CameraScenesViewpointSet viewpointSet, SelectionStep step)
	{
		List<CameraScenesViewpoint> enabledViewpoints = listEnabledViewpoints(viewpointSet);
		if (enabledViewpoints.isEmpty()) return null;

		Integer previousViewpointId = lastChosenViewpointIds.get(viewpointSet.getId());
		int previousIndex = -1;
		for (int entryIndex = 0; entryIndex < enabledViewpoints.size(); entryIndex++)
		{
			if (previousViewpointId != null && enabledViewpoints.get(entryIndex).getId() == previousViewpointId)
			{
				previousIndex = entryIndex;
				break;
			}
		}

		int selectedIndex = previousIndex < 0 ? step.initialIndex(enabledViewpoints.size())
			: Math.floorMod(previousIndex + step.offset, enabledViewpoints.size());
		CameraScenesViewpoint selectedViewpoint = enabledViewpoints.get(selectedIndex);
		markSelected(viewpointSet, selectedViewpoint);
		return selectedViewpoint;
	}

	private enum SelectionStep
	{
		FORWARD(1),
		BACKWARD(-1);

		private final int offset;

		SelectionStep(int offset)
		{
			this.offset = offset;
		}

		private int initialIndex(int listSize)
		{
			return offset > 0 ? 0 : listSize - 1;
		}
	}

	private int indexOfSet(CameraScenesViewpointSet target)
	{
		for (int index = 0; index < viewpointSets.size(); index++)
		{
			if (viewpointSets.get(index) == target) return index;
		}
		return -1;
	}

	private int indexOfViewpoint(CameraScenesViewpointSet viewpointSet, CameraScenesViewpoint target)
	{
		if (viewpointSet == null || target == null || viewpointSet.getViewpoints() == null) return -1;
		for (int index = 0; index < viewpointSet.getViewpoints().size(); index++)
		{
			if (viewpointSet.getViewpoints().get(index) == target) return index;
		}
		return -1;
	}

	private Map<CameraScenesViewpointSet, Integer> captureSelectedViewpoints()
	{
		Map<CameraScenesViewpointSet, Integer> selectedIdsBySet = new IdentityHashMap<>();
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			Integer selectedId = lastChosenViewpointIds.get(viewpointSet.getId());
			if (selectedId != null) selectedIdsBySet.put(viewpointSet, selectedId);
		}
		return selectedIdsBySet;
	}

	private void restoreSelectedViewpoints(Map<CameraScenesViewpointSet, Integer> selectedIdsBySet)
	{
		lastChosenViewpointIds.clear();
		for (CameraScenesViewpointSet viewpointSet : viewpointSets)
		{
			Integer selectedId = selectedIdsBySet.get(viewpointSet);
			if (selectedId != null) lastChosenViewpointIds.put(viewpointSet.getId(), selectedId);
		}
	}

	private CameraScenesViewpoint findViewpointById(CameraScenesViewpointSet viewpointSet, Integer viewpointId)
	{
		if (viewpointSet == null || viewpointId == null || viewpointSet.getViewpoints() == null) return null;
		for (CameraScenesViewpoint savedViewpoint : viewpointSet.getViewpoints())
		{
			if (savedViewpoint != null && savedViewpoint.getId() == viewpointId) return savedViewpoint;
		}
		return null;
	}
}
