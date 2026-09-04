package com.camerascenes;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.runelite.client.config.Keybind;

/** A named collection of saved camera viewpoints and their shortcuts. */
public class CameraScenesViewpointSet
{
	private int id;
	private String name = "New Group";
	private boolean enabled = true;
	private boolean expanded = true;
	private Keybind nextKeybind = Keybind.NOT_SET;
	private Keybind previousKeybind = Keybind.NOT_SET;

	/** Keep the old storage key so existing Camera Scenes profiles continue to load. */
	@SerializedName("points")
	private List<CameraScenesViewpoint> viewpoints = new ArrayList<>();

	public CameraScenesViewpointSet()
	{
	}

	public CameraScenesViewpointSet(int id, String name)
	{
		this.id = id;
		this.name = name;
	}

	public int getId() { return id; }
	public void setId(int id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public boolean isExpanded() { return expanded; }
	public void setExpanded(boolean expanded) { this.expanded = expanded; }
	public Keybind getNextKeybind() { return nextKeybind; }
	public void setNextKeybind(Keybind keybind) { nextKeybind = CameraScenesViewpoint.normalizeKeybind(keybind); }
	public Keybind getPreviousKeybind() { return previousKeybind; }
	public void setPreviousKeybind(Keybind keybind) { previousKeybind = CameraScenesViewpoint.normalizeKeybind(keybind); }
	public List<CameraScenesViewpoint> getViewpoints() { return viewpoints; }
	public void setViewpoints(List<CameraScenesViewpoint> viewpoints)
	{
		this.viewpoints = viewpoints == null ? new ArrayList<>() : viewpoints;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other) return true;
		if (!(other instanceof CameraScenesViewpointSet)) return false;
		CameraScenesViewpointSet viewpointSet = (CameraScenesViewpointSet) other;
		return id == viewpointSet.id && enabled == viewpointSet.enabled && expanded == viewpointSet.expanded
			&& Objects.equals(name, viewpointSet.name) && Objects.equals(nextKeybind, viewpointSet.nextKeybind)
			&& Objects.equals(previousKeybind, viewpointSet.previousKeybind)
			&& Objects.equals(viewpoints, viewpointSet.viewpoints);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, enabled, expanded, nextKeybind, previousKeybind, viewpoints);
	}
}
