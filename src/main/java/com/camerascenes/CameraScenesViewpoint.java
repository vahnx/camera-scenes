package com.camerascenes;

import java.util.Objects;
import net.runelite.client.config.Keybind;

public class CameraScenesViewpoint
{
	/** RuneLite camera yaw uses 14-bit Jagex Angle Units per revolution. */
	public static final int YAW_UNITS = 1 << 14;
	public static final int MIN_PITCH = 0;
	public static final int MAX_PITCH = 4160;
	public static final int DEFAULT_MIN_PITCH = 128;
	public static final int DEFAULT_MAX_PITCH = 383;
	public static final int DEFAULT_PITCH = 128;
	public static final int MIN_ZOOM = -272;
	public static final int MAX_ZOOM = 1400;

	private int id;
	private String name = "New Viewpoint";
	private String notes = "";
	private int yaw;
	private int pitch = DEFAULT_PITCH;
	private int zoom = 512;
	private Keybind keybind = Keybind.NOT_SET;
	private boolean enabled = true;

	public CameraScenesViewpoint()
	{
	}

	public CameraScenesViewpoint(int id, String name, int yaw, int pitch, int zoom, Keybind keybind, boolean enabled)
	{
		this.id = id;
		this.name = name;
		setYaw(yaw);
		setPitch(pitch);
		setZoom(zoom);
		this.keybind = keybind;
		this.enabled = enabled;
	}

	public void capture(int yaw, int pitch, int zoom)
	{
		this.yaw = normalizeYaw(yaw);
		this.pitch = clamp(pitch, MIN_PITCH, MAX_PITCH);
		this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
	}

	public int getId() { return id; }
	public void setId(int id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getNotes() { return notes == null ? "" : notes; }
	public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }
	public int getYaw() { return yaw; }
	public void setYaw(int yaw) { this.yaw = normalizeYaw(yaw); }
	public int getPitch() { return pitch; }
	public void setPitch(int pitch) { this.pitch = clamp(pitch, MIN_PITCH, MAX_PITCH); }
	public int getZoom() { return zoom; }
	public void setZoom(int zoom) { this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM); }
	public Keybind getKeybind() { return keybind; }
	public void setKeybind(Keybind keybind) { this.keybind = normalizeKeybind(keybind); }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	static boolean isUnsetKeybind(Keybind keybind)
	{
		return keybind == null
			|| (keybind.getKeyCode() == Keybind.NOT_SET.getKeyCode()
				&& keybind.getModifiers() == Keybind.NOT_SET.getModifiers());
	}

	static Keybind normalizeKeybind(Keybind keybind)
	{
		return isUnsetKeybind(keybind) ? Keybind.NOT_SET : keybind;
	}

	public static int normalizeYaw(int yaw)
	{
		return Math.floorMod(yaw, YAW_UNITS);
	}

	static boolean usesExtendedPitch(int pitch)
	{
		return pitch < DEFAULT_MIN_PITCH || pitch > DEFAULT_MAX_PITCH;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other) return true;
		if (!(other instanceof CameraScenesViewpoint)) return false;
		CameraScenesViewpoint viewpoint = (CameraScenesViewpoint) other;
		return id == viewpoint.id && yaw == viewpoint.yaw && pitch == viewpoint.pitch && zoom == viewpoint.zoom
			&& enabled == viewpoint.enabled && Objects.equals(name, viewpoint.name)
			&& Objects.equals(getNotes(), viewpoint.getNotes()) && Objects.equals(keybind, viewpoint.keybind);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id, name, getNotes(), yaw, pitch, zoom, keybind, enabled);
	}
}
