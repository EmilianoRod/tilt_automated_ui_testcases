package Utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VideoRecorder {

    private static boolean ffmpegAvailable = true;

    private Process process;
    private final Path videoPath;
    private final String testName;
    private Path ffmpegLogPath;

    public VideoRecorder(String testName) {
        this.testName = testName;

        // Always ensure target/videos exists, even if recording is disabled or fails
        Path parent = Paths.get("target", "videos");
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            System.err.println("[VideoRecorder] Failed to create videos directory: " + e.getMessage());
        }

        this.videoPath = parent.resolve(
                sanitize(testName) + "_" + UUID.randomUUID() + ".mp4"
        );
    }

    /** Global enable/disable flag (no OS special case here). */
    public static boolean isEnabled() {
        return Boolean.parseBoolean(System.getProperty("video.enabled", "true"));
    }

    public Path getVideoPath() {
        return videoPath;
    }

    // =====================================================================
    // START RECORDING
    // =====================================================================
    public void start() {
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));

        // Optional: allow disabling in headless if you want
        if (headless && Boolean.parseBoolean(System.getProperty("video.skipInHeadless", "false"))) {
            System.out.println("[VideoRecorder] headless=true & video.skipInHeadless=true → skipping recording for " + testName);
            return;
        }

        if (!checkFfmpeg()) {
            System.out.println("[VideoRecorder] ffmpeg not found → video disabled for " + testName);
            return;
        }

        try {
            String[] cmd = buildFfmpegCommand(videoPath.toAbsolutePath().toString());
            if (cmd == null) {
                System.err.println("[VideoRecorder] Unsupported OS for screen capture.");
                return;
            }

            System.out.println("[VideoRecorder] Starting recording for " + testName);
            System.out.println("[VideoRecorder] ffmpeg cmd: " + String.join(" ", cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            // Log file for this recording
            ffmpegLogPath = videoPath.resolveSibling(
                    videoPath.getFileName().toString().replace(".mp4", ".log")
            );
            pb.redirectOutput(ffmpegLogPath.toFile());
            System.out.println("[VideoRecorder] ffmpeg log → " + ffmpegLogPath.toAbsolutePath());

            process = pb.start();

            System.out.println("[VideoRecorder] Recording → " + videoPath.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("[VideoRecorder] Failed to start recording: " + e.getMessage());
            process = null;
        }
    }

    // =====================================================================
    // STOP RECORDING
    // =====================================================================
    public Path stop() {
        if (process == null) {
            if (Files.exists(videoPath)) {
                System.out.println("[VideoRecorder] stop() called but process==null; file exists: " + videoPath);
                return videoPath;
            }
            System.out.println("[VideoRecorder] stop() called but process==null and no file exists for " + testName);
            return null;
        }

        try {
            System.out.println("[VideoRecorder] Stopping recording for " + testName + "…");

            // Send 'q' to ffmpeg for graceful shutdown
            try (OutputStream os = process.getOutputStream()) {
                os.write('q');
                os.flush();
            } catch (IOException e) {
                System.out.println("[VideoRecorder] Failed to send 'q' to ffmpeg: " + e.getMessage());
            }

            // Give ffmpeg enough time to finalize MP4 (a bit more than before)
            boolean exited = process.waitFor(40, TimeUnit.SECONDS);

            if (!exited) {
                System.out.println("[VideoRecorder] ffmpeg still running after 40s, calling destroy() (no force kill)...");
                process.destroy();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    System.out.println("[VideoRecorder] ffmpeg still alive after destroy(); leaving OS to reap it.");
                }
            }

            try {
                int code = process.exitValue();
                System.out.println("[VideoRecorder] ffmpeg exit code: " + code);
            } catch (IllegalThreadStateException e) {
                System.out.println("[VideoRecorder] ffmpeg still alive; exit code unavailable.");
            }

        } catch (InterruptedException e) {
            System.out.println("[VideoRecorder] Interrupted while waiting for ffmpeg: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.out.println("[VideoRecorder] Error stopping ffmpeg: " + e.getMessage());
        } finally {
            process = null;
        }

        // Final check: does the file exist?
        if (!Files.exists(videoPath)) {
            System.out.println("[VideoRecorder] File not found after stop: " + videoPath);
            if (ffmpegLogPath != null && Files.exists(ffmpegLogPath)) {
                System.out.println("[VideoRecorder] ffmpeg log content (tail):");
                try {
                    long linesCount = Files.lines(ffmpegLogPath).count();
                    Files.lines(ffmpegLogPath)
                            .skip(Math.max(0, linesCount - 40))
                            .forEach(System.out::println);
                } catch (IOException ignored) { }
            }
            return null;
        }

        System.out.println("[VideoRecorder] Final video file exists: " + videoPath.toAbsolutePath());
        return videoPath;
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Build ffmpeg command line.
     *
     * macOS:
     *   - Uses avfoundation
     *   - Video device "1" (Capture screen 0) by default
     *   - Explicit pixel format to avoid the warning you saw
     *
     * Linux:
     *   - x11grab
     *
     * Windows:
     *   - gdigrab
     */
    private static String[] buildFfmpegCommand(String outputFile) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (os.contains("mac")) {
            // From your -list_devices:
            // [0] FaceTime HD Camera (Built-in)
            // [1] Capture screen 0
            // [2] Capture screen 1
            // We use "1" by default, but let you override via sysprop if needed.
            String device = System.getProperty("ffmpeg.avfoundation.input", "1");
            String fps    = System.getProperty("ffmpeg.avfoundation.fps", "30");

            return new String[]{
                    "ffmpeg", "-y",
                    "-f", "avfoundation",
                    "-pix_fmt", "uyvy422",
                    "-framerate", "30",
                    "-i", device + ":none",  // <-- IMPORTANT FIX
                    "-vcodec", "libx264",
                    "-preset", "ultrafast",
                    "-pix_fmt", "yuv420p",
                    outputFile
            };

        }

        if (os.contains("linux")) {
            return new String[]{
                    "ffmpeg", "-y",
                    "-loglevel", "error",
                    "-f", "x11grab",
                    "-framerate", "15",
                    "-i", ":0.0",
                    outputFile
            };
        }

        if (os.contains("win")) {
            return new String[]{
                    "ffmpeg", "-y",
                    "-loglevel", "error",
                    "-f", "gdigrab",
                    "-framerate", "15",
                    "-i", "desktop",
                    outputFile
            };
        }

        return null;
    }

    private static boolean checkFfmpeg() {
        if (!ffmpegAvailable) return false;

        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor(5, TimeUnit.SECONDS);
            return true;

        } catch (IOException | InterruptedException e) {
            System.err.println("[VideoRecorder] ffmpeg NOT found in PATH.");
            ffmpegAvailable = false;
            return false;
        }
    }
}
