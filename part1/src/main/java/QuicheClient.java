import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import com.networkproject.logger.HighPerformanceLogger;
import com.networkproject.logger.LogEvent;
import com.networkproject.logger.StatisticsTracker;


/**
 * Kısım I: multithread Java istemci + loglama.
 * Her olay, Docker içindeki quiche {@code http3-client} ile tam QUIC/TLS/HTTP/3 isteği gönderir.
 */
public class QuicheClient {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final String DOCKER_IMAGE =
            env("QUICHE_DOCKER_IMAGE", "quiche-proje");
    private static final String TARGET_URL =
            env("QUICHE_TARGET_URL", "https://host.docker.internal:4433/");
    private static final String HTTP3_CLIENT_BIN =
            env("QUICHE_HTTP3_CLIENT", "/quiche/target/debug/examples/http3-client");

    private static final int THREAD_COUNT = intEnv("QUICHE_THREAD_COUNT", 5);
    private static final int NUM_EVENT = intEnv("QUICHE_NUM_EVENT", 10);

    private record Datas(double durationSeconds, double tps) {}

    private static final AtomicInteger startEventCount = new AtomicInteger(0);
    private static final AtomicInteger stopEventCount = new AtomicInteger(0);
    private static final AtomicInteger failEventCount = new AtomicInteger(0);

    private static HighPerformanceLogger logger;
    private static StatisticsTracker tracker;

    public static void main(String[] args) {
        if (!isDockerAvailable()) {
            System.err.println("Docker bulunamadı. Kurulu ve çalışır durumda olmalı.");
            System.exit(1);
        }

        logger = new HighPerformanceLogger(
                Paths.get("quic_project_logs.txt"),
                10_000,
                HighPerformanceLogger.OverflowPolicy.BLOCK);
        tracker = new StatisticsTracker();
        tracker.start();

        System.out.println("#".repeat(5) + " QuicheClient (Docker HTTP/3) "
                + LocalTime.now().format(TIME_FORMATTER) + " " + "#".repeat(5));
        System.out.println("Image: " + DOCKER_IMAGE);
        System.out.println("URL:   " + TARGET_URL);
        System.out.println("Threads: " + THREAD_COUNT + ", events: " + NUM_EVENT);
        System.out.println("Sunucu: docker run ... examples/server (UDP 4433)\n");

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_EVENT; i++) {
            final int id = i;
            executorService.submit(() -> startEvent(id));
        }

        executorService.shutdown();
        while (!executorService.isTerminated()) {
            // wait for pool
        }

        System.out.println("#".repeat(5) + " shutdown Thread Pool entirely - end of events "
                + "#".repeat(5));

        Datas datas = calculateDatas(startTime);

        System.out.println("(".repeat(5) + " RESULTS " + ")".repeat(5));
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER)
                + "] Total duration: " + String.format("%.3f", datas.durationSeconds()) + " s");
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER)
                + "] Start event count: " + startEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER)
                + "] Stop Event Count: " + stopEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER)
                + "] Fail Event Count: " + failEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER)
                + "] TPS: " + String.format("%.2f", datas.tps()));

        tracker.stop();
        tracker.printSummary(System.out);
        logger.close();
    }

    private static void startEvent(int id) {
        startEventCount.incrementAndGet();
        tracker.recordStart();
        logger.log(LogEvent.START,
                "Thread ID " + id + " event started at " + LocalTime.now().format(TIME_FORMATTER));
        System.out.println("!".repeat(5) + " Thread ID " + id + " START "
                + LocalTime.now().format(TIME_FORMATTER) + " " + "!".repeat(5));

        try {
            int exit = runDockerHttp3Client();
            if (exit == 0) {
                stopEventCount.incrementAndGet();
                tracker.recordStop();
                logger.log(LogEvent.STOP,
                        "Thread ID " + id + " QUIC/HTTP3 OK via Docker (exit 0)");
                System.out.println("*".repeat(5) + " Thread ID " + id
                        + " QUIC/HTTP3 OK (docker http3-client) " + "*".repeat(5));
            } else {
                recordFail(id, "docker exit code " + exit);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordFail(id, e.getMessage());
        }
    }

    private static int runDockerHttp3Client() throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add(DOCKER_IMAGE);
        cmd.add(HTTP3_CLIENT_BIN);
        cmd.add(TARGET_URL);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        }

        int exit = process.waitFor();
        if (exit != 0 && out.length() > 0) {
            String tail = out.length() > 400
                    ? out.substring(out.length() - 400)
                    : out.toString();
            System.err.println("[docker] " + tail.replace("\n", " "));
        }
        return exit;
    }

    private static void recordFail(int id, String message) {
        failEventCount.incrementAndGet();
        tracker.recordFail();
        logger.log(LogEvent.FAIL,
                "Thread ID " + id + " QUIC/HTTP3 failed: " + message);
        System.out.println("Error Thread " + id + ": " + message);
    }

    private static Datas calculateDatas(long startTime) {
        double durationSeconds = Math.max((System.currentTimeMillis() - startTime), 1) / 1000.0;
        int totalProcessed = stopEventCount.get() + failEventCount.get();
        double tps = totalProcessed / durationSeconds;
        return new Datas(durationSeconds, tps);
    }

    private static boolean isDockerAvailable() {
        try {
            Process p = new ProcessBuilder("docker", "version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return v != null && !v.isBlank() ? v.trim() : defaultValue;
    }

    private static int intEnv(String key, int defaultValue) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
