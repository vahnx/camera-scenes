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
	private static final int CAMERA_SETTLE_TOLERANCE = 2;
	private final Client client;
	private final ClientThread clientThread;
	private final CameraScenesConfig config;
	private CameraScenesViewpoint queuedViewpoint;
	@Inject private CameraScenesLoadTrace trace;
	private String tracePhase = "idle";
	private double traceProgress;

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
			state.setDiagnostics("Mode " + client.getCameraMode() + " | " + tracePhase
				+ " | " + Math.round(traceProgress * 100) + "%"
				+ "<br>Target Y/P: " + client.getCameraYawTarget() + " / " + client.getCameraPitchTarget()
				+ "<br>Camera XYZ: " + client.getCameraX() + " / " + client.getCameraY() + " / " + client.getCameraZ()
				+ "<br>Focus XYZ: " + client.getCameraFocalPointX() + " / " + client.getCameraFocalPointY() + " / " + client.getCameraFocalPointZ()
				+ "<br>" + trace.status());
			SwingUtilities.invokeLater(() -> stateConsumer.accept(state));
		});
	}

	void apply(CameraScenesViewpoint savedViewpoint)
	{
		if (savedViewpoint == null) return;
		long requestedAt = System.nanoTime();
		clientThread.invokeLater(() ->
		{
			trace.begin(requestedAt, savedViewpoint, config);
			tracePhase = "requested";
			traceProgress = 0;
			traceSample("load_request");
			if (client.getGameState() != GameState.LOGGED_IN) return;
			queuedViewpoint = savedViewpoint;
			if (isCameraReady() && !isCutsceneActive())
			{
				startQueuedTransition();
			}
		});
	}

	void onBeforeRender()
	{
		if (!config.showDebugTextInSidePanel()) trace.finish("debug disabled");
		traceSample("before_update");
		try { updateCamera(); }
		finally
		{
			traceSample("after_update");
			if (queuedViewpoint == null) trace.landed();
		}
	}

	private void traceSample(String event)
	{
		trace.sample(client, event, tracePhase, traceProgress, isCameraInputActive(), isCutsceneActive());
	}

	private void updateCamera()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
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

	}

	void cancelPendingLoad()
	{
		trace.finish("cancelled or shutdown");
		queuedViewpoint = null;
	}

	void returnToAttachedCamera()
	{
		clientThread.invokeLater(() -> {
			cancelPendingLoad();
			if (client.getGameState() == GameState.LOGGED_IN && client.getCameraMode() == FREE_CAMERA_MODE)
			{
				client.setCameraMode(0);
			}
		});
	}

	private void startQueuedTransition()
	{
		CameraScenesViewpoint viewpoint = queuedViewpoint;
		queuedViewpoint = null;
		if (viewpoint == null || !isCameraReady() || isCutsceneActive())
		{
			return;
		}
		applyImmediate(viewpoint);
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
		tracePhase = "immediate";
		if (!ensureFreeCameraMode())
		{
			return;
		}
		client.setCameraYawTarget(savedViewpoint.getYaw());
		client.setCameraPitchTarget(savedViewpoint.getPitch());
		client.runScript(ScriptID.CAMERA_DO_ZOOM, savedViewpoint.getZoom(), savedViewpoint.getZoom());
		returnToAttachedCameraNow();
	}

	private boolean ensureFreeCameraMode()
	{
		if (client.getCameraMode() != FREE_CAMERA_MODE)
		{
			client.setCameraMode(FREE_CAMERA_MODE);
			traceSample("entered_free_mode");
		}
		return client.getCameraMode() == FREE_CAMERA_MODE;
	}

	private void returnToAttachedCameraNow()
	{
		if (client.getGameState() == GameState.LOGGED_IN && client.getCameraMode() == FREE_CAMERA_MODE)
		{
			traceSample("before_reattach");
			client.setCameraMode(0);
			tracePhase = "post-load";
			traceSample("after_reattach");
			trace.landed();
		}
	}

	static boolean hasCameraSettled(int currentYaw, int currentPitch, int targetYaw, int targetPitch)
	{
		int yawDelta = Math.abs(Math.floorMod(currentYaw - targetYaw + CameraScenesViewpoint.YAW_UNITS / 2,
			CameraScenesViewpoint.YAW_UNITS) - CameraScenesViewpoint.YAW_UNITS / 2);
		return yawDelta <= CAMERA_SETTLE_TOLERANCE
			&& Math.abs(currentPitch - targetPitch) <= CAMERA_SETTLE_TOLERANCE;
	}

	private int currentZoom()
	{
		return client.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG);
	}

}
