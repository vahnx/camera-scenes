package com.camerascenes;

import java.util.ArrayList;
import java.util.List;

/** Versioned JSON envelope for user-initiated Camera Scenes backups. */
final class CameraScenesBackup
{
	static final int CURRENT_SCHEMA_VERSION = 1;
	static final String FORMAT = "camera-scenes-backup";
	static final String SCOPE_ALL = "all";
	static final String SCOPE_GROUP = "group";

	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	private String format = FORMAT;
	private String scope = SCOPE_ALL;
	private CameraScenesBackupSettings settings;
	private List<CameraScenesViewpointSet> groups = new ArrayList<>();

	CameraScenesBackup()
	{
	}

	static CameraScenesBackup all(List<CameraScenesViewpointSet> groups, CameraScenesBackupSettings settings)
	{
		CameraScenesBackup backup = new CameraScenesBackup();
		backup.scope = SCOPE_ALL;
		backup.groups = groups == null ? new ArrayList<>() : groups;
		backup.settings = settings;
		return backup;
	}

	static CameraScenesBackup group(CameraScenesViewpointSet viewpointSet)
	{
		CameraScenesBackup backup = new CameraScenesBackup();
		backup.scope = SCOPE_GROUP;
		backup.groups = new ArrayList<>();
		if (viewpointSet != null)
		{
			backup.groups.add(viewpointSet);
		}
		return backup;
	}

	int getSchemaVersion()
	{
		return schemaVersion;
	}

	String getFormat()
	{
		return format;
	}

	String getScope()
	{
		return scope;
	}

	CameraScenesBackupSettings getSettings()
	{
		return settings;
	}

	List<CameraScenesViewpointSet> getGroups()
	{
		return groups;
	}
}
