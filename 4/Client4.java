import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class Client4 {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;
    private static final int BUFFER_SIZE = 1024;
    private static final int TIMEOUT = 5000;

    private final DatagramSocket socket;
    private final InetAddress serverAddress;
    private int sequenceNumber;
    private final NetworkSimulator networkSimulator;
    private final List<Long> rttMeasurements;
    private final CommunicationLogger logger;
    private final String sessionId;
    private final long startTime;
    private int packetsSent = 0;
    private int packetsReceived = 0;

    public Client4() throws SocketException, UnknownHostException {
        socket = new DatagramSocket();
        socket.setSoTimeout(TIMEOUT);
        serverAddress = InetAddress.getByName(SERVER_ADDRESS);
        sequenceNumber = 0;

        networkSimulator = new NetworkSimulator(0.1, 0.1, 50, 200);
        this.rttMeasurements = new ArrayList<>();
        this.logger = new CommunicationLogger();
        this.startTime = System.currentTimeMillis();
        this.sessionId = logger.startTransaction(socket.getLocalAddress().getHostAddress(), 
                                               socket.getLocalPort());
    }

    public boolean sendMessage(String message) {
        try {
            Packet packet = new Packet(
                Packet.PacketType.DATA,
                sequenceNumber,
                message.getBytes()
            );

            byte[] sendData = networkSimulator.maybeCorruptPacket(packet.toBytes());
            
            DatagramPacket sendPacket = new DatagramPacket(
                sendData,
                sendData.length,
                serverAddress,
                SERVER_PORT
            );

            int attempts = 0;
            int maxAttempts = 3;
            
            while (attempts < maxAttempts) {
                long sendTime = System.currentTimeMillis();
                
                if (networkSimulator.shouldDropPacket()) {
                    logger.logPacketDropped(sessionId, packet);
                    attempts++;
                    continue;
                }

                networkSimulator.simulateNetworkDelay();
                socket.send(sendPacket);
                packetsSent++;
                logger.logPacketSent(sessionId, packet);

                try {
                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                    socket.receive(receivePacket);

                    networkSimulator.simulateNetworkDelay();

                    Packet response = Packet.fromBytes(
                        Arrays.copyOf(receivePacket.getData(), receivePacket.getLength())
                    );

                    if (response.getType() == Packet.PacketType.ACK &&
                        response.getSequenceNumber() == sequenceNumber) {
                        long rtt = System.currentTimeMillis() - sendTime;
                        rttMeasurements.add(rtt);
                        packetsReceived++;
                        sequenceNumber = (sequenceNumber + 1) % 2;
                        System.out.println(String.format("Packet RTT: %dms", rtt));
                        return true;
                    } else if (response.getType() == Packet.PacketType.NACK) {
                        System.out.println("Client4 received NACK, retrying...");
                        attempts++;
                        continue;
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Client4 timeout, retrying... (Attempt " + (attempts + 1) + " of " + maxAttempts + ")");
                    attempts++;
                }
            }
            return false;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Client4 error: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        double averageRTT = rttMeasurements.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
                
        logger.endTransaction(sessionId, socket.getLocalAddress().getHostAddress(), 
                            socket.getLocalPort(), startTime, packetsSent, 
                            packetsReceived, averageRTT);
                            
        System.out.println(String.format("Client closing - Average RTT: %.2fms", averageRTT));
                            
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public static void main(String[] args) {
        try {
            Client4 client = new Client4();

            String[] testMessages = {
                "Test1: Basic Message",
                "Test2: This message might be corrupted",
                "Test3: This message might be lost",
                "Test4: This message might be delayed",
                "Test5: Final test message"
            };

            for (String message : testMessages) {
                System.out.println("\nClient4 attempting to send: " + message);
                boolean success = client.sendMessage(message);
                System.out.println("Send " + (success ? "successful" : "failed"));
                Thread.sleep(1000);
            }

            client.close();
            
        } catch (SocketException | UnknownHostException e) {
            System.err.println("Error creating Client4: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Client4 sleep interrupted: " + e.getMessage());
        }
    }
} 