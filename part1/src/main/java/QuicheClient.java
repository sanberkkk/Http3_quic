import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;


public class QuicheClient {

    static {
        try {
            System.loadLibrary("quiche");
        } catch (UnsatisfiedLinkError e) {
            System.out.println(e.getMessage());
        }
    }

    private static final int SERVER_PORT = 4433;
    private static final String SERVER_HOST = "127.0.0.1";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static int THREAD_COUNT = 5;
    private static int NUM_EVENT = 10;
    private static int BYTEBUFF_SIZE = 1350;

    private record Datas (double duration, double tps) {}

    private static final AtomicInteger startEventCount = new AtomicInteger(0);
    private static final AtomicInteger stopEventCount = new AtomicInteger(0);
    private static final AtomicInteger failEventCount = new AtomicInteger(0);

    public static void main (String[] args) {
        System.out.println("#".repeat(5) + "Starting Quiche Client at " + LocalTime.now().format(TIME_FORMATTER) + " #".repeat(5));

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        long startTime = System.currentTimeMillis();

        for (int i = 0 ; i < NUM_EVENT ; i++) {
            final int id = i;
            executorService.submit(() -> startEvent(id));
        }

        executorService.shutdown();
        while (!executorService.isTerminated()) {}

        System.out.println("#".repeat(5) + " shutdown Thread Pool entirely - end of events " + "#".repeat(5));

        Datas datas = calculateDatas(startTime);

        System.out.println("(".repeat(5) + " RESULTS " + ")".repeat(5));
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + "] Total event execution duration: " + datas.duration() + " ms");
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + "] Start event count: " + startEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + "] Stop Event Count: " + stopEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + "] Fail Event Count: " + failEventCount.get());
        System.out.println("[" + LocalTime.now().format(TIME_FORMATTER) + "] TPS: " + String.format("%.2f", datas.tps()));

    }

    private static  void startEvent (int id) {
        startEventCount.incrementAndGet();
        System.out.println("!".repeat(5) + " Triggered an event for Thread ID " + id +" at " + LocalTime.now().format(TIME_FORMATTER) + " !".repeat(5));

        try (DatagramChannel channel = DatagramChannel.open()) {

            channel.configureBlocking(false);

            InetSocketAddress serverAd = new InetSocketAddress(SERVER_HOST, SERVER_PORT);
            channel.connect(serverAd);

            ByteBuffer buffer = ByteBuffer.allocate(BYTEBUFF_SIZE);
            formBuffer(buffer);
            buffer.flip();

            channel.write(buffer);

            stopEventCount.incrementAndGet();
            System.out.println("*".repeat(5) + " Thread ID " + id + " successfully sent packet into channel " + "*".repeat(5));

        } catch (IOException e) {
            failEventCount.incrementAndGet();
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static Datas calculateDatas (long startTime) {

        double duration = Math.max((System.currentTimeMillis() - startTime), 1) / 1000.0;

        int totalProcessed = stopEventCount.get() + failEventCount.get();
        double tps = totalProcessed / duration;

        return new Datas (duration, tps);
    }

    private static void formBuffer (ByteBuffer buffer) {
        // Header + Initial
        buffer.put((byte) 0xC0);

        // QUIC version
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x00);
        buffer.put((byte) 0x01);

        // Destination Connection ID length and Id
        buffer.put((byte) 0x08);
        for (int j = 0; j < 8; j++) buffer.put((byte) 0xAA);

        // Source Connection ID length and ID
        buffer.put((byte) 0x08);
        for (int j = 0; j < 8; j++) buffer.put((byte) 0xBB);

        // Token length
        buffer.put((byte) 0x00);

        // Packet length
        buffer.put((byte) 0x44);
        buffer.put((byte) 0x00);
    }


}
