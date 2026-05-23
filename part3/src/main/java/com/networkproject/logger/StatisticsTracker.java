package com.networkproject.logger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe istatistik toplayıcı.
 *
 *  - Her olay tipi için {@link LongAdder} kullanılır; yüksek eşzamanlılıkta
 *    AtomicLong'a göre çok daha az kontansiyon üretir (write tarafı şeritli,
 *    okuma sırasında {@code sum()} yapar).
 *  - Test penceresi {@link #start()} ile başlar, {@link #stop()} ile biter.
 *    Her ikisi de idempotent'tir (ilk çağrı kazanır).
 *  - {@link #record(LogEvent)} (veya kısa varyantları) çağıran thread'i
 *    bloklamadan sayaçları günceller.
 *  - {@link #getTps()} ve arkadaşları "transaction" kavramını şu şekilde
 *    tanımlar: bir transaction, ya STOP ya da FAIL ile sonuçlanan iştir.
 *      total      = STOP + FAIL
 *      successful = STOP
 *      TPS        = total / elapsedSeconds
 *      successTPS = STOP  / elapsedSeconds
 *      successRate= STOP  / total
 *
 * Sınıf {@link LoggerListener} arayüzünü uyguladığı için doğrudan bir
 * {@link HighPerformanceLogger}'a takılabilir; bu sayede her log çağrısı
 * istatistikleri otomatik günceller.
 */
public final class StatisticsTracker implements LoggerListener {

    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final EnumMap<LogEvent, LongAdder> counters;

    private final AtomicLong startNanos = new AtomicLong(0L);
    private final AtomicLong stopNanos  = new AtomicLong(0L);

    public StatisticsTracker() {
        this.counters = new EnumMap<>(LogEvent.class);
        for (LogEvent e : LogEvent.values()) {
            this.counters.put(e, new LongAdder());
        }
    }

    // =====================================================================
    //  Test penceresi
    // =====================================================================

    /** Test zaman penceresinin başlangıcını kaydeder. İlk çağrı kazanır. */
    public void start() {
        startNanos.compareAndSet(0L, System.nanoTime());
    }

    /** Test zaman penceresinin bitişini kaydeder. İlk çağrı kazanır. */
    public void stop() {
        stopNanos.compareAndSet(0L, System.nanoTime());
    }

    /** Test henüz çalışıyorsa şu ana kadar geçen süre; aksi halde toplam süre. */
    public long getElapsedNanos() {
        long s = startNanos.get();
        if (s == 0L) {
            return 0L;
        }
        long e = stopNanos.get();
        return (e == 0L ? System.nanoTime() : e) - s;
    }

    public double getElapsedSeconds() {
        return getElapsedNanos() / 1_000_000_000.0;
    }

    // =====================================================================
    //  Olay kaydı
    // =====================================================================

    /** Tek noktadan tüm olay tipleri için sayaç artırımı. */
    public void record(LogEvent event) {
        if (event == null) {
            return;
        }
        counters.get(event).increment();
    }

    public void recordStart() { counters.get(LogEvent.START).increment(); }
    public void recordStop()  { counters.get(LogEvent.STOP).increment();  }
    public void recordFail()  { counters.get(LogEvent.FAIL).increment();  }

    /** {@link LoggerListener} entegrasyonu. */
    @Override
    public void onEvent(LogEvent event, String threadName, long threadId,
                        Instant timestamp, String message) {
        record(event);
    }

    // =====================================================================
    //  Okuma / metrikler
    // =====================================================================

    public long getCount(LogEvent event) {
        LongAdder la = counters.get(event);
        return la == null ? 0L : la.sum();
    }

    public long getStartCount() { return getCount(LogEvent.START); }
    public long getStopCount()  { return getCount(LogEvent.STOP);  }
    public long getFailCount()  { return getCount(LogEvent.FAIL);  }

    /** Tamamlanan transaction sayısı = STOP + FAIL. */
    public long getTotalTransactions() {
        return getStopCount() + getFailCount();
    }

    /** TPS = (STOP + FAIL) / elapsedSeconds. */
    public double getTps() {
        double sec = getElapsedSeconds();
        return sec <= 0.0 ? 0.0 : getTotalTransactions() / sec;
    }

    /** Sadece başarılı tamamlananlar için TPS = STOP / elapsedSeconds. */
    public double getSuccessfulTps() {
        double sec = getElapsedSeconds();
        return sec <= 0.0 ? 0.0 : getStopCount() / sec;
    }

    /** Başarı oranı = STOP / (STOP + FAIL). */
    public double getSuccessRate() {
        long total = getTotalTransactions();
        return total == 0L ? 0.0 : (double) getStopCount() / total;
    }

    /** İzlenebilirlik için sayaçların anlık görüntüsü. */
    public Map<LogEvent, Long> snapshot() {
        EnumMap<LogEvent, Long> out = new EnumMap<>(LogEvent.class);
        for (Map.Entry<LogEvent, LongAdder> e : counters.entrySet()) {
            out.put(e.getKey(), e.getValue().sum());
        }
        return out;
    }

    /** Yeni bir test koşumu için sayaçları ve pencereyi sıfırlar. */
    public void reset() {
        for (LongAdder la : counters.values()) {
            la.reset();
        }
        startNanos.set(0L);
        stopNanos.set(0L);
    }

    // =====================================================================
    //  Raporlama
    // =====================================================================

    /** İnsan-okur formatında özet metin. */
    public String formatSummary() {
        long started   = getStartCount();
        long stopped   = getStopCount();
        long failed    = getFailCount();
        long total     = getTotalTransactions();
        double seconds = getElapsedSeconds();

        StringBuilder sb = new StringBuilder(512);
        sb.append("============= TEST SUMMARY =============").append(System.lineSeparator());
        sb.append(String.format("Test penceresi   : %.3f saniye%n", seconds));
        sb.append(String.format("START olayları   : %d%n", started));
        sb.append(String.format("STOP  olayları   : %d  (başarılı)%n", stopped));
        sb.append(String.format("FAIL  olayları   : %d  (başarısız)%n", failed));
        sb.append(String.format("Toplam transaction (STOP+FAIL): %d%n", total));
        sb.append(String.format("TPS (toplam)     : %.2f tx/sn%n", getTps()));
        sb.append(String.format("TPS (başarılı)   : %.2f tx/sn%n", getSuccessfulTps()));
        sb.append(String.format("Başarı oranı     : %.2f%%%n", getSuccessRate() * 100.0));
        // Diğer olay tipleri varsa onları da göster.
        for (Map.Entry<LogEvent, LongAdder> e : counters.entrySet()) {
            LogEvent ev = e.getKey();
            if (ev == LogEvent.START || ev == LogEvent.STOP || ev == LogEvent.FAIL) {
                continue;
            }
            long v = e.getValue().sum();
            if (v > 0L) {
                sb.append(String.format("%-5s olayları   : %d%n", ev.name(), v));
            }
        }
        sb.append("========================================");
        return sb.toString();
    }

    /** Konsola özeti basar. */
    public void printSummary(PrintStream out) {
        (out == null ? System.out : out).println(formatSummary());
    }

    /**
     * Özeti ayrı bir .txt raporu olarak diske yazar. Üst dizin yoksa oluşturulur.
     * Aynı dosyaya birden çok çağrı append eder (zaman damgalı başlıkla ayrılır).
     */
    public void writeReport(Path reportPath) throws IOException {
        if (reportPath == null) {
            throw new IllegalArgumentException("reportPath null olamaz");
        }
        Path parent = reportPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String header = "# Rapor " + TS_FORMATTER.format(LocalDateTime.now(ZONE))
                      + System.lineSeparator();
        String body   = formatSummary() + System.lineSeparator() + System.lineSeparator();
        Files.write(
                reportPath,
                (header + body).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
