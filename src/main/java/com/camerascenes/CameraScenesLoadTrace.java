package com.camerascenes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.RuneLite;

/** Client-thread samples buffered in memory; completed traces are written off-thread. */
@Singleton
@Slf4j
final class CameraScenesLoadTrace
{
	private static final int MAX_SAMPLES = 30000;
	private static final long MAX_NANOS = 120_000_000_000L;
	private final AtomicInteger pendingWrites = new AtomicInteger();
	@Inject private ScheduledExecutorService executor;
	private StringBuilder rows;
	private long started;
	private long ended = -1;
	private int samples;
	private volatile String status = "Trace idle";

	synchronized String status()
	{
		return rows == null ? status : (ended < 0 ? "Recording load" : "Recording after landing (5 s)");
	}

	synchronized void begin(long requested, CameraScenesViewpoint view, CameraScenesConfig config)
	{
		finish("superseded");
		if (!config.showDebugTextInSidePanel()) return;
		started = requested;
		ended = -1;
		samples = 0;
		rows = new StringBuilder("# requested_at=" + java.time.Instant.now()
			+ ",saved_yaw=" + view.getYaw() + ",saved_pitch=" + view.getPitch()
			+ ",saved_zoom=" + view.getZoom() + ",load_mode=immediate"
			+ "\nms,event,phase,progress,state,mode,yaw,pitch,target_yaw,target_pitch,zoom_small,zoom_big,camera_x,camera_y,camera_z,focal_x,focal_y,focal_z,input,cutscene\n");
		status = "Recording load";
	}

	synchronized void sample(Client c, String event, String phase, double progress, boolean input, boolean cutscene)
	{
		if (rows == null) return;
		long now = System.nanoTime();
		rows.append((now - started) / 1_000_000.0).append(',').append(event).append(',')
			.append(phase).append(',').append(progress).append(',').append(c.getGameState()).append(',')
			.append(c.getCameraMode()).append(',').append(c.getCameraYaw()).append(',').append(c.getCameraPitch()).append(',')
			.append(c.getCameraYawTarget()).append(',').append(c.getCameraPitchTarget()).append(',')
			.append(c.getVarcIntValue(VarClientID.CAMERA_ZOOM_SMALL)).append(',')
			.append(c.getVarcIntValue(VarClientID.CAMERA_ZOOM_BIG)).append(',')
			.append(c.getCameraX()).append(',').append(c.getCameraY()).append(',').append(c.getCameraZ()).append(',')
			.append(c.getCameraFocalPointX()).append(',').append(c.getCameraFocalPointY()).append(',')
			.append(c.getCameraFocalPointZ()).append(',').append(input).append(',').append(cutscene).append('\n');
		samples++;
		if (ended >= 0 && now - ended >= 5_000_000_000L) finish("post-load window complete");
		else if (samples >= MAX_SAMPLES || now - started >= MAX_NANOS) finish("trace safety limit");
	}

	synchronized void landed()
	{
		if (rows != null && ended < 0) { ended = System.nanoTime(); status = "Recording after landing (5 s)"; }
	}

	synchronized void finish(String reason)
	{
		if (rows == null) return;
		String data = rows.append("# end=").append(reason).append('\n').toString();
		rows = null;
		if (pendingWrites.incrementAndGet() > 4)
		{
			pendingWrites.decrementAndGet();
			status = "Trace dropped: disk writer busy";
			return;
		}
		status = "Saving trace";
		try
		{
			executor.execute(() -> {
				try
				{
					Path dir = RuneLite.RUNELITE_DIR.toPath().resolve("camera-scenes/temp");
					Files.createDirectories(dir);
					Path file = Files.createTempFile(dir, "camera-load-", ".csv");
					Files.write(file, data.getBytes(StandardCharsets.UTF_8));
					status = "Saved " + file.getFileName();
				}
				catch (Exception e) { status = "Trace save failed: " + e.getClass().getSimpleName(); log.debug("Cannot write camera trace", e); }
				finally { pendingWrites.decrementAndGet(); }
			});
		}
		catch (java.util.concurrent.RejectedExecutionException e)
		{
			pendingWrites.decrementAndGet(); status = "Trace save failed: executor stopped";
			log.debug("Cannot schedule camera trace write", e);
		}
	}
}
