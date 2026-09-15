package com.camerascenes;

import java.util.Arrays;
import java.util.List;

/** Copies saved settings from the plugin's former config namespace. */
final class CameraScenesConfigMigration
{
	static final String LEGACY_GROUP = "eye-spy";
	private static final List<String> KEYS = Arrays.asList(
		"groups",
		"smoothViewpointLoads",
		"viewpointLoadDuration",
		"smoothZoomLoads",
		"showDebugTextInSidePanel");

	private CameraScenesConfigMigration()
	{
	}

	static void migrate(Configuration configuration)
	{
		for (String key : KEYS)
		{
			if (configuration.get(CameraScenesConfig.GROUP, key) != null)
			{
				continue;
			}

			String legacyValue = configuration.get(LEGACY_GROUP, key);
			if (legacyValue != null)
			{
				configuration.set(CameraScenesConfig.GROUP, key, legacyValue);
			}
		}
	}

	interface Configuration
	{
		String get(String group, String key);

		void set(String group, String key, String value);
	}
}
