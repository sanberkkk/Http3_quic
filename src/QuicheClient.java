import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class QuicheClient {

    private static final String HOST = "host.docker.internal";
    private static final int PORT = 4433;
    private static final int THREAD_COUNT = 5;

    public static void main(String[] args) {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        System.out.println("#".repeat(5)+"Started " + THREAD_COUNT + " client threads."+"#".repeat(5));

        for (int i = 0 ; i< THREAD_COUNT ; i++) {
            final int clientID = i;
            executor.submit(()->startClient(clientID));
        }

        executor.shutdown();

    }
    private static void startClient(int clientID) {

        System.out.println("#".repeat(5)+ "Thread started for client ID: " + clientID +"#".repeat(5));

        try {

            DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.configureBlocking(false);
            System.out.println("#".repeat(5)+"Client ID " + clientID +": Connecting to server "+"#".repeat(5));
            datagramChannel.connect(new InetSocketAddress(HOST, PORT));
            System.out.println("#".repeat(5)+"Client ID " + clientID +": Connection to server successful "+"#".repeat(5));

            System.out.println("#".repeat(5)+" Thread triggered sooo... "+"#".repeat(5));
            System.out.println("#".repeat(5)+" Connected to server successfully "+"#".repeat(5));

            String message = "This is a test message from Client: "+ clientID +" to Server";
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            datagramChannel.write(buffer);

            System.out.println("#".repeat(5)+" Message sent: "+ message +"#".repeat(5));

            datagramChannel.close();
            System.out.println("#".repeat(5)+"Client ID " + clientID +": Disconnected from socket - closed "+"#".repeat(5));

        } catch(IOException e){
            System.out.println("Error occured from client " + clientID + ":" + e.getMessage());
        }
    }
}
