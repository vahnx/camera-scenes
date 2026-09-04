package com.camerascenes;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Reads and writes user-selected, versioned Camera Scenes JSON files. */
final class CameraScenesBackupService
{
	private static final int MAX_BACKUP_BYTES = 4 * 1024 * 1024;

	private final Gson gson;
	private final Gson formattedGson;

	@Inject
	CameraScenesBackupService(Gson gson)
	{
		this.gson = gson;
		this.formattedGson = gson.newBuilder().setPrettyPrinting().create();
	}

	void write(File file, CameraScenesBackup backup) throws IOException
	{
		if (file == null || backup == null)
		{
			throw new IOException("No backup file was selected.");
		}
		try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))
		{
			formattedGson.toJson(backup, writer);
		}
	}

	CameraScenesBackup read(File file) throws IOException
	{
		if (file == null || !file.isFile())
		{
			throw new IOException("The selected backup file does not exist.");
		}
		if (file.length() > MAX_BACKUP_BYTES)
		{
			throw new IOException("The selected backup file is too large.");
		}

		try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))
		{
			CameraScenesBackup backup = gson.fromJson(reader, CameraScenesBackup.class);
			validate(backup);
			return backup;
		}
		catch (JsonParseException | IllegalStateException ex)
		{
			throw new IOException("The selected file is not a valid Camera Scenes JSON backup.", ex);
		}
	}

	List<CameraScenesViewpointSet> copyGroups(List<CameraScenesViewpointSet> groups)
	{
		if (groups == null)
		{
			return new ArrayList<>();
		}
		List<CameraScenesViewpointSet> copies = new ArrayList<>();
		for (CameraScenesViewpointSet group : groups)
		{
			if (group != null)
			{
				copies.add(gson.fromJson(gson.toJson(group), CameraScenesViewpointSet.class));
			}
		}
		return copies;
	}

	private static void validate(CameraScenesBackup backup) throws IOException
	{
		if (backup == null || !CameraScenesBackup.FORMAT.equals(backup.getFormat()))
		{
			throw new IOException("This file is not a Camera Scenes backup.");
		}
		if (backup.getSchemaVersion() <= 0 || backup.getSchemaVersion() > CameraScenesBackup.CURRENT_SCHEMA_VERSION)
		{
			throw new IOException("This backup uses an unsupported Camera Scenes format version.");
		}
		if (!CameraScenesBackup.SCOPE_ALL.equals(backup.getScope())
			&& !CameraScenesBackup.SCOPE_GROUP.equals(backup.getScope()))
		{
			throw new IOException("This backup has an unsupported scope.");
		}
		if (backup.getGroups() == null)
		{
			throw new IOException("This backup does not contain any group data.");
		}
		if (CameraScenesBackup.SCOPE_GROUP.equals(backup.getScope()) && backup.getGroups().size() != 1)
		{
			throw new IOException("A group backup must contain exactly one group.");
		}
		if (CameraScenesBackup.SCOPE_ALL.equals(backup.getScope()) && backup.getSettings() == null)
		{
			throw new IOException("This full backup does not contain Camera Scenes settings.");
		}
	}
}
