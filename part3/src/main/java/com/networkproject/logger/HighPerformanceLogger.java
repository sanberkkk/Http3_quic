package com.networkproject.logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Yüksek performanslı, thread-safe asenkron loglama sınıfı.
 *
 * Mimari:
 *   Üretici (Client) Thread ----> [ ArrayBlockingQueue ] ----> Tüketici (Worker) Thread ----> .txt dosyaları
 *
 *  - Üretici thread'ler {@link #log} çağrısında yalnızca bir LogRecord nesnesi
 *    oluşturup kuyruğa bırakır; disk I/O üretici thread'i bloklamaz.
 *  - Tek bir arka plan worker daemon thread kuyruğu drain ederek tampon
 *    (BufferedWriter) üzerinden dosyaya yazar. Toplu (batch) drain ile
 *    yüksek throughput sağlanır.
 *  - Aynı yazarlar {@link ConcurrentHashMap} içinde önbelleklenir, böylece
 *    hem ortak (shared) hem de thread-özel dosyalar desteklenir.
 *
 * Log formatı:
 *   [yyyy-MM-dd HH:mm:ss.SSS] [thread-name#id] [Event: START] - mesaj
 */
public final class HighPerformanceLogger implements AutoCloseable {

    /** Kuyruk doluyken üretici tarafında uygulanacak politika. */
    public enum OverflowPolicy {
        /** Kuyruk boşalana kadar üreticiyi blokla (en güvenli, geri basınç sağlar). */
        BLOCK,
        /** Kuyruk doluysa yeni log kaydını sessizce at (en hızlı, kayıp riski). */
        DROP_NEW,
        /** Kuyruktaki en eski kaydı atıp yeni kaydı yerleştir. */
        DROP_OLDEST
    }

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private static final int DEFAULT_QUEUE_CAPACITY = 10_000;
    private static final int BATCH_DRAIN_LIMIT = 256;
    private static final long IDLE_FLUSH_INTERVAL_MS = 200L;

    private final ArrayBlockingQueue<LogRecord> queue;
    private final Map<Path, BufferedWriter> writers = new ConcurrentHashMap<>();
    private final Path defaultLogPath;
    private final OverflowPolicy overflowPolicy;
    private final Thread workerThread;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong droppedCount = new AtomicLong();
    private final List<LoggerListener> listeners = new CopyOnWriteArrayList<>();

    private static volatile HighPerformanceLogger defaultInstance;

    /**
     * Varsayılan paylaşımlı dosya ve makul varsayılan kapasite ile bir
     * singleton örnek döndürür. Çağıran taraf bunu kapatmak zorunda değildir;
     * JVM kapanışında shutdown-hook ile temizlenir.
     */
    public static HighPerformanceLogger getDefault() {
        HighPerformanceLogger local = defaultInstance;
        if (local == null) {
            synchronized (HighPerformanceLogger.class) {
                local = defaultInstance;
                if (local == null) {
                    local = new HighPerformanceLogger(
                            Paths.get("logs", "application.log.txt"),
                            DEFAULT_QUEUE_CAPACITY,
                            OverflowPolicy.BLOCK);
                    final HighPerformanceLogger toClose = local;
                    Runtime.getRuntime().addShutdownHook(new Thread(toClose::close,
                            "HighPerformanceLogger-shutdown"));
                    defaultInstance = local;
                }
            }
        }
        return local;
    }

    /**
     * Özelleştirilmiş logger oluşturur.
     *
     * @param defaultLogPath  log() çağrılarında hedef dosya verilmezse kullanılacak yol
     * @param queueCapacity   kuyruğun maksimum eleman sayısı
     * @param overflowPolicy  kuyruk doluyken davranış
     */
    public HighPerformanceLogger(Path defaultLogPath,
                                 int queueCapacity,
                                 OverflowPolicy overflowPolicy) {
        if (defaultLogPath == null) {
            throw new IllegalArgumentException("defaultLogPath null olamaz");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity > 0 olmalı");
        }
        this.defaultLogPath = defaultLogPath.toAbsolutePath();
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.overflowPolicy = overflowPolicy == null ? OverflowPolicy.BLOCK : overflowPolicy;

        ensureParent(this.defaultLogPath);

        this.workerThread = new Thread(this::runWorkerLoop, "HPLogger-Worker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    // =====================================================================
    //  Public API
    // =====================================================================

    /** Varsayılan dosyaya log yazar. */
    public void log(LogEvent event, String message) {
        enqueue(event, message, defaultLogPath);
    }

    /**
     * Belirtilen .txt dosyasına log yazar. Aynı dosyaya birden fazla thread'den
     * eş zamanlı yazım güvenlidir (tüm yazım tek worker thread tarafından yapılır).
     *
     * @param targetFile null verilirse varsayılan dosya kullanılır
     */
    public void log(LogEvent event, String message, Path targetFile) {
        enqueue(event, message, targetFile == null ? defaultLogPath : targetFile.toAbsolutePath());
    }

    /**
     * Çağıran thread'e özel bir .txt dosyasına log yazar (örn. logs/thread-Worker-3.txt).
     */
    public void logToOwnFile(LogEvent event, String message, Path directory) {
        Path dir = (directory == null ? Paths.get("logs") : directory).toAbsolutePath();
        String safeName = sanitize(Thread.currentThread().getName());
        Path target = dir.resolve("thread-" + safeName + ".txt");
        enqueue(event, message, target);
    }

    /** Üretici tarafında düşürülen (drop edilen) kayıt sayısını döndürür. */
    public long getDroppedCount() {
        return droppedCount.get();
    }

    /**
     * Her log çağrısında gerçek-zamanlı tetiklenecek bir dinleyici ekler
     * (örneğin {@link StatisticsTracker}). Birden fazla dinleyici eklenebilir.
     * Dinleyici, üretici thread bağlamında ve kuyruğa konmadan ÖNCE çağrılır;
     * implementasyonu hızlı tutulmalıdır.
     */
    public void addListener(LoggerListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(LoggerListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /** Kuyruğun anlık doluluk oranı (0.0 - 1.0). İzleme/debug amaçlıdır. */
    public double getQueueUtilization() {
        int cap = queue.remainingCapacity() + queue.size();
        return cap == 0 ? 0.0 : (double) queue.size() / cap;
    }

    /**
     * Kuyruğu drain edip worker thread'i temiz şekilde durdurur ve tüm dosya
     * tutamaklarını flush + close eder. Birden fazla çağrı güvenlidir.
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            workerThread.join(5_000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        for (Map.Entry<Path, BufferedWriter> e : writers.entrySet()) {
            try {
                e.getValue().flush();
                e.getValue().close();
            } catch (IOException ignore) {
                // shutdown sırasında sessiz; bu noktada yapılabilecek bir şey yok
            }
        }
        writers.clear();
    }

    // =====================================================================
    //  Internal
    // =====================================================================

    private void enqueue(LogEvent event, String message, Path target) {
        if (!running.get()) {
            return;
        }
        Thread t = Thread.currentThread();
        Instant now = Instant.now();
        long tid = threadId(t);
        String msg = message == null ? "" : message;

        if (!listeners.isEmpty()) {
            for (LoggerListener l : listeners) {
                try {
                    l.onEvent(event, t.getName(), tid, now, msg);
                } catch (Throwable th) {
                    System.err.println("[HPLogger] Listener hata: " + th);
                }
            }
        }

        LogRecord rec = new LogRecord(
                now,
                t.getName(),
                tid,
                event,
                msg,
                target);

        switch (overflowPolicy) {
            case BLOCK:
                try {
                    queue.put(rec);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    droppedCount.incrementAndGet();
                }
                break;
            case DROP_NEW:
                if (!queue.offer(rec)) {
                    droppedCount.incrementAndGet();
                }
                break;
            case DROP_OLDEST:
                while (!queue.offer(rec)) {
                    if (queue.poll() != null) {
                        droppedCount.incrementAndGet();
                    }
                }
                break;
            default:
                queue.offer(rec);
        }
    }

    private void runWorkerLoop() {
        StringBuilder sb = new StringBuilder(128);
        while (running.get() || !queue.isEmpty()) {
            try {
                LogRecord first = queue.poll(IDLE_FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    flushAll();
                    continue;
                }
                writeRecord(first, sb);
                LogRecord next;
                int drained = 1;
                while (drained < BATCH_DRAIN_LIMIT && (next = queue.poll()) != null) {
                    writeRecord(next, sb);
                    drained++;
                }
                if (queue.isEmpty()) {
                    flushAll();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                // bir sonraki iterasyonda running kontrolü ile çıkışa yöneliriz
            } catch (Throwable t) {
                System.err.println("[HPLogger] Worker hata: " + t);
            }
        }
    }

    private void writeRecord(LogRecord r, StringBuilder sb) {
        sb.setLength(0);
        LocalDateTime ldt = LocalDateTime.ofInstant(r.timestamp, ZONE);
        sb.append('[').append(TS_FORMATTER.format(ldt)).append(']')
          .append(" [").append(r.threadName).append('#').append(r.threadId).append(']')
          .append(" [Event: ").append(r.event.name()).append(']')
          .append(" - ").append(r.message)
          .append(System.lineSeparator());

        BufferedWriter writer = writers.computeIfAbsent(r.targetPath, this::openWriter);
        if (writer == null) {
            return;
        }
        try {
            writer.write(sb.toString());
        } catch (IOException ioe) {
            System.err.println("[HPLogger] Yazma hatası (" + r.targetPath + "): " + ioe);
        }
    }

    private void flushAll() {
        for (BufferedWriter w : writers.values()) {
            try {
                w.flush();
            } catch (IOException ignore) {
                // ignore; sonraki yazımda tekrar denenir
            }
        }
    }

    private BufferedWriter openWriter(Path path) {
        try {
            ensureParent(path);
            return Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ioe) {
            System.err.println("[HPLogger] Dosya açılamadı: " + path + " -> " + ioe);
            return null;
        }
    }

    private static void ensureParent(Path path) {
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException ignore) {
                // open sırasında zaten tekrar denenecek / hata raporlanacak
            }
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * Java 19+ üzerinde Thread.getId() deprecated; refleksiyon ile yeni
     * Thread.threadId() çağrılır, eski JDK'larda getId() devreye girer.
     */
    @SuppressWarnings("deprecation")
    private static long threadId(Thread t) {
        try {
            return (long) Thread.class.getMethod("threadId").invoke(t);
        } catch (ReflectiveOperationException ex) {
            return t.getId();
        }
    }

    // =====================================================================
    //  Iç veri tipi
    // =====================================================================

    private static final class LogRecord {
        final Instant timestamp;
        final String threadName;
        final long threadId;
        final LogEvent event;
        final String message;
        final Path targetPath;

        LogRecord(Instant timestamp, String threadName, long threadId,
                  LogEvent event, String message, Path targetPath) {
            this.timestamp = timestamp;
            this.threadName = threadName;
            this.threadId = threadId;
            this.event = event;
            this.message = message;
            this.targetPath = targetPath;
        }
    }
}
