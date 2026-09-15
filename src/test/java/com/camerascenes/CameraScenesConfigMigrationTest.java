package com.camerascenes;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class CameraScenesConfigMigrationTest
{
	@Test
	public void copiesLegacyValuesIntoNewNamespaceWithoutDeletingLegacyValues()
	{
		Map<String, String> values = new HashMap<>();
		values.put("eye-spy.groups", "legacy groups");
		values.put("eye-spy.smoothViewpointLoads", "true");

		migrate(values);

		assertEquals("legacy groups", values.get("camera-scenes.groups"));
		assertEquals("true", values.get("camera-scenes.smoothViewpointLoads"));
		assertEquals("legacy groups", values.get("eye-spy.groups"));
	}

	@Test
	public void preservesExistingNewValuesAndDoesNotMigrateRetiredKeys()
	{
		Map<String, String> values = new HashMap<>();
		values.put("camera-scenes.groups", "new groups");
		values.put("eye-spy.groups", "legacy groups");
		values.put("eye-spy.disableWhileTyping", "false");

		migrate(values);

		assertEquals("new groups", values.get("camera-scenes.groups"));
		assertNull(values.get("camera-scenes.disableWhileTyping"));
	}

	private static void migrate(Map<String, String> values)
	{
		CameraScenesConfigMigration.migrate(new CameraScenesConfigMigration.Configuration()
		{
			@Override
			public String get(String group, String key)
			{
				return values.get(group + "." + key);
			}

			@Override
			public void set(String group, String key, String value)
			{
				values.put(group + "." + key, value);
			}
		});
	}
}
