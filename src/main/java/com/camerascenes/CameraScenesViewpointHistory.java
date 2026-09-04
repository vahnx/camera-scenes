package com.camerascenes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Singleton;

/** Keeps a bounded, session-only camera-value history for individual viewpoints. */
@Singleton
final class CameraScenesViewpointHistory
{
	private static final int MAX_HISTORY_ENTRIES = 20;
	private static final long COALESCE_WINDOW_NANOS = 750_000_000L;

	private final Map<CameraScenesViewpoint, ViewpointHistory> histories = new IdentityHashMap<>();

	void recordBeforeMutation(CameraScenesViewpoint viewpoint)
	{
		if (viewpoint == null)
		{
			return;
		}

		ViewpointHistory history = histories.computeIfAbsent(viewpoint, ignored -> new ViewpointHistory());
		long now = System.nanoTime();
		if (history.undo.isEmpty() || now - history.lastCheckpointNanos > COALESCE_WINDOW_NANOS)
		{
			history.undo.addLast(ViewpointState.capture(viewpoint));
			trim(history.undo);
		}
		history.lastCheckpointNanos = now;
		history.redo.clear();
	}

	boolean canUndo(CameraScenesViewpoint viewpoint)
	{
		ViewpointHistory history = histories.get(viewpoint);
		return history != null && !history.undo.isEmpty();
	}

	boolean canRedo(CameraScenesViewpoint viewpoint)
	{
		ViewpointHistory history = histories.get(viewpoint);
		return history != null && !history.redo.isEmpty();
	}

	boolean undo(CameraScenesViewpoint viewpoint)
	{
		ViewpointHistory history = histories.get(viewpoint);
		if (history == null || history.undo.isEmpty())
		{
			return false;
		}

		history.redo.addLast(ViewpointState.capture(viewpoint));
		trim(history.redo);
		history.undo.removeLast().applyTo(viewpoint);
		history.lastCheckpointNanos = 0;
		return true;
	}

	boolean redo(CameraScenesViewpoint viewpoint)
	{
		ViewpointHistory history = histories.get(viewpoint);
		if (history == null || history.redo.isEmpty())
		{
			return false;
		}

		history.undo.addLast(ViewpointState.capture(viewpoint));
		trim(history.undo);
		history.redo.removeLast().applyTo(viewpoint);
		history.lastCheckpointNanos = 0;
		return true;
	}

	void clearAll()
	{
		histories.clear();
	}

	private static void trim(Deque<ViewpointState> states)
	{
		while (states.size() > MAX_HISTORY_ENTRIES)
		{
			states.removeFirst();
		}
	}

	private static final class ViewpointHistory
	{
		private final Deque<ViewpointState> undo = new ArrayDeque<>();
		private final Deque<ViewpointState> redo = new ArrayDeque<>();
		private long lastCheckpointNanos;
	}

	private static final class ViewpointState
	{
		private final int yaw;
		private final int pitch;
		private final int zoom;

		private ViewpointState(int yaw, int pitch, int zoom)
		{
			this.yaw = yaw;
			this.pitch = pitch;
			this.zoom = zoom;
		}

		private static ViewpointState capture(CameraScenesViewpoint viewpoint)
		{
			return new ViewpointState(viewpoint.getYaw(), viewpoint.getPitch(), viewpoint.getZoom());
		}

		private void applyTo(CameraScenesViewpoint viewpoint)
		{
			viewpoint.setYaw(yaw);
			viewpoint.setPitch(pitch);
			viewpoint.setZoom(zoom);
		}
	}
}
