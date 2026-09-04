package com.camerascenes;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.KeyCode;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;

/** Bridges saved Camera Scenes viewpoints to RuneLite's client thread. */
@Singleton
final class CameraScenesCameraService
{
	private static final int FREE_CAMERA_MODE = 1;
	private final Client client;
	private final ClientThread clientThread;
	private final CameraScenesConfig config;
	private PendingCameraTransition pendingTransition;
	private CameraScenesViewpoint queuedViewpoint;

	@Inject
	CameraScenesCameraService(Client client, ClientThread clientThread, CameraScenesConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
	}

	void captureCurrent(Consumer<CameraScenesCameraSnapshot> snapshotConsumer)
	{
		readCurrent(state ->
		{
			if (state != null)
			{
				// Keyboard camera movement updates RuneLite's targets first; capture those
				// values so a viewpoint stores the requested angle instead of a transient
				// rendered value while the camera is still easing into position.
				snapshotConsumer.accept(new CameraScenesCameraSnapshot(
					state.getYawTarget(), state.getPitchTarget(), state.getZoom()));
			}
		});
	}

	void readCurrent(Consumer<CameraScenesCameraState> stateConsumer)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				SwingUtilities.invokeLater(() -> stateConsumer.accept(null));
				return;
			}
			CameraScenesCameraState state = new CameraScenesCameraState(
				client.getCameraYaw(),
				client.getCameraPitch(),
				client.getCameraYawTarget(),
				client.getCameraPitchTarget(),
				currentZoom());
			SwingUtilities.invokeLater(() -> stateConsumer.accept(state));
		});
	}

	void apply(CameraScenesViewpoint savedViewpoint)
	{
		if (savedViewpoint == null) return;
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN) return;
			pendingTransition = null;
			queuedViewpoint = savedViewpoint;
			if (isCameraReady() && !isCutsceneActive())
			{
				startQueuedTransition();
			}
		});
	}

	void onBeforeRender()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			pendingTransition = null;
			queuedViewpoint = null;
			return;
		}

		if (queuedViewpoint != null)
		{
			if (isCameraInputActive())
			{
				queuedViewpoint = null;
				return;
			}
			if (!isCameraReady() || isCutsceneActive())
			{
				return;
			}
			startQueuedTransition();
		}

		if (pendingTransition == null)
		{
			return;
		}

		PendingCameraTransition transition = pendingTransition;
		if (isCameraInputActive())
		{
			pendingTransition = null;
			return;
		}

		long nowNanos = System.nanoTime();
		if (!isCameraReady() || isCutsceneActive())
		{
			transition.hold(nowNanos);
			return;
		}
		transition.resume(nowNanos);
		if (!ensureFreeCameraMode())
		{
			pendingTransition = null;
			return;
		}

		double rawProgress = Math.min(1.0,
			transition.getElapsedNanos(nowNanos) / (transition.getDurationMilliseconds() * 1_000_000.0));
		double progress = CameraScenesCameraInterpolation.easeInOut(rawProgress);
		int yaw = CameraScenesCameraInterpolation.yaw(transition.getStartYaw(), transition.getTargetYaw(), progress);
		int pitch = CameraScenesCameraInterpolation.linear(transition.getStartPitch(), transition.getTargetPitch(), progress);
		client.setCameraYawTarget(yaw);
		client.setCameraPitchTarget(pitch);
		if (transition.isSmoothZoom())
		{
			int zoom = CameraScenesCameraInterpolation.linear(transition.getStartZoom(), transition.getTargetZoom(), progress);
			client.runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom);
		}

		if (rawProgress >= 1.0)
		{
			client.setCameraYawTarget(transition.getTargetYaw());
			client.setCameraPitchTarget(transition.getTargetPitch());
			if (transition.isSmoothZoom())
			{
				client.runScript(ScriptID.CAMERA_DO_ZOOM, transition.getTargetZoom(), transition.getTargetZoom());
			}
			pendingTransition = null;
		}
	}

	void cancelPendingLoad()
	{
		pendingTransition = null;
		queuedViewpoint = null;
	}

	private void startQueuedTransition()
	{
		CameraScenesViewpoint viewpoint = queuedViewpoint;
		queuedViewpoint = null;
		if (viewpoint == null || !isCameraReady() || isCutsceneActive() || !ensureFreeCameraMode())
		{
			return;
		}
		if (!config.smoothViewpointLoads())
		{
			applyImmediate(viewpoint);
			return;
		}

		boolean smoothZoom = config.smoothZoomLoads();
		if (!smoothZoom)
		{
			client.runScript(ScriptID.CAMERA_DO_ZOOM, viewpoint.getZoom(), viewpoint.getZoom());
		}
		pendingTransition = new PendingCameraTransition(
			client.getCameraYawTarget(),
			client.getCameraPitchTarget(),
			currentZoom(),
			viewpoint.getYaw(),
			viewpoint.getPitch(),
			viewpoint.getZoom(),
			System.nanoTime(),
			Math.max(100, Math.min(2000, config.viewpointLoadDuration())),
			smoothZoom);
	}

	private boolean isCameraReady()
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& hasReadyCameraState(currentZoom(), client.getCameraPitchTarget());
	}

	static boolean hasReadyCameraState(int zoom, int pitchTarget)
	{
		// Camera Scenes supports the game's negative zoom values. During early
		// initialization both values are zero, so retain a lightweight readiness guard.
		return zoom != 0 || pitchTarget > 0;
	}

	private boolean isCutsceneActive()
	{
		return client.getVarbitValue(VarbitID.CUTSCENE_STATUS) != 0;
	}

	private boolean isCameraInputActive()
	{
		return client.isKeyPressed(KeyCode.KC_UP)
			|| client.isKeyPressed(KeyCode.KC_DOWN)
			|| client.isKeyPressed(KeyCode.KC_LEFT)
			|| client.isKeyPressed(KeyCode.KC_RIGHT)
			|| client.getMouseCurrentButton() == 2
			|| client.getMouseCurrentButton() == 4;
	}

	private void applyImmediate(CameraScenesViewpoint savedViewpoint)
	{
		if (!ensureFreeCameraMode())
		{
			return;
		}
		client.setCameraYawTarget(savedViewpoint.getYaw());
		client.setCameraPitchTarget(savedViewpoint.getPitch());
		client.runScript(ScriptID.CAMERA_DO_ZOOM, savedViewpoint.getZoom(), savedViewpoint.getZoom());
	}

	private boolean ensureFreeCameraMode()
	{
		if (client.getCameraMode() != FREE_CAMERA_MODE)
		{
			client.setCameraMode(FREE_CAMERA_MODE);
		}
		return client.getCameraMode() == FREE_CAMERA_MODE;
	}

	private int currentZoom()
	{
		return client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
	}

	private static final class PendingCameraTransition
	{
		private final int startYaw;
		private final int startPitch;
		private final int startZoom;
		private final int targetYaw;
		private final int targetPitch;
		private final int targetZoom;
		private final long createdAtNanos;
		private final int durationMilliseconds;
		private final boolean smoothZoom;
		private long heldAtNanos = -1L;
		private long heldDurationNanos;

		private PendingCameraTransition(int startYaw, int startPitch, int startZoom, int targetYaw, int targetPitch,
			int targetZoom, long createdAtNanos, int durationMilliseconds, boolean smoothZoom)
		{
			this.startYaw = startYaw;
			this.startPitch = startPitch;
			this.startZoom = startZoom;
			this.targetYaw = targetYaw;
			this.targetPitch = targetPitch;
			this.targetZoom = targetZoom;
			this.createdAtNanos = createdAtNanos;
			this.durationMilliseconds = durationMilliseconds;
			this.smoothZoom = smoothZoom;
		}

		private int getStartYaw() { return startYaw; }
		private int getStartPitch() { return startPitch; }
		private int getStartZoom() { return startZoom; }
		private int getTargetYaw() { return targetYaw; }
		private int getTargetPitch() { return targetPitch; }
		private int getTargetZoom() { return targetZoom; }
		private int getDurationMilliseconds() { return durationMilliseconds; }
		private boolean isSmoothZoom() { return smoothZoom; }

		private void hold(long nowNanos)
		{
			if (heldAtNanos < 0)
			{
				heldAtNanos = nowNanos;
			}
		}

		private void resume(long nowNanos)
		{
			if (heldAtNanos >= 0)
			{
				heldDurationNanos += nowNanos - heldAtNanos;
				heldAtNanos = -1L;
			}
		}

		private long getElapsedNanos(long nowNanos)
		{
			long currentHoldNanos = heldAtNanos < 0 ? 0 : nowNanos - heldAtNanos;
			return Math.max(0, nowNanos - createdAtNanos - heldDurationNanos - currentHoldNanos);
		}
	}
}
